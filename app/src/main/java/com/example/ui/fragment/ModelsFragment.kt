package com.example.ui.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.MainActivity
import com.example.R
import com.example.data.model.ModelEntity
import com.example.data.model.ProviderEntity
import com.example.data.presets.ProviderPreset
import com.example.databinding.DialogEditModelBinding
import com.example.databinding.FragmentModelsBinding
import com.example.ui.adapter.ModelAdapter
import com.example.ui.util.ModernModalHelper
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ModelFilter
import com.example.ui.viewmodel.ModelsViewModel
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!

    private val modelsViewModel: ModelsViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by activityViewModels()

    private lateinit var modelAdapter: ModelAdapter
    private var dynamicProviderChipIds = mutableListOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelAdapter = ModelAdapter(
            onSelect = { model ->
                chatViewModel.setActiveModel(model)
                ModernModalHelper.showSnackbar(binding.root, "Selected: ${model.displayName}", isSuccess = true)
                (activity as? MainActivity)?.switchToChatTab()
            },
            onFavoriteToggle = { model ->
                modelsViewModel.toggleFavorite(model.id, !model.isFavorite)
            }
        )

        binding.recyclerModels.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = modelAdapter
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                modelsViewModel.searchQuery.value = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                modelsViewModel.selectedProviderId.value = null
                modelsViewModel.selectedFilter.value = ModelFilter.ALL
                return@setOnCheckedStateChangeListener
            }

            val checkedId = checkedIds.first()
            when (checkedId) {
                R.id.chip_all -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.ALL
                }
                R.id.chip_favorites -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.FAVORITES
                }
                R.id.chip_custom -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.CUSTOM
                }
                R.id.chip_reasoning -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.REASONING
                }
                R.id.chip_vision -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.VISION
                }
                else -> {
                    // Check if it's a dynamic provider chip
                    val chip = group.findViewById<Chip>(checkedId)
                    val providerId = chip?.tag as? String
                    if (providerId != null) {
                        modelsViewModel.selectedProviderId.value = providerId
                        modelsViewModel.selectedFilter.value = ModelFilter.ALL
                    } else {
                        modelsViewModel.selectedProviderId.value = null
                        modelsViewModel.selectedFilter.value = ModelFilter.ALL
                    }
                }
            }
        }

        binding.fabAddModel.setOnClickListener {
            showAddCustomModelDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    modelsViewModel.filteredModels.collect { list ->
                        modelAdapter.submitList(list)
                    }
                }

                launch {
                    modelsViewModel.allProviders.collect { providers ->
                        updateDynamicProviderChips(providers)
                    }
                }
            }
        }
    }

    private fun updateDynamicProviderChips(providers: List<ProviderEntity>) {
        val chipGroup = binding.chipGroupFilters

        // Remove previously added dynamic provider chips
        for (id in dynamicProviderChipIds) {
            val view = chipGroup.findViewById<View>(id)
            if (view != null) {
                chipGroup.removeView(view)
            }
        }
        dynamicProviderChipIds.clear()

        // Add chip for each provider
        for (provider in providers) {
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = provider.name
                tag = provider.id
                isCheckable = true
                isClickable = true

                val iconRes = when (provider.id) {
                    ProviderPreset.PROVIDER_ID_OPENAI -> R.drawable.ic_brand_openai
                    ProviderPreset.PROVIDER_ID_ANTHROPIC -> R.drawable.ic_brand_anthropic
                    ProviderPreset.PROVIDER_ID_DEEPSEEK -> R.drawable.ic_brand_deepseek
                    ProviderPreset.PROVIDER_ID_GEMINI -> R.drawable.ic_brand_gemini
                    else -> R.drawable.ic_sparkle
                }
                setChipIconResource(iconRes)
                isChipIconVisible = true
                chipIconSize = 18f * resources.displayMetrics.density
            }

            chipGroup.addView(chip)
            dynamicProviderChipIds.add(chip.id)
        }
    }

    private fun showAddCustomModelDialog() {
        val dialogBinding = DialogEditModelBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val providers = modelsViewModel.allProviders.value
        val providerNames = providers.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, providerNames)
        dialogBinding.actvProvider.setAdapter(adapter)

        val activeProv = chatViewModel.activeProvider.value
        val defaultIndex = providers.indexOfFirst { it.id == activeProv?.id }.coerceAtLeast(0)
        if (providers.isNotEmpty()) {
            dialogBinding.actvProvider.setText(providers[defaultIndex].name, false)
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val modelId = dialogBinding.etModelId.text.toString().trim()
            val displayName = dialogBinding.etDisplayName.text.toString().trim().ifEmpty { modelId }
            val contextWin = dialogBinding.etContextWindow.text.toString().toIntOrNull() ?: 128000

            if (modelId.isBlank()) {
                ModernModalHelper.showSnackbar(binding.root, "Please enter a Model ID", isSuccess = false)
                return@setOnClickListener
            }

            val selectedName = dialogBinding.actvProvider.text.toString()
            val selectedProvider = providers.find { it.name == selectedName }
                ?: providers.firstOrNull()
            val targetProviderId = selectedProvider?.id ?: ProviderPreset.PROVIDER_ID_OPENAI

            val newModel = ModelEntity(
                id = "$targetProviderId::$modelId",
                modelId = modelId,
                providerId = targetProviderId,
                displayName = displayName,
                description = "Custom model (${selectedProvider?.name ?: "Custom"})",
                contextWindow = contextWin,
                supportsVision = dialogBinding.switchVision.isChecked,
                supportsReasoning = dialogBinding.switchReasoning.isChecked,
                supportsToolCalling = dialogBinding.switchTools.isChecked,
                supportsStreaming = true,
                isFavorite = true,
                isCustom = true
            )

            modelsViewModel.saveModel(newModel)
            chatViewModel.setActiveModel(newModel)
            dialog.dismiss()
            ModernModalHelper.showSnackbar(binding.root, "Custom model \"$displayName\" saved!", isSuccess = true)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
