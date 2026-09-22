package com.example

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.databinding.ActivityMainBinding
import com.example.databinding.DialogDiagnosticsBinding
import com.example.network.NetworkDiagnostics
import com.example.ui.fragment.ChatFragment
import com.example.ui.fragment.ConversationsFragment
import com.example.ui.fragment.ModelsFragment
import com.example.ui.fragment.ProvidersFragment
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ModelsViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val chatViewModel: ChatViewModel by viewModels()
    private val modelsViewModel: ModelsViewModel by viewModels()

    private val chatFragment = ChatFragment()
    private val conversationsFragment = ConversationsFragment()
    private val modelsFragment = ModelsFragment()
    private val providersFragment = ProvidersFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Window Insets handling for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.setPadding(0, systemBars.top, 0, 0)
            binding.bottomNavContainer.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        setupNavigation()
        setupToolbarActions()
        observeActiveModel()

        // Set initial fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, chatFragment)
                .commit()
        }
    }

    private fun setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetFragment: Fragment = when (item.itemId) {
                R.id.nav_chat -> chatFragment
                R.id.nav_conversations -> conversationsFragment
                R.id.nav_models -> modelsFragment
                R.id.nav_providers -> providersFragment
                else -> chatFragment
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, targetFragment)
                .commit()
            true
        }
    }

    private fun setupToolbarActions() {
        binding.btnNewChat.setOnClickListener {
            chatViewModel.startNewConversation()
            switchToChatTab()
        }

        binding.btnModelSelector.setOnClickListener {
            switchToModelsTab()
        }

        binding.btnDiagnostics.setOnClickListener {
            showDiagnosticsDialog()
        }
    }

    fun switchToChatTab() {
        if (binding.bottomNav.selectedItemId != R.id.nav_chat) {
            binding.bottomNav.selectedItemId = R.id.nav_chat
        }
    }

    fun switchToProvidersTab() {
        if (binding.bottomNav.selectedItemId != R.id.nav_providers) {
            binding.bottomNav.selectedItemId = R.id.nav_providers
        }
    }

    fun switchToModelsTab() {
        if (binding.bottomNav.selectedItemId != R.id.nav_models) {
            binding.bottomNav.selectedItemId = R.id.nav_models
        }
    }

    private fun observeActiveModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.activeModel.collect { model ->
                    binding.btnModelSelector.text = model?.displayName ?: "Select Model"
                    val iconRes = when (model?.providerId) {
                        "provider_openai" -> R.drawable.ic_brand_openai
                        "provider_anthropic" -> R.drawable.ic_brand_anthropic
                        "provider_deepseek" -> R.drawable.ic_brand_deepseek
                        "provider_gemini" -> R.drawable.ic_brand_gemini
                        else -> R.drawable.ic_sparkle
                    }
                    binding.btnModelSelector.setIconResource(iconRes)
                }
            }
        }
    }

    private fun showDiagnosticsDialog() {
        val record = NetworkDiagnostics.getLastRecord()
        val dialogBinding = DialogDiagnosticsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (record != null) {
            val headersStr = record.sanitizedHeaders.entries.joinToString("\n") { "  ${it.key}: ${it.value}" }
            val text = """
                Provider: ${record.providerName}
                Adapter: ${record.adapterType}
                Method: ${record.requestMethod}
                Endpoint: ${record.endpointUrl}
                Status Code: ${record.statusCode ?: "N/A (Failed/Cancelled)"}
                Latency: ${record.latencyMs} ms
                Streaming: ${record.isStreaming}
                
                Headers:
                $headersStr
                ${if (!record.errorDetails.isNullOrBlank()) "\nError Details:\n" + record.errorDetails else ""}
            """.trimIndent()
            dialogBinding.tvDiagnosticsContent.text = text
        } else {
            dialogBinding.tvDiagnosticsContent.text = "No API requests executed in this session yet."
        }

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
