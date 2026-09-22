package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OmniChatApplication
import com.example.data.model.*
import com.example.network.NetworkResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as OmniChatApplication).repository

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _activeConversation = MutableStateFlow<ConversationEntity?>(null)
    val activeConversation: StateFlow<ConversationEntity?> = _activeConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _activeProvider = MutableStateFlow<ProviderEntity?>(null)
    val activeProvider: StateFlow<ProviderEntity?> = _activeProvider.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelEntity?>(null)
    val activeModel: StateFlow<ModelEntity?> = _activeModel.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()

    private var activeJob: Job? = null
    private var messageCollectionJob: Job? = null

    init {
        // Observe enabled providers to pick a sensible default
        viewModelScope.launch {
            repository.enabledProviders.collect { providers ->
                if (_activeProvider.value == null && providers.isNotEmpty()) {
                    val defaultProv = providers.first()
                    _activeProvider.value = defaultProv
                    loadDefaultModelForProvider(defaultProv.id)
                }
            }
        }
    }

    private fun loadDefaultModelForProvider(providerId: String) {
        viewModelScope.launch {
            repository.getModelsForProvider(providerId).firstOrNull()?.let { models ->
                if (models.isNotEmpty()) {
                    _activeModel.value = models.firstOrNull { it.isFavorite } ?: models.first()
                }
            }
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val provId = _activeProvider.value?.id ?: ""
            val modId = _activeModel.value?.modelId ?: ""
            val newConv = repository.createConversation(
                title = "New Chat",
                providerId = provId,
                modelId = modId
            )
            loadConversation(newConv.id)
        }
    }

    fun loadConversation(conversationId: String?) {
        if (conversationId == null) {
            _activeConversationId.value = null
            _activeConversation.value = null
            _messages.value = emptyList()
            return
        }

        _activeConversationId.value = conversationId

        viewModelScope.launch {
            val conv = repository.getConversation(conversationId)
            _activeConversation.value = conv

            // Sync provider and model if specified in conversation
            if (conv != null && conv.providerId.isNotBlank()) {
                repository.getProviderById(conv.providerId)?.let { p ->
                    _activeProvider.value = p
                    if (conv.modelId.isNotBlank()) {
                        repository.getModelById("${p.id}::${conv.modelId}")?.let { m ->
                            _activeModel.value = m
                        }
                    }
                }
            }

            // Listen to messages flow
            messageCollectionJob?.cancel()
            messageCollectionJob = viewModelScope.launch {
                repository.getMessages(conversationId).collect { list ->
                    _messages.value = list.map { ChatMessage.fromEntity(it) }
                }
            }
        }
    }

    fun setActiveProvider(provider: ProviderEntity) {
        _activeProvider.value = provider
        loadDefaultModelForProvider(provider.id)
        _activeConversation.value?.let { conv ->
            viewModelScope.launch {
                repository.updateConversationModel(conv.id, provider.id, _activeModel.value?.modelId ?: "")
            }
        }
    }

    fun setActiveModel(model: ModelEntity) {
        _activeModel.value = model
        _activeConversation.value?.let { conv ->
            viewModelScope.launch {
                repository.updateConversationModel(conv.id, model.providerId, model.modelId)
            }
        }
    }

    fun addAttachment(attachment: Attachment) {
        _attachments.value = _attachments.value + attachment
    }

    fun removeAttachment(id: String) {
        _attachments.value = _attachments.value.filter { it.id != id }
    }

    fun clearAttachments() {
        _attachments.value = emptyList()
    }

    fun toggleThinkingExpanded(messageId: String, currentExpanded: Boolean) {
        viewModelScope.launch {
            repository.setThinkingExpanded(messageId, !currentExpanded)
        }
    }

    fun stopGeneration() {
        activeJob?.cancel()
        _isGenerating.value = false
    }

    fun sendMessage(promptText: String) {
        val trimmed = promptText.trim()
        val currentAttach = _attachments.value
        if (trimmed.isEmpty() && currentAttach.isEmpty()) return

        val provider = _activeProvider.value ?: return
        val model = _activeModel.value ?: ModelEntity(
            id = "${provider.id}::default",
            modelId = "default",
            providerId = provider.id,
            displayName = "Default Model"
        )

        _isGenerating.value = true
        clearAttachments()

        activeJob = viewModelScope.launch {
            // Ensure conversation exists
            var convId = _activeConversationId.value
            if (convId == null) {
                val newConv = repository.createConversation(
                    title = "New Chat",
                    providerId = provider.id,
                    modelId = model.modelId
                )
                convId = newConv.id
                _activeConversationId.value = convId
                _activeConversation.value = newConv

                // Start observing
                messageCollectionJob?.cancel()
                messageCollectionJob = viewModelScope.launch {
                    repository.getMessages(convId).collect { list ->
                        _messages.value = list.map { ChatMessage.fromEntity(it) }
                    }
                }
            }

            val systemPrompt = _activeConversation.value?.systemPrompt ?: ""

            repository.sendChatMessage(
                conversationId = convId,
                provider = provider,
                model = model,
                userPrompt = trimmed,
                systemPrompt = systemPrompt,
                attachments = currentAttach
            ) { _, isFinished ->
                if (isFinished) {
                    _isGenerating.value = false
                }
            }

            _isGenerating.value = false
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }
}
