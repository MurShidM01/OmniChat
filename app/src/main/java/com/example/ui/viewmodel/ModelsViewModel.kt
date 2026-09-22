package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OmniChatApplication
import com.example.data.model.ModelEntity
import com.example.data.model.ProviderEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ModelFilter {
    ALL,
    FAVORITES,
    CUSTOM,
    VISION,
    REASONING,
    TOOLS
}

class ModelsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as OmniChatApplication).repository

    val rawModels: StateFlow<List<ModelEntity>> = repository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProviders: StateFlow<List<ProviderEntity>> = repository.allProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow(ModelFilter.ALL)
    val selectedProviderId = MutableStateFlow<String?>(null)

    val filteredModels: StateFlow<List<ModelEntity>> = combine(
        rawModels,
        searchQuery,
        selectedFilter,
        selectedProviderId
    ) { models, query, filter, providerId ->
        models.filter { model ->
            val matchesQuery = query.isBlank() ||
                    model.displayName.contains(query, ignoreCase = true) ||
                    model.modelId.contains(query, ignoreCase = true) ||
                    model.description.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                ModelFilter.ALL -> true
                ModelFilter.FAVORITES -> model.isFavorite
                ModelFilter.CUSTOM -> model.isCustom
                ModelFilter.VISION -> model.supportsVision
                ModelFilter.REASONING -> model.supportsReasoning
                ModelFilter.TOOLS -> model.supportsToolCalling
            }

            val matchesProvider = providerId == null || model.providerId == providerId

            matchesQuery && matchesFilter && matchesProvider
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(modelId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(modelId, isFavorite)
        }
    }

    fun saveModel(model: ModelEntity) {
        viewModelScope.launch {
            repository.saveModel(model)
        }
    }

    fun deleteModel(id: String) {
        viewModelScope.launch {
            repository.deleteModel(id)
        }
    }
}
