package com.example.network

import com.example.data.model.ChatMessage
import com.example.data.model.ModelEntity
import com.example.data.model.ProviderEntity

interface ProviderAdapter {
    suspend fun testConnection(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): ConnectionTestResult

    suspend fun fetchModels(
        provider: ProviderEntity,
        decryptedApiKey: String
    ): NetworkResult<List<ModelEntity>>

    suspend fun sendMessage(
        provider: ProviderEntity,
        model: ModelEntity,
        history: List<ChatMessage>,
        systemPrompt: String,
        decryptedApiKey: String,
        stream: Boolean,
        onChunk: (StreamChunk) -> Unit
    ): NetworkResult<String>
}
