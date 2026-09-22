package com.example.ui.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.text.Html
import android.text.SpannableString
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.databinding.ItemMessageAssistantBinding
import com.example.databinding.ItemMessageUserBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val onToggleThinking: (String, Boolean) -> Unit,
    private val onDeleteMessage: (String) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
            MessageRole.USER -> TYPE_USER
            else -> TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val binding = ItemMessageUserBinding.inflate(inflater, parent, false)
            UserViewHolder(binding)
        } else {
            val binding = ItemMessageAssistantBinding.inflate(inflater, parent, false)
            AssistantViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is UserViewHolder) {
            holder.bind(item)
        } else if (holder is AssistantViewHolder) {
            holder.bind(item)
        }
    }

    inner class UserViewHolder(private val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.tvContent.text = message.content

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            binding.tvTimestamp.text = timeFormat.format(Date(message.timestamp))

            // Attachment preview if present
            if (message.attachments.isNotEmpty()) {
                val firstAttach = message.attachments.first()
                if (!firstAttach.base64Data.isNullOrBlank()) {
                    try {
                        val decodedBytes = Base64.decode(firstAttach.base64Data, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            binding.ivAttachment.setImageBitmap(bitmap)
                            binding.ivAttachment.visibility = View.VISIBLE
                        } else {
                            binding.ivAttachment.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        binding.ivAttachment.visibility = View.GONE
                    }
                } else {
                    binding.ivAttachment.visibility = View.GONE
                }
            } else {
                binding.ivAttachment.visibility = View.GONE
            }

            binding.btnCopy.setOnClickListener {
                copyToClipboard(itemView.context, message.content)
            }
        }
    }

    inner class AssistantViewHolder(private val binding: ItemMessageAssistantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val modelId = message.modelUsed ?: ""
            val (brandIcon, assistantLabel) = when {
                modelId.contains("gpt", ignoreCase = true) || modelId.contains("o1", ignoreCase = true) || modelId.contains("o3", ignoreCase = true) || modelId.contains("openai", ignoreCase = true) ->
                    Pair(R.drawable.ic_brand_openai, if (modelId.isNotEmpty()) modelId else "OpenAI")
                modelId.contains("claude", ignoreCase = true) || modelId.contains("anthropic", ignoreCase = true) ->
                    Pair(R.drawable.ic_brand_anthropic, if (modelId.isNotEmpty()) modelId else "Claude")
                modelId.contains("deepseek", ignoreCase = true) ->
                    Pair(R.drawable.ic_brand_deepseek, if (modelId.isNotEmpty()) modelId else "DeepSeek")
                modelId.contains("gemini", ignoreCase = true) ->
                    Pair(R.drawable.ic_brand_gemini, if (modelId.isNotEmpty()) modelId else "Gemini")
                else ->
                    Pair(R.drawable.ic_robot, if (modelId.isNotEmpty()) modelId else "AI Assistant")
            }
            binding.ivAssistantAvatar.setImageResource(brandIcon)
            binding.tvAssistantName.text = assistantLabel

            // Render basic markdown styling for bold, code, bullets, headers
            binding.tvContent.text = renderMarkdown(message.content)

            // Streaming indicator
            binding.progressStreaming.visibility = if (message.isStreaming) View.VISIBLE else View.GONE

            // Collapsible Thinking / Reasoning Process
            val reasoning = message.reasoningContent
            if (!reasoning.isNullOrBlank()) {
                binding.cardThinking.visibility = View.VISIBLE
                binding.tvThinkingContent.text = reasoning

                val isExpanded = message.isThinkingExpanded
                binding.tvThinkingContent.visibility = if (isExpanded) View.VISIBLE else View.GONE
                binding.ivThinkingChevron.rotation = if (isExpanded) 180f else 0f

                binding.headerThinking.setOnClickListener {
                    onToggleThinking(message.id, isExpanded)
                }
            } else {
                binding.cardThinking.visibility = View.GONE
            }

            // Error display
            if (message.isError && !message.errorMessage.isNullOrBlank()) {
                binding.layoutError.visibility = View.VISIBLE
                binding.tvErrorText.text = message.errorMessage
            } else {
                binding.layoutError.visibility = View.GONE
            }

            // Metrics badge (latency and tokens)
            if (message.latencyMs > 0 || message.tokenCount > 0) {
                binding.tvMetrics.visibility = View.VISIBLE
                val parts = mutableListOf<String>()
                if (message.latencyMs > 0) parts.add("${message.latencyMs}ms")
                if (message.tokenCount > 0) parts.add("~${message.tokenCount} tokens")
                binding.tvMetrics.text = parts.joinToString(" • ")
            } else {
                binding.tvMetrics.visibility = View.GONE
            }

            binding.btnCopy.setOnClickListener {
                copyToClipboard(itemView.context, message.content)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteMessage(message.id)
            }
        }
    }

    private fun renderMarkdown(raw: String): CharSequence {
        if (raw.isBlank()) return ""
        return try {
            // Simple robust markdown parser for HTML Spanned
            var formatted = raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

            // Code blocks ```code``` -> <pre><code>code</code></pre>
            val codeBlockRegex = Regex("```([a-zA-Z0-9]*)\\n?([\\s\\S]*?)```")
            formatted = codeBlockRegex.replace(formatted) { matchResult ->
                val code = matchResult.groupValues[2]
                "<br/><tt style=\"background-color:#1E293B;color:#F8FAFC;\">${code.replace("\n", "<br/>")}</tt><br/>"
            }

            // Inline code `code` -> <tt>code</tt>
            formatted = formatted.replace(Regex("`([^`]+)`"), "<tt>$1</tt>")

            // Bold **text** -> <b>text</b>
            formatted = formatted.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")

            // Italic *text* -> <i>text</i>
            formatted = formatted.replace(Regex("\\*([^*]+)\\*"), "<i>$1</i>")

            // Newlines
            formatted = formatted.replace("\n", "<br/>")

            Html.fromHtml(formatted, Html.FROM_HTML_MODE_LEGACY)
        } catch (e: Exception) {
            raw
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OmniChat Message", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
