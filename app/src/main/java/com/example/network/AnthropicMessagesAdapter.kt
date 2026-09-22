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

class AnthropicMessagesAdapter : ProviderAdapter {

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
            builder.header("anthropic-version", "2023-06-01")
            builder.header("x-api-key", decryptedApiKey)
            val finalUrl = NetworkUtils.applyAuthAndHeaders(builder, provider, decryptedApiKey, url)
            val request = builder.url(finalUrl).get().build()

            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime

            NetworkDiagnostics.record(
                providerName = provider.name,
                adapterType = "Anthropic Messages",
                requestMethod = "GET",
                endpointUrl = finalUrl,
                headers = NetworkUtils.extractHeadersMap(request.headers),
                statusCode = response.code,
                latencyMs = latencyMs,
                isStreaming = false,
                errorDetails = if (!response.isSuccessful) "HTTP ${response.code}: ${response.message}" else null
            )

            if (response.isSuccessful) {
                var count = 0
                val bodyStr = response.body?.string() ?: ""
                try {
                    val root = JSONObject(bodyStr)
                    if (root.has("data")) count = root.getJSONArray("data").length()
                } catch (e: Exception) {}

                ConnectionTestResult(
                    isSuccess = true,
                    message = "Connected to Anthropic successfully (${response.code})",
                    latencyMs = latencyMs,
                    discoveredModelsCount = count
                )
            } else {
                ConnectionTestResult(
                    isSuccess = false,
                    message = "Anthropic returned HTTP ${response.code}: ${response.message}",
                    latencyMs = latencyMs
                )
            }
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startTime
            ConnectionTestResult(
                isSuccess = false,
                message = e.localizedMessage ?: "Anthropic connection failed",
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
            builder.header("anthropic-version", "2023-06-01")
            builder.header("x-api-key", decryptedApiKey)
            val finalUrl = NetworkUtils.applyAuthAndHeaders(builder, provider, decryptedApiKey, url)
            val request = builder.url(finalUrl).get().build()

            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                // If /models endpoint is not available on this Anthropic proxy, return standard Claude models
                return@withContext NetworkResult.Success(getStandardAnthropicModels(provider.id), latencyMs)
            }

            val body = response.body?.string() ?: ""
            val parsed = parseAnthropicModels(body, provider.id)
            if (parsed.isNotEmpty()) {
                NetworkResult.Success(parsed, latencyMs)
            } else {
                NetworkResult.Success(getStandardAnthropicModels(provider.id), latencyMs)
            }
        } catch (e: Exception) {
            // Graceful fallback to known models
            NetworkResult.Success(getStandardAnthropicModels(provider.id), 0L)
        }
    }

    private fun parseAnthropicModels(jsonString: String, providerId: String): List<ModelEntity> {
        val list = mutableListOf<ModelEntity>()
        try {
            val root = JSONObject(jsonString)
            val data = root.optJSONArray("data")
            if (data != null) {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optString("id")
                    val name = item.optString("display_name", id)
                    if (id.isNotBlank()) {
                        list.add(
                            ModelEntity(
                                id = "$providerId::$id",
                                modelId = id,
                                providerId = providerId,
                                displayName = name,
                                description = "Anthropic Claude Model",
                                contextWindow = 200000,
                                supportsVision = true,
                                supportsReasoning = id.contains("3-7") || id.contains("thinking"),
                                supportsToolCalling = true,
                                supportsStreaming = true,
                                isFavorite = false,
                                isCustom = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {}
        return list
    }

    private fun getStandardAnthropicModels(providerId: String): List<ModelEntity> {
        return listOf(
            ModelEntity(
                id = "$providerId::claude-3-7-sonnet-20250219",
                modelId = "claude-3-7-sonnet-20250219",
                providerId = providerId,
                displayName = "Claude 3.7 Sonnet (Hybrid Thinking)",
                description = "Anthropic's latest hybrid reasoning & intelligence model",
                contextWindow = 200000,
                supportsVision = true,
                supportsReasoning = true,
                supportsToolCalling = true,
                supportsStreaming = true
            ),
            ModelEntity(
                id = "$providerId::claude-3-5-sonnet-20241022",
                modelId = "claude-3-5-sonnet-20241022",
                providerId = providerId,
                displayName = "Claude 3.5 Sonnet",
                description = "High intelligence, fast and versatile",
                contextWindow = 200000,
                supportsVision = true,
                supportsReasoning = false,
                supportsToolCalling = true,
                supportsStreaming = true
            ),
            ModelEntity(
                id = "$providerId::claude-3-5-haiku-20241022",
                modelId = "claude-3-5-haiku-20241022",
                providerId = providerId,
                displayName = "Claude 3.5 Haiku",
                description = "Fast, lightweight model with high coding capability",
                contextWindow = 200000,
                supportsVision = false,
                supportsReasoning = false,
                supportsToolCalling = true,
                supportsStreaming = true
            )
        )
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
                put("max_tokens", 4096)
                put("stream", stream)

                if (systemPrompt.isNotBlank()) {
                    put("system", systemPrompt)
                }

                val messagesArray = JSONArray()
                for (msg in history) {
                    if (msg.role == MessageRole.SYSTEM) continue // Anthropic requires system prompt at top-level

                    val roleStr = if (msg.role == MessageRole.USER) "user" else "assistant"
                    val msgObj = JSONObject()
                    msgObj.put("role", roleStr)

                    if (msg.attachments.isNotEmpty()) {
                        val contentArray = JSONArray()
                        for (att in msg.attachments) {
                            if (!att.base64Data.isNullOrBlank()) {
                                val mime = if (att.mimeType.isNotBlank()) att.mimeType else "image/jpeg"
                                contentArray.put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", mime)
                                        put("data", att.base64Data)
                                    })
                                })
                            }
                        }
                        if (msg.content.isNotBlank()) {
                            contentArray.put(JSONObject().apply {
                                put("type", "text")
                                put("text", msg.content)
                            })
                        }
                        msgObj.put("content", contentArray)
                    } else {
                        msgObj.put("content", msg.content)
                    }
                    messagesArray.put(msgObj)
                }
                put("messages", messagesArray)

                // Custom body overrides
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
            builder.header("anthropic-version", "2023-06-01")
            val finalUrl = NetworkUtils.applyAuthAndHeaders(builder, provider, decryptedApiKey, url)
            val request = builder.url(finalUrl).post(body).build()

            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startTime

            NetworkDiagnostics.record(
                providerName = provider.name,
                adapterType = "Anthropic Messages",
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
                var parsed = "HTTP ${response.code}: ${response.message}"
                try {
                    val root = JSONObject(errBody)
                    val err = root.optJSONObject("error")
                    if (err != null) parsed = err.optString("message", parsed)
                } catch (e: Exception) {}
                return@withContext NetworkResult.Error(parsed, response.code)
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
                        if (line.isEmpty() || line.startsWith(":")) continue

                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") {
                                onChunk(StreamChunk(isFinished = true, finishReason = "stop"))
                                break
                            }

                            try {
                                val eventObj = JSONObject(data)
                                val type = eventObj.optString("type")

                                when (type) {
                                    "content_block_delta" -> {
                                        val delta = eventObj.optJSONObject("delta")
                                        if (delta != null) {
                                            val deltaType = delta.optString("type")
                                            if (deltaType == "text_delta") {
                                                val text = delta.optString("text")
                                                fullContent.append(text)
                                                onChunk(StreamChunk(deltaContent = text))
                                            } else if (deltaType == "thinking_delta") {
                                                val thinking = delta.optString("thinking")
                                                fullReasoning.append(thinking)
                                                onChunk(StreamChunk(deltaReasoning = thinking))
                                            }
                                        }
                                    }
                                    "message_delta" -> {
                                        val delta = eventObj.optJSONObject("delta")
                                        val stopReason = delta?.optString("stop_reason")
                                        if (!stopReason.isNullOrEmpty() && stopReason != "null") {
                                            onChunk(StreamChunk(isFinished = true, finishReason = stopReason))
                                        }
                                    }
                                    "message_stop" -> {
                                        onChunk(StreamChunk(isFinished = true, finishReason = "stop"))
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }

                NetworkResult.Success(fullContent.toString(), System.currentTimeMillis() - startTime)
            } else {
                val resString = responseBody.string()
                val root = JSONObject(resString)
                val contentArray = root.optJSONArray("content")
                val textBuilder = StringBuilder()
                var reasoning: String? = null

                if (contentArray != null) {
                    for (i in 0 until contentArray.length()) {
                        val block = contentArray.getJSONObject(i)
                        val bType = block.optString("type")
                        if (bType == "text") {
                            textBuilder.append(block.optString("text"))
                        } else if (bType == "thinking") {
                            reasoning = block.optString("thinking")
                        }
                    }
                }

                val finalContent = textBuilder.toString()
                onChunk(
                    StreamChunk(
                        deltaContent = finalContent,
                        deltaReasoning = reasoning,
                        isFinished = true,
                        finishReason = "stop"
                    )
                )

                NetworkResult.Success(finalContent, System.currentTimeMillis() - startTime)
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Anthropic request failed", cause = e)
        }
    }
}
