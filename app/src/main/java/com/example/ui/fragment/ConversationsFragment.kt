package com.example.ui.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.MainActivity
import com.example.databinding.FragmentConversationsBinding
import com.example.ui.adapter.ConversationAdapter
import com.example.ui.util.ModernModalHelper
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ConversationsViewModel
import kotlinx.coroutines.launch

class ConversationsFragment : Fragment() {

    private var _binding: FragmentConversationsBinding? = null
    private val binding get() = _binding!!

    private val conversationsViewModel: ConversationsViewModel by activityViewModels()
    private val chatViewModel: ChatViewModel by activityViewModels()

    private lateinit var conversationAdapter: ConversationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        conversationAdapter = ConversationAdapter(
            onClick = { conv ->
                chatViewModel.loadConversation(conv.id)
                (activity as? MainActivity)?.switchToChatTab()
            },
            onPinToggle = { conv ->
                conversationsViewModel.togglePinned(conv.id, !conv.isPinned)
            },
            onDelete = { conv ->
                showDeleteConfirmDialog(conv.id, conv.title)
            }
        )

        binding.recyclerConversations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = conversationAdapter
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                conversationsViewModel.searchQuery.value = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.fabNewConversation.setOnClickListener {
            conversationsViewModel.createNewConversation { newConv ->
                chatViewModel.loadConversation(newConv.id)
                (activity as? MainActivity)?.switchToChatTab()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                conversationsViewModel.filteredConversations.collect { list ->
                    conversationAdapter.submitList(list)
                    binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(id: String, title: String) {
        ModernModalHelper.showModal(
            context = requireContext(),
            title = "Delete Conversation?",
            message = "Are you sure you want to delete \"$title\"? This cannot be undone.",
            type = ModernModalHelper.ModalType.DANGER,
            positiveButtonText = "Delete",
            negativeButtonText = "Cancel",
            onPositiveClick = {
                conversationsViewModel.deleteConversation(id)
                if (chatViewModel.activeConversationId.value == id) {
                    chatViewModel.loadConversation(null)
                }
                ModernModalHelper.showSnackbar(binding.root, "Conversation deleted", isSuccess = true)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
