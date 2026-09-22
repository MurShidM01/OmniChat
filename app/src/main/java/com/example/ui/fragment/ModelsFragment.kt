package com.example.ui.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.MainActivity
import com.example.R
import com.example.data.model.ModelEntity
import com.example.data.presets.ProviderPreset
import com.example.databinding.DialogEditModelBinding
import com.example.databinding.FragmentModelsBinding
import com.example.ui.adapter.ModelAdapter
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ModelFilter
import com.example.ui.viewmodel.ModelsViewModel
import kotlinx.coroutines.launch

class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!

    private val modelsViewModel: ModelsViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by activityViewModels()

    private lateinit var modelAdapter: ModelAdapter

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
                Toast.makeText(requireContext(), "Selected: ${model.displayName}", Toast.LENGTH_SHORT).show()
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

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chip_openai) -> {
                    modelsViewModel.selectedProviderId.value = ProviderPreset.PROVIDER_ID_OPENAI
                    modelsViewModel.selectedFilter.value = ModelFilter.ALL
                }
                checkedIds.contains(R.id.chip_anthropic) -> {
                    modelsViewModel.selectedProviderId.value = ProviderPreset.PROVIDER_ID_ANTHROPIC
                    modelsViewModel.selectedFilter.value = ModelFilter.ALL
                }
                checkedIds.contains(R.id.chip_deepseek) -> {
                    modelsViewModel.selectedProviderId.value = ProviderPreset.PROVIDER_ID_DEEPSEEK
                    modelsViewModel.selectedFilter.value = ModelFilter.ALL
                }
                checkedIds.contains(R.id.chip_gemini) -> {
                    modelsViewModel.selectedProviderId.value = ProviderPreset.PROVIDER_ID_GEMINI
                    modelsViewModel.selectedFilter.value = ModelFilter.ALL
                }
                checkedIds.contains(R.id.chip_favorites) -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.FAVORITES
                }
                checkedIds.contains(R.id.chip_reasoning) -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.REASONING
                }
                checkedIds.contains(R.id.chip_vision) -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.VISION
                }
                else -> {
                    modelsViewModel.selectedProviderId.value = null
                    modelsViewModel.selectedFilter.value = ModelFilter.ALL
                }
            }
        }

        binding.fabAddModel.setOnClickListener {
            showAddCustomModelDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                modelsViewModel.filteredModels.collect { list ->
                    modelAdapter.submitList(list)
                }
            }
        }
    }

    private fun showAddCustomModelDialog() {
        val dialogBinding = DialogEditModelBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSave.setOnClickListener {
            val modelId = dialogBinding.etModelId.text.toString().trim()
            val displayName = dialogBinding.etDisplayName.text.toString().trim().ifEmpty { modelId }
            val contextWin = dialogBinding.etContextWindow.text.toString().toIntOrNull() ?: 128000

            if (modelId.isBlank()) {
                Toast.makeText(requireContext(), "Please enter a Model ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentProviderId = chatViewModel.activeProvider.value?.id ?: ProviderPreset.PROVIDER_ID_OPENAI
            val newModel = ModelEntity(
                id = "$currentProviderId::$modelId",
                modelId = modelId,
                providerId = currentProviderId,
                displayName = displayName,
                description = "Custom model",
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
            Toast.makeText(requireContext(), "Custom model saved!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
