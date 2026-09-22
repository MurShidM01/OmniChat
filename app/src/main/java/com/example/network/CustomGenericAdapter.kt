package com.example.network

import com.example.data.model.ApiCompatibilityType
import com.example.data.model.ChatMessage
import com.example.data.model.ModelEntity
import com.example.data.model.ProviderEntity

class CustomGenericAdapter : ProviderAdapter {

    private val openAIAdapter = OpenAIChatCompletionsAdapter()

    override suspend fun testConnection(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): ConnectionTestResult {
        return openAIAdapter.testConnection(provider, decryptedApiKey)
    }

    override suspend fun fetchModels(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): NetworkResult<List<ModelEntity>> {
        return openAIAdapter.fetchModels(provider, decryptedApiKey)
    }

    override suspend fun sendMessage(
        provider: ProviderEntity,
        model: ModelEntity,
        history: List<ChatMessage>,
        systemPrompt: String,
        decryptedApiKey: String,
        stream: Boolean,
        onChunk: (StreamChunk) -> Unit
    ): NetworkResult<String> {
        return openAIAdapter.sendMessage(
            provider,
            model,
            history,
            systemPrompt,
            decryptedApiKey,
            stream,
            onChunk
        )
    }
}

object AdapterFactory {
    private val openAIAdapter by lazy { OpenAIChatCompletionsAdapter() }
    private val anthropicAdapter by lazy { AnthropicMessagesAdapter() }
    private val openAIResponsesAdapter by lazy { OpenAIResponsesAdapter() }
    private val customGenericAdapter by lazy { CustomGenericAdapter() }

    fun getAdapter(compatibilityType: ApiCompatibilityType): ProviderAdapter {
        return when (compatibilityType) {
            ApiCompatibilityType.OPENAI_CHAT_COMPLETIONS -> openAIAdapter
            ApiCompatibilityType.ANTHROPIC_MESSAGES -> anthropicAdapter
            ApiCompatibilityType.OPENAI_RESPONSES -> openAIResponsesAdapter
            ApiCompatibilityType.CUSTOM_GENERIC -> customGenericAdapter
        }
    }
}
