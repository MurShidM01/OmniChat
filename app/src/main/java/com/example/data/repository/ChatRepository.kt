package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.presets.ProviderPreset
import com.example.network.*
import com.example.security.KeystoreSecretManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val providerDao = db.providerDao()
    private val modelDao = db.modelDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    // Providers
    val allProviders: Flow<List<ProviderEntity>> = providerDao.getAllProviders()
    val enabledProviders: Flow<List<ProviderEntity>> = providerDao.getEnabledProviders()

    suspend fun syncOfficialProvidersAndCleanPlaceholders() {
        val existingProviders = providerDao.getAllProvidersList()
        val officialPresets = ProviderPreset.getPresets()
        val officialIds = ProviderPreset.OFFICIAL_IDS

        // 1. Delete all old placeholder providers (Ollama, LM Studio, Groq, OpenRouter, Custom Provider placeholder)
        for (prov in existingProviders) {
            val isOfficial = officialIds.contains(prov.id)
            val isOldPlaceholder = prov.name.contains("Ollama", ignoreCase = true) ||
                    prov.name.contains("LM Studio", ignoreCase = true) ||
                    prov.name.contains("OpenRouter", ignoreCase = true) ||
                    prov.name.contains("Groq", ignoreCase = true) ||
                    (prov.name.contains("Custom Provider", ignoreCase = true) && prov.encryptedApiKey.isBlank())
            if (!isOfficial && isOldPlaceholder) {
                deleteProvider(prov.id)
            }
        }

        // 2. Insert or update the 4 official providers, preserving existing encryptedApiKey if user configured it
        for (i in officialPresets.indices) {
            val preset = officialPresets[i]
            val existing = providerDao.getProviderById(preset.id)
            if (existing == null) {
                providerDao.insertOrUpdate(preset.template.copy(sortOrder = i))
            } else {
                val updated = preset.template.copy(
                    encryptedApiKey = existing.encryptedApiKey,
                    isEnabled = existing.isEnabled,
                    sortOrder = i
                )
                providerDao.insertOrUpdate(updated)
            }
            // Seed the official models for this provider
            modelDao.insertAll(preset.defaultModels)
        }
    }

    suspend fun getProviderById(id: String): ProviderEntity? = providerDao.getProviderById(id)

    suspend fun saveProvider(provider: ProviderEntity, plainApiKey: String? = null) {
        val finalEncryptedKey = if (plainApiKey != null) {
            KeystoreSecretManager.encrypt(plainApiKey)
        } else {
            provider.encryptedApiKey
        }
        providerDao.insertOrUpdate(provider.copy(encryptedApiKey = finalEncryptedKey))
    }

    suspend fun deleteProvider(id: String) {
        providerDao.deleteById(id)
        modelDao.deleteDiscoveredByProvider(id)
    }

    suspend fun setProviderEnabled(id: String, enabled: Boolean) {
        providerDao.setEnabled(id, enabled)
    }

    suspend fun updateProviderSortOrder(id: String, order: Int) {
        providerDao.updateSortOrder(id, order)
    }

    suspend fun testProvider(provider: ProviderEntity, plainKey: String?): ConnectionTestResult {
        val apiKey = plainKey ?: KeystoreSecretManager.decrypt(provider.encryptedApiKey)
        val adapter = AdapterFactory.getAdapter(provider.compatibilityType)
        return adapter.testConnection(provider, apiKey)
    }

    suspend fun fetchAndStoreModels(provider: ProviderEntity): NetworkResult<List<ModelEntity>> {
        val apiKey = KeystoreSecretManager.decrypt(provider.encryptedApiKey)
        val adapter = AdapterFactory.getAdapter(provider.compatibilityType)
        val result = adapter.fetchModels(provider, apiKey)
        if (result is NetworkResult.Success) {
            modelDao.deleteDiscoveredByProvider(provider.id)
            modelDao.insertAll(result.data)
        }
        return result
    }

    // Models
    val allModels: Flow<List<ModelEntity>> = modelDao.getAllModels()

    fun getModelsForProvider(providerId: String): Flow<List<ModelEntity>> =
        modelDao.getModelsByProvider(providerId)

    suspend fun getModelById(id: String): ModelEntity? = modelDao.getModelById(id)

    suspend fun saveModel(model: ModelEntity) = modelDao.insertOrUpdate(model)

    suspend fun toggleFavorite(modelId: String, isFavorite: Boolean) =
        modelDao.setFavorite(modelId, isFavorite)

    suspend fun deleteModel(id: String) = modelDao.deleteById(id)

    // Conversations
    val activeConversations: Flow<List<ConversationEntity>> = conversationDao.getActiveConversations()
    val archivedConversations: Flow<List<ConversationEntity>> = conversationDao.getArchivedConversations()

    suspend fun getConversation(id: String): ConversationEntity? = conversationDao.getConversationById(id)

    suspend fun createConversation(
        title: String = "New Chat",
        providerId: String = "",
        modelId: String = "",
        systemPrompt: String = ""
    ): ConversationEntity {
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            providerId = providerId,
            modelId = modelId,
            systemPrompt = systemPrompt
        )
        conversationDao.insertOrUpdate(conv)
        return conv
    }

    suspend fun updateConversationTitle(id: String, title: String) =
        conversationDao.updateTitle(id, title)

    suspend fun setConversationPinned(id: String, isPinned: Boolean) =
        conversationDao.setPinned(id, isPinned)

    suspend fun setConversationArchived(id: String, isArchived: Boolean) =
        conversationDao.setArchived(id, isArchived)

    suspend fun updateConversationModel(id: String, providerId: String, modelId: String) =
        conversationDao.updateModel(id, providerId, modelId)

    suspend fun deleteConversation(id: String) = conversationDao.deleteById(id)

    // Messages
    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun saveMessage(message: MessageEntity) {
        messageDao.insertOrUpdate(message)
        conversationDao.touchConversation(message.conversationId)
    }

    suspend fun deleteMessage(id: String) = messageDao.deleteById(id)

    suspend fun clearMessages(conversationId: String) = messageDao.clearConversation(conversationId)

    suspend fun setThinkingExpanded(messageId: String, expanded: Boolean) =
        messageDao.setThinkingExpanded(messageId, expanded)

    /**
     * Executes sending a message via the active provider's adapter,
     * maintaining streaming in Room.
     */
    suspend fun sendChatMessage(
        conversationId: String,
        provider: ProviderEntity,
        model: ModelEntity,
        userPrompt: String,
        systemPrompt: String,
        attachments: List<Attachment>,
        onUpdate: (streamingContent: String, isFinished: Boolean) -> Unit
    ): NetworkResult<String> {
        val userMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userPrompt,
            attachmentsJson = Attachment.serializeList(attachments),
            timestamp = System.currentTimeMillis()
        )
        saveMessage(userMsg)

        // If conversation title is default, update it with user's first prompt snippet
        val conv = conversationDao.getConversationById(conversationId)
        if (conv != null && (conv.title == "New Chat" || conv.title.isBlank())) {
            val snippet = userPrompt.trim().take(36).let { if (userPrompt.length > 36) "$it..." else it }
            conversationDao.updateTitle(conversationId, snippet)
        }

        // Create Assistant placeholder
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = MessageEntity(
            id = assistantMsgId,
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            timestamp = System.currentTimeMillis(),
            modelUsed = model.displayName
        )
        saveMessage(assistantMsg)

        val fullHistory = messageDao.getMessagesList(conversationId).map { ChatMessage.fromEntity(it) }
        val apiKey = KeystoreSecretManager.decrypt(provider.encryptedApiKey)
        val adapter = AdapterFactory.getAdapter(provider.compatibilityType)

        val contentAccumulator = StringBuilder()
        val reasoningAccumulator = StringBuilder()
        var tokenEstimate = 0
        val startTime = System.currentTimeMillis()

        return try {
            val result = adapter.sendMessage(
                provider = provider,
                model = model,
                history = fullHistory,
                systemPrompt = systemPrompt,
                decryptedApiKey = apiKey,
                stream = provider.supportsStreaming
            ) { chunk ->
                if (chunk.deltaContent.isNotEmpty()) {
                    contentAccumulator.append(chunk.deltaContent)
                    tokenEstimate += (chunk.deltaContent.length / 4).coerceAtLeast(1)
                }
                if (!chunk.deltaReasoning.isNullOrEmpty()) {
                    reasoningAccumulator.append(chunk.deltaReasoning)
                }

                val latency = System.currentTimeMillis() - startTime
                // Update stream state in Room
                val currentReasoning = if (reasoningAccumulator.isNotEmpty()) reasoningAccumulator.toString() else null
                kotlinx.coroutines.runBlocking {
                    messageDao.updateStreamChunk(
                        id = assistantMsgId,
                        content = contentAccumulator.toString(),
                        reasoningContent = currentReasoning,
                        isStreaming = !chunk.isFinished,
                        latencyMs = latency,
                        tokenCount = tokenEstimate
                    )
                }
                onUpdate(contentAccumulator.toString(), chunk.isFinished)
            }

            val finalLatency = System.currentTimeMillis() - startTime
            val finalReasoning = if (reasoningAccumulator.isNotEmpty()) reasoningAccumulator.toString() else null

            when (result) {
                is NetworkResult.Success -> {
                    messageDao.updateStreamChunk(
                        id = assistantMsgId,
                        content = contentAccumulator.toString().ifEmpty { result.data },
                        reasoningContent = finalReasoning,
                        isStreaming = false,
                        latencyMs = finalLatency,
                        tokenCount = tokenEstimate
                    )
                    modelDao.updateLastUsed(model.id)
                }
                is NetworkResult.Error -> {
                    messageDao.markError(assistantMsgId, result.message)
                }
            }
            result
        } catch (e: Exception) {
            messageDao.markError(assistantMsgId, e.localizedMessage ?: "Unknown error")
            NetworkResult.Error(e.localizedMessage ?: "Send error", cause = e)
        }
    }
}
