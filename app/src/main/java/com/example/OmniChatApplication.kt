package com.example

import android.app.Application
import com.example.data.presets.ProviderPreset
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OmniChatApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ChatRepository(this)

        // Sync official providers, clean placeholders, and seed default models
        applicationScope.launch {
            repository.syncOfficialProvidersAndCleanPlaceholders()
        }
    }
}
