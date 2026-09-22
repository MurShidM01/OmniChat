package com.example.network

import com.example.data.model.ChatMessage
import com.example.data.model.ModelEntity
import com.example.data.model.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class OpenAIResponsesAdapter : ProviderAdapter {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): ConnectionTestResult = OpenAIChatCompletionsAdapter().testConnection(provider, decryptedApiKey)

    override suspend fun fetchModels(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): NetworkResult<List<ModelEntity>> = OpenAIChatCompletionsAdapter().fetchModels(provider, decryptedApiKey)

    override suspend fun sendMessage(
        provider: ProviderEntity,
        model: ModelEntity,
        history: List<ChatMessage>,
        systemPrompt: String,
        decryptedApiKey: String,
        stream: Boolean,
        onChunk: (StreamChunk) -> Unit
    ): NetworkResult<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val client = NetworkUtils.createClient(provider.timeoutSeconds)
        val url = NetworkUtils.buildFullUrl(provider.baseUrl, provider.chatEndpoint)

        try {
            val payload = JSONObject().apply {
                put("model", model.modelId)
                put("stream", stream)

                if (systemPrompt.isNotBlank()) {
                    put("instructions", systemPrompt)
                }

                val inputArray = JSONArray()
                for (msg in history) {
                    val item = JSONObject().apply {
                        put("role", msg.role.name.lowercase())
                        put("content", msg.content)
                    }
                    inputArray.put(item)
                }
                put("input", inputArray)

                if (provider.customBodyJson.isNotBlank()) {
                    try {
                        val custom = JSONObject(provider.customBodyJson)
                        val keys = custom.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            put(k, custom.get(k))
                        }
                    } catch (e: Exception) {}
                }
            }

            val body = payload.toString().toRequestBody(jsonMediaType)
            val builder = Request.Builder()
            val finalUrl = NetworkUtils.applyAuthAndHeaders(builder, provider, decryptedApiKey, url)
            val request = builder.url(finalUrl).post(body).build()

            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime

            NetworkDiagnostics.record(
                providerName = provider.name,
                adapterType = "OpenAI Responses",
                requestMethod = "POST",
                endpointUrl = finalUrl,
                headers = NetworkUtils.extractHeadersMap(request.headers),
                statusCode = response.code,
                latencyMs = latencyMs,
                isStreaming = stream,
                errorDetails = if (!response.isSuccessful) "HTTP ${response.code}: ${response.message}" else null
            )

            if (!response.isSuccessful) {
                return@withContext NetworkResult.Error("HTTP ${response.code}: ${response.message}", response.code)
            }

            val responseBody = response.body
                ?: return@withContext NetworkResult.Error("Empty response body")

            if (stream) {
                val fullContent = StringBuilder()
                val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
                reader.useLines { lines ->
                    for (rawLine in lines) {
                        val line = rawLine.trim()
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") {
                                onChunk(StreamChunk(isFinished = true, finishReason = "stop"))
                                break
                            }
                            try {
                                val chunk = JSONObject(data)
                                val delta = chunk.optString("delta", "")
                                if (delta.isNotEmpty()) {
                                    fullContent.append(delta)
                                    onChunk(StreamChunk(deltaContent = delta))
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
                NetworkResult.Success(fullContent.toString(), System.currentTimeMillis() - startTime)
            } else {
                val resStr = responseBody.string()
                val root = JSONObject(resStr)
                val output = root.optString("output", "")
                onChunk(StreamChunk(deltaContent = output, isFinished = true, finishReason = "stop"))
                NetworkResult.Success(output, System.currentTimeMillis() - startTime)
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Responses request failed", cause = e)
        }
    }
}
