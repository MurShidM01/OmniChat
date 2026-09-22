package com.example.ui.fragment

import android.app.AlertDialog
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.R
import com.example.data.model.ApiCompatibilityType
import com.example.data.model.AuthType
import com.example.data.model.ProviderEntity
import com.example.data.presets.ProviderPreset
import com.example.databinding.DialogConfigureKeyBinding
import com.example.databinding.DialogEditProviderBinding
import com.example.databinding.FragmentProvidersBinding
import com.example.network.ConnectionTestResult
import com.example.network.NetworkResult
import com.example.security.KeystoreSecretManager
import com.example.ui.adapter.ProviderAdapterUi
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ProvidersViewModel
import kotlinx.coroutines.launch
import java.util.UUID

class ProvidersFragment : Fragment() {

    private var _binding: FragmentProvidersBinding? = null
    private val binding get() = _binding!!

    private val providersViewModel: ProvidersViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by activityViewModels()

    private lateinit var providerAdapterUi: ProviderAdapterUi

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProvidersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        providerAdapterUi = ProviderAdapterUi(
            onToggleEnabled = { provider, isEnabled ->
                providersViewModel.toggleProviderEnabled(provider.id, isEnabled)
            },
            onConfigureKey = { provider ->
                showConfigureKeyDialog(provider)
            },
            onTest = { provider ->
                testProviderConnection(provider)
            },
            onFetchModels = { provider ->
                fetchProviderModels(provider)
            },
            onEdit = { provider ->
                showEditProviderDialog(provider)
            },
            onDelete = { provider ->
                showDeleteConfirmDialog(provider)
            }
        )

        binding.recyclerProviders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = providerAdapterUi
        }

        binding.fabAddProvider.setOnClickListener {
            showEditProviderDialog(null)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                providersViewModel.providers.collect { list ->
                    providerAdapterUi.submitList(list)
                }
            }
        }
    }

    private fun showConfigureKeyDialog(provider: ProviderEntity) {
        val dialogBinding = DialogConfigureKeyBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val preset = ProviderPreset.findPreset(provider.id)
        val providerTitle = preset?.title ?: provider.name

        dialogBinding.tvDialogTitle.text = "Connect $providerTitle"
        dialogBinding.tvDialogSubtitle.text = "Stored encrypted on this device with Android Keystore"

        if (preset != null) {
            dialogBinding.ivProviderIcon.setImageResource(preset.iconRes)
            dialogBinding.tilApiKey.hint = "API Key (${preset.apiKeyPlaceholder})"
            dialogBinding.btnGetKeyLink.text = "Get $providerTitle API key ↗"
            dialogBinding.btnGetKeyLink.setOnClickListener {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(preset.apiKeyDocsUrl))
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), preset.apiKeyDocsUrl, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            dialogBinding.ivProviderIcon.setImageResource(R.drawable.ic_tune)
            dialogBinding.btnGetKeyLink.visibility = View.GONE
        }

        // Prefill existing decrypted key
        val existingKey = KeystoreSecretManager.decrypt(provider.encryptedApiKey)
        if (existingKey.isNotBlank()) {
            dialogBinding.etApiKey.setText(existingKey)
        }

        // Paste button
        dialogBinding.btnPasteKey.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val pasteText = clip.getItemAt(0).coerceToText(requireContext()).toString().trim()
                    if (pasteText.isNotBlank()) {
                        dialogBinding.etApiKey.setText(pasteText)
                        Toast.makeText(requireContext(), "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Advanced Settings Toggle
        dialogBinding.etCustomBaseUrl.setText(provider.baseUrl)
        dialogBinding.switchStream.isChecked = provider.supportsStreaming
        dialogBinding.tvAdvancedToggle.setOnClickListener {
            val isVisible = dialogBinding.layoutAdvancedSettings.visibility == View.VISIBLE
            dialogBinding.layoutAdvancedSettings.visibility = if (isVisible) View.GONE else View.VISIBLE
            dialogBinding.tvAdvancedToggle.text = if (isVisible) "▼ Advanced Settings (Custom URL / Proxy)" else "▲ Hide Advanced Settings"
        }

        // Test in dialog
        dialogBinding.btnTestDialog.setOnClickListener {
            val testKey = dialogBinding.etApiKey.text.toString().trim()
            if (testKey.isBlank()) {
                dialogBinding.tvTestStatus.visibility = View.VISIBLE
                dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_rose))
                dialogBinding.tvTestStatus.text = "Please enter an API key to test."
                return@setOnClickListener
            }
            dialogBinding.tvTestStatus.visibility = View.VISIBLE
            dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            dialogBinding.tvTestStatus.text = "Pinging $providerTitle API..."

            val tempProvider = provider.copy(
                baseUrl = dialogBinding.etCustomBaseUrl.text.toString().trim().ifEmpty { provider.baseUrl }
            )

            providersViewModel.testProvider(tempProvider, testKey) { result ->
                if (result.isSuccess) {
                    dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green_text))
                    dialogBinding.tvTestStatus.text = "✓ Connection Successful! Latency: ${result.latencyMs}ms"
                } else {
                    dialogBinding.tvTestStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_rose))
                    dialogBinding.tvTestStatus.text = "✗ Connection Failed: ${result.message}"
                }
            }
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val key = dialogBinding.etApiKey.text.toString().trim()
            val customUrl = dialogBinding.etCustomBaseUrl.text.toString().trim().ifEmpty { provider.baseUrl }
            val stream = dialogBinding.switchStream.isChecked

            val updatedProvider = provider.copy(
                baseUrl = customUrl,
                supportsStreaming = stream,
                isEnabled = true
            )

            providersViewModel.saveProvider(updatedProvider, plainApiKey = key)
            dialog.dismiss()
            Toast.makeText(requireContext(), "$providerTitle configured & enabled!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun testProviderConnection(provider: ProviderEntity) {
        Toast.makeText(requireContext(), "Testing connection to ${provider.name}...", Toast.LENGTH_SHORT).show()
        providersViewModel.testProvider(provider, null) { result: ConnectionTestResult ->
            if (result.isSuccess) {
                providerAdapterUi.setLatency(provider.id, result.latencyMs)
            }
            AlertDialog.Builder(requireContext())
                .setTitle(if (result.isSuccess) "Connection Successful" else "Connection Failed")
                .setIcon(if (result.isSuccess) R.drawable.ic_check else R.drawable.ic_error)
                .setMessage(
                    "${result.message}\nLatency: ${result.latencyMs}ms" +
                            if (result.discoveredModelsCount > 0) "\nDiscovered models: ${result.discoveredModelsCount}" else ""
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun fetchProviderModels(provider: ProviderEntity) {
        Toast.makeText(requireContext(), "Syncing models from ${provider.name}...", Toast.LENGTH_SHORT).show()
        providersViewModel.fetchModels(provider) { result ->
            when (result) {
                is NetworkResult.Success -> {
                    Toast.makeText(
                        requireContext(),
                        "Loaded ${result.data.size} models from ${provider.name}!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(requireContext(), "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(provider: ProviderEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Provider?")
            .setMessage("Are you sure you want to delete \"${provider.name}\"? Discovered models will also be removed.")
            .setPositiveButton("Delete") { _, _ ->
                providersViewModel.deleteProvider(provider.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditProviderDialog(existing: ProviderEntity?) {
        val dialogBinding = DialogEditProviderBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val isEditing = existing != null
        dialogBinding.tvDialogTitle.text = if (isEditing) "Edit Provider" else "Add Custom Provider"

        val compatTypes = ApiCompatibilityType.values()
        val compatNames = compatTypes.map { it.displayName }
        dialogBinding.spinnerCompatibility.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            compatNames
        )

        // Presets Adapter
        val presets = ProviderPreset.getPresets()
        val presetTitles = listOf("Custom (Manual URL)") + presets.map { it.title }
        dialogBinding.spinnerPreset.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            presetTitles
        )

        dialogBinding.spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val selectedPreset = presets[position - 1].template
                    dialogBinding.etName.setText(selectedPreset.name)
                    dialogBinding.etBaseUrl.setText(selectedPreset.baseUrl)
                    dialogBinding.etChatEndpoint.setText(selectedPreset.chatEndpoint)
                    dialogBinding.etModelsEndpoint.setText(selectedPreset.modelsEndpoint)
                    dialogBinding.etCustomHeaders.setText(selectedPreset.customHeadersJson)
                    dialogBinding.etCustomBody.setText(selectedPreset.customBodyJson)
                    val compatIndex = compatTypes.indexOf(selectedPreset.compatibilityType)
                    if (compatIndex >= 0) {
                        dialogBinding.spinnerCompatibility.setSelection(compatIndex)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (existing != null) {
            dialogBinding.etName.setText(existing.name)
            dialogBinding.etBaseUrl.setText(existing.baseUrl)
            dialogBinding.etChatEndpoint.setText(existing.chatEndpoint)
            dialogBinding.etModelsEndpoint.setText(existing.modelsEndpoint)
            dialogBinding.etCustomHeaders.setText(existing.customHeadersJson)
            dialogBinding.etCustomBody.setText(existing.customBodyJson)
            dialogBinding.switchStreaming.isChecked = existing.supportsStreaming
            val decryptedKey = KeystoreSecretManager.decrypt(existing.encryptedApiKey)
            dialogBinding.etApiKey.setText(decryptedKey)

            val index = compatTypes.indexOf(existing.compatibilityType)
            if (index >= 0) dialogBinding.spinnerCompatibility.setSelection(index)
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etName.text.toString().trim()
            val baseUrl = dialogBinding.etBaseUrl.text.toString().trim()
            val apiKey = dialogBinding.etApiKey.text.toString().trim()
            val chatEndpoint = dialogBinding.etChatEndpoint.text.toString().trim().ifEmpty { "/chat/completions" }
            val modelsEndpoint = dialogBinding.etModelsEndpoint.text.toString().trim().ifEmpty { "/models" }
            val customHeaders = dialogBinding.etCustomHeaders.text.toString().trim().ifEmpty { "{}" }
            val customBody = dialogBinding.etCustomBody.text.toString().trim().ifEmpty { "{}" }
            val streaming = dialogBinding.switchStreaming.isChecked
            val selectedCompat = compatTypes[dialogBinding.spinnerCompatibility.selectedItemPosition]

            if (name.isBlank() || baseUrl.isBlank()) {
                Toast.makeText(requireContext(), "Name and Base URL are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val authType = when (selectedCompat) {
                ApiCompatibilityType.ANTHROPIC_MESSAGES -> AuthType.API_KEY_HEADER
                else -> AuthType.BEARER_TOKEN
            }
            val authHeader = if (selectedCompat == ApiCompatibilityType.ANTHROPIC_MESSAGES) "x-api-key" else "Authorization"

            val providerToSave = existing?.copy(
                name = name,
                baseUrl = baseUrl,
                compatibilityType = selectedCompat,
                chatEndpoint = chatEndpoint,
                modelsEndpoint = modelsEndpoint,
                authType = authType,
                customAuthHeader = authHeader,
                customHeadersJson = customHeaders,
                customBodyJson = customBody,
                supportsStreaming = streaming
            ) ?: ProviderEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                baseUrl = baseUrl,
                compatibilityType = selectedCompat,
                chatEndpoint = chatEndpoint,
                modelsEndpoint = modelsEndpoint,
                authType = authType,
                customAuthHeader = authHeader,
                customHeadersJson = customHeaders,
                customBodyJson = customBody,
                supportsStreaming = streaming
            )

            providersViewModel.saveProvider(providerToSave, plainApiKey = apiKey)
            dialog.dismiss()
            Toast.makeText(requireContext(), "Provider saved securely!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
