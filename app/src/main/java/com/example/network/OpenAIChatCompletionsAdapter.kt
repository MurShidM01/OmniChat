package com.example.network

import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
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

class OpenAIChatCompletionsAdapter : ProviderAdapter {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val client = NetworkUtils.createClient(provider.timeoutSeconds)
        val url = NetworkUtils.buildFullUrl(provider.baseUrl, provider.modelsEndpoint)

        try {
            val builder = Request.Builder()
            val finalUrl = NetworkUtils.applyAuthAndHeaders(builder, provider, decryptedApiKey, url)
            val request = builder.url(finalUrl).get().build()

            val call = client.newCall(request)
            val response = call.execute()
            val latencyMs = System.currentTimeMillis() - startTime

            NetworkDiagnostics.record(
                providerName = provider.name,
                adapterType = "OpenAI Chat Completions",
                requestMethod = "GET",
                endpointUrl = finalUrl,
                headers = NetworkUtils.extractHeadersMap(request.headers),
                statusCode = response.code,
                latencyMs = latencyMs,
                isStreaming = false,
                errorDetails = if (!response.isSuccessful) "HTTP ${response.code}: ${response.message}" else null
            )

            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                var modelCount = 0
                try {
                    val root = JSONObject(bodyStr)
                    if (root.has("data")) {
                        modelCount = root.getJSONArray("data").length()
                    } else if (root.has("models")) {
                        modelCount = root.getJSONArray("models").length()
                    }
                } catch (e: Exception) {
                    // response is valid but body format varies
                }
                ConnectionTestResult(
                    isSuccess = true,
                    message = "Connected successfully (${response.code})",
                    latencyMs = latencyMs,
                    discoveredModelsCount = modelCount
                )
            } else {
                ConnectionTestResult(
                    isSuccess = false,
                    message = "Server returned HTTP ${response.code}: ${response.message}",
                    latencyMs = latencyMs
                )
            }
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startTime
            NetworkDiagnostics.record(
                providerName = provider.name,
                adapterType = "OpenAI Chat Completions",
                requestMethod = "GET",
                endpointUrl = url,
                headers = emptyMap(),
                statusCode = null,
                latencyMs = latencyMs,
                isStreaming = false,
                errorDetails = e.localizedMessage
            )
            ConnectionTestResult(
                isSuccess = false,
                message = e.localizedMessage ?: "Connection failed",
                latencyMs = latencyMs
            )
        }
    }

    override suspend fun fetchModels(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): NetworkResult<List<ModelEntity>> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val client = NetworkUtils.createClient(provider.timeoutSeconds)
        val url = NetworkUtils.buildFullUrl(provider.baseUrl, provider.modelsEndpoint)

        try {
            val builder = Request.Builder()
            val finalUrl = NetworkUtils.applyAuthAndHeaders(builder, provider, decryptedApiKey, url)
            val request = builder.url(finalUrl).get().build()

            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                return@withContext NetworkResult.Error(
                    message = "Failed to fetch models: HTTP ${response.code}",
                    statusCode = response.code
                )
            }

            val body = response.body?.string() ?: ""
            val models = parseModelsList(body, provider.id)
            NetworkResult.Success(models, latencyMs)
        } catch (e: Exception) {
            NetworkResult.Error(
                message = e.localizedMessage ?: "Network error fetching models",
                cause = e
            )
        }
    }

    private fun parseModelsList(jsonString: String, providerId: String): List<ModelEntity> {
        val result = mutableListOf<ModelEntity>()
        try {
            val root = JSONObject(jsonString)
            val array: JSONArray? = when {
                root.has("data") -> root.optJSONArray("data")
                root.has("models") -> root.optJSONArray("models")
                else -> null
            }

            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").ifEmpty { item.optString("name") }
                    if (id.isBlank()) continue

                    val lower = id.lowercase()
                    val isVision = lower.contains("vision") || lower.contains("4o") || lower.contains("vl") || lower.contains("claude") || lower.contains("gemini")
                    val isReasoning = lower.contains("o1") || lower.contains("o3") || lower.contains("r1") || lower.contains("reason") || lower.contains("think")
                    val isTools = !lower.contains("instruct") || lower.contains("tool") || lower.contains("gpt")

                    val contextWindow = when {
                        lower.contains("1m") -> 1000000
                        lower.contains("200k") -> 200000
                        lower.contains("128k") || lower.contains("4o") -> 128000
                        lower.contains("32k") -> 32768
                        else -> 128000
                    }

                    result.add(
                        ModelEntity(
                            id = "$providerId::$id",
                            modelId = id,
                            providerId = providerId,
                            displayName = id,
                            description = "Discovered from provider",
                            contextWindow = contextWindow,
                            supportsVision = isVision,
                            supportsReasoning = isReasoning,
                            supportsToolCalling = isTools,
                            supportsStreaming = true,
                            isFavorite = false,
                            isCustom = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return result
    }

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

                val messagesArray = JSONArray()

                // System message if provided
                if (systemPrompt.isNotBlank()) {
                    messagesArray.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }

                // Conversation history
                for (msg in history) {
                    val roleStr = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    }

                    val msgObj = JSONObject()
                    msgObj.put("role", roleStr)

                    if (msg.attachments.isNotEmpty()) {
                        val contentArray = JSONArray()
                        if (msg.content.isNotBlank()) {
                            contentArray.put(JSONObject().apply {
                                put("type", "text")
                                put("text", msg.content)
                            })
                        }
                        for (att in msg.attachments) {
                            if (!att.base64Data.isNullOrBlank()) {
                                val mime = if (att.mimeType.isNotBlank()) att.mimeType else "image/jpeg"
                                contentArray.put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:$mime;base64,${att.base64Data}")
                                    })
                                })
                            }
                        }
                        msgObj.put("content", contentArray)
                    } else {
                        msgObj.put("content", msg.content)
                    }
                    messagesArray.put(msgObj)
                }

                put("messages", messagesArray)

                // Merge custom body params if specified
                if (provider.customBodyJson.isNotBlank()) {
                    try {
                        val customJson = JSONObject(provider.customBodyJson)
                        val keys = customJson.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            put(k, customJson.get(k))
                        }
                    } catch (e: Exception) {
                        // ignore malformed custom body
                    }
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
                adapterType = "OpenAI Chat Completions",
                requestMethod = "POST",
                endpointUrl = finalUrl,
                headers = NetworkUtils.extractHeadersMap(request.headers),
                statusCode = response.code,
                latencyMs = latencyMs,
                isStreaming = stream,
                errorDetails = if (!response.isSuccessful) "HTTP ${response.code}: ${response.message}" else null
            )

            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                var parsedError = "HTTP ${response.code}: ${response.message}"
                try {
                    val errJson = JSONObject(errBody)
                    if (errJson.has("error")) {
                        val inner = errJson.optJSONObject("error")
                        if (inner != null) {
                            parsedError = inner.optString("message", parsedError)
                        } else {
                            parsedError = errJson.optString("error", parsedError)
                        }
                    }
                } catch (e: Exception) {
                    if (errBody.isNotBlank()) parsedError = "$parsedError\n$errBody"
                }
                return@withContext NetworkResult.Error(parsedError, response.code)
            }

            val responseBody = response.body
                ?: return@withContext NetworkResult.Error("Empty response body")

            if (stream) {
                val fullContent = StringBuilder()
                val fullReasoning = StringBuilder()
                val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))

                reader.useLines { lines ->
                    for (rawLine in lines) {
                        val line = rawLine.trim()
                        if (line.isEmpty() || line.startsWith(":")) continue // SSE ping/comment

                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") {
                                onChunk(StreamChunk(isFinished = true, finishReason = "stop"))
                                break
                            }

                            try {
                                val chunkJson = JSONObject(data)
                                val choices = chunkJson.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")
                                    val finishReason = choice.optString("finish_reason").takeIf { it.isNotEmpty() && it != "null" }

                                    var textDelta = ""
                                    var reasoningDelta: String? = null

                                    if (delta != null) {
                                        if (delta.has("content")) {
                                            textDelta = delta.optString("content")
                                        }
                                        if (delta.has("reasoning_content")) {
                                            reasoningDelta = delta.optString("reasoning_content")
                                        } else if (delta.has("reasoning")) {
                                            reasoningDelta = delta.optString("reasoning")
                                        }
                                    }

                                    if (textDelta.isNotEmpty()) fullContent.append(textDelta)
                                    if (!reasoningDelta.isNullOrEmpty()) fullReasoning.append(reasoningDelta)

                                    if (textDelta.isNotEmpty() || reasoningDelta != null || finishReason != null) {
                                        onChunk(
                                            StreamChunk(
                                                deltaContent = textDelta,
                                                deltaReasoning = reasoningDelta,
                                                isFinished = finishReason != null,
                                                finishReason = finishReason
                                            )
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore non-json or partial json chunks
                            }
                        }
                    }
                }

                NetworkResult.Success(fullContent.toString(), System.currentTimeMillis() - startTime)
            } else {
                val responseString = responseBody.string()
                val root = JSONObject(responseString)
                val choices = root.getJSONArray("choices")
                val choice = choices.getJSONObject(0)
                val messageObj = choice.getJSONObject("message")
                val content = messageObj.optString("content", "")
                val reasoning = messageObj.optString("reasoning_content", "").takeIf { it.isNotEmpty() }

                onChunk(
                    StreamChunk(
                        deltaContent = content,
                        deltaReasoning = reasoning,
                        isFinished = true,
                        finishReason = "stop"
                    )
                )

                NetworkResult.Success(content, System.currentTimeMillis() - startTime)
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Request failed", cause = e)
        }
    }
}
