package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OmniChatApplication
import com.example.data.model.ConversationEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as OmniChatApplication).repository

    val rawConversations: StateFlow<List<ConversationEntity>> = repository.activeConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")

    val filteredConversations: StateFlow<List<ConversationEntity>> = combine(
        rawConversations,
        searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createNewConversation(onCreated: (ConversationEntity) -> Unit) {
        viewModelScope.launch {
            val conv = repository.createConversation()
            onCreated(conv)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(id, newTitle)
        }
    }

    fun togglePinned(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.setConversationPinned(id, isPinned)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }
}
