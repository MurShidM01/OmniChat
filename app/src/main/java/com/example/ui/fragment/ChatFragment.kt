package com.example.ui.fragment

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.MainActivity
import com.example.R
import com.example.data.model.Attachment
import com.example.data.presets.ProviderPreset
import com.example.databinding.FragmentChatBinding
import com.example.ui.adapter.AttachmentAdapter
import com.example.ui.adapter.MessageAdapter
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val chatViewModel: ChatViewModel by activityViewModels()

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var attachmentAdapter: AttachmentAdapter

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processSelectedImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        messageAdapter = MessageAdapter(
            onToggleThinking = { messageId, currentExpanded ->
                chatViewModel.toggleThinkingExpanded(messageId, currentExpanded)
            },
            onDeleteMessage = { messageId ->
                chatViewModel.deleteMessage(messageId)
            }
        )

        binding.recyclerMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }

        attachmentAdapter = AttachmentAdapter { attachmentId ->
            chatViewModel.removeAttachment(attachmentId)
        }
        binding.recyclerAttachments.adapter = attachmentAdapter
    }

    private fun setupListeners() {
        binding.fabSend.setOnClickListener {
            if (chatViewModel.isGenerating.value) {
                chatViewModel.stopGeneration()
            } else {
                val activeProvider = chatViewModel.activeProvider.value
                val hasKey = activeProvider?.encryptedApiKey?.isNotBlank() == true
                if (!hasKey) {
                    Toast.makeText(
                        requireContext(),
                        "Please configure your API key for ${activeProvider?.name ?: "active provider"} first.",
                        Toast.LENGTH_LONG
                    ).show()
                    (activity as? MainActivity)?.switchToProvidersTab()
                    return@setOnClickListener
                }

                val text = binding.etMessage.text.toString()
                if (text.isNotBlank() || chatViewModel.attachments.value.isNotEmpty()) {
                    chatViewModel.sendMessage(text)
                    binding.etMessage.setText("")
                }
            }
        }

        binding.btnAttach.setOnClickListener {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Missing key banner action
        binding.btnAddKeyBanner.setOnClickListener {
            (activity as? MainActivity)?.switchToProvidersTab()
        }

        binding.tvActiveModelPill.setOnClickListener {
            (activity as? MainActivity)?.switchToModelsTab()
        }

        // Quick starter prompts
        binding.cardStarter1.setOnClickListener {
            sendMessageOrPromptKey(binding.tvStarter1Text.text.toString())
        }
        binding.cardStarter2.setOnClickListener {
            sendMessageOrPromptKey(binding.tvStarter2Text.text.toString())
        }
        binding.cardStarter3.setOnClickListener {
            sendMessageOrPromptKey(binding.tvStarter3Text.text.toString())
        }
        binding.cardStarter4.setOnClickListener {
            sendMessageOrPromptKey(binding.tvStarter4Text.text.toString())
        }
    }

    private fun sendMessageOrPromptKey(text: String) {
        val activeProvider = chatViewModel.activeProvider.value
        val hasKey = activeProvider?.encryptedApiKey?.isNotBlank() == true
        if (!hasKey) {
            Toast.makeText(
                requireContext(),
                "Please configure your API key for ${activeProvider?.name ?: "active provider"} first.",
                Toast.LENGTH_LONG
            ).show()
            (activity as? MainActivity)?.switchToProvidersTab()
        } else {
            chatViewModel.sendMessage(text)
        }
    }

    private fun processSelectedImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val outputStream = ByteArrayOutputStream()
                    val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                        val factor = 1024f / Math.max(bitmap.width, bitmap.height)
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * factor).toInt(),
                            (bitmap.height * factor).toInt(),
                            true
                        )
                    } else {
                        bitmap
                    }
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                    val attachment = Attachment(
                        id = UUID.randomUUID().toString(),
                        name = "image_${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg",
                        base64Data = base64,
                        uriString = uri.toString()
                    )

                    withContext(Dispatchers.Main) {
                        chatViewModel.addAttachment(attachment)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    chatViewModel.messages.collect { list ->
                        messageAdapter.submitList(list) {
                            if (list.isNotEmpty()) {
                                binding.recyclerMessages.smoothScrollToPosition(list.size - 1)
                            }
                        }
                        binding.layoutEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    chatViewModel.attachments.collect { list ->
                        attachmentAdapter.submitList(list)
                        binding.recyclerAttachments.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    chatViewModel.isGenerating.collect { generating ->
                        if (generating) {
                            binding.fabSend.setImageResource(R.drawable.ic_stop)
                            binding.fabSend.contentDescription = "Stop Generating"
                        } else {
                            binding.fabSend.setImageResource(R.drawable.ic_send)
                            binding.fabSend.contentDescription = "Send Message"
                        }
                    }
                }

                launch {
                    chatViewModel.activeModel.collect { model ->
                        val provider = chatViewModel.activeProvider.value
                        val modelName = model?.displayName ?: "GPT-4o"
                        val providerName = provider?.name ?: "OpenAI"
                        binding.tvActiveModelPill.text = "⚡ $modelName • $providerName"

                        val brandIcon = when (model?.providerId) {
                            ProviderPreset.PROVIDER_ID_OPENAI -> R.drawable.ic_brand_openai
                            ProviderPreset.PROVIDER_ID_ANTHROPIC -> R.drawable.ic_brand_anthropic
                            ProviderPreset.PROVIDER_ID_DEEPSEEK -> R.drawable.ic_brand_deepseek
                            ProviderPreset.PROVIDER_ID_GEMINI -> R.drawable.ic_brand_gemini
                            else -> R.drawable.ic_sparkle
                        }
                        binding.ivEmptyBrandIcon.setImageResource(brandIcon)
                    }
                }

                launch {
                    chatViewModel.activeProvider.collect { provider ->
                        if (provider != null) {
                            val hasKey = provider.encryptedApiKey.isNotBlank()
                            binding.cardMissingKey.visibility = if (hasKey) View.GONE else View.VISIBLE
                            binding.tvMissingKeyTitle.text = "⚠️ ${provider.name} API Key Missing"
                            binding.tvMissingKeySubtitle.text = "Add your official ${provider.name} key to start chatting"
                        } else {
                            binding.cardMissingKey.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
