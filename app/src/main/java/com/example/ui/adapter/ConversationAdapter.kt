package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.model.ConversationEntity
import com.example.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (ConversationEntity) -> Unit,
    private val onPinToggle: (ConversationEntity) -> Unit,
    private val onDelete: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ConversationAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity) =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ConversationEntity) {
            binding.tvTitle.text = item.title

            val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            val dateStr = timeFormat.format(Date(item.updatedAt))
            val modelDesc = if (item.modelId.isNotBlank()) " • ${item.modelId}" else ""
            binding.tvSubtitle.text = "$dateStr$modelDesc"

            binding.btnPin.setImageResource(
                if (item.isPinned) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            binding.root.setOnClickListener { onClick(item) }
            binding.btnPin.setOnClickListener { onPinToggle(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
