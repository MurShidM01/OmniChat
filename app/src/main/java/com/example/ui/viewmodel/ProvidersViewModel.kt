package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OmniChatApplication
import com.example.data.model.ModelEntity
import com.example.data.model.ProviderEntity
import com.example.network.ConnectionTestResult
import com.example.network.NetworkResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProvidersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as OmniChatApplication).repository

    val providers: StateFlow<List<ProviderEntity>> = repository.allProviders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProvider(provider: ProviderEntity, plainApiKey: String? = null) {
        viewModelScope.launch {
            repository.saveProvider(provider, plainApiKey)
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repository.deleteProvider(id)
        }
    }

    fun toggleProviderEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setProviderEnabled(id, enabled)
        }
    }

    fun testProvider(
        provider: ProviderEntity,
        plainKey: String?,
        onResult: (ConnectionTestResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.testProvider(provider, plainKey)
            onResult(result)
        }
    }

    fun fetchModels(
        provider: ProviderEntity,
        onResult: (NetworkResult<List<ModelEntity>>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.fetchAndStoreModels(provider)
            onResult(result)
        }
    }
}
