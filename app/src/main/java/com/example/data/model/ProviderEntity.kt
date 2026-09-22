package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val encryptedApiKey: String = "",
    val compatibilityType: ApiCompatibilityType = ApiCompatibilityType.OPENAI_CHAT_COMPLETIONS,
    val chatEndpoint: String = "/chat/completions",
    val modelsEndpoint: String = "/models",
    val authType: AuthType = AuthType.BEARER_TOKEN,
    val customAuthHeader: String = "Authorization",
    val customHeadersJson: String = "{}",
    val customBodyJson: String = "{}",
    val supportsStreaming: Boolean = true,
    val timeoutSeconds: Int = 60,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
