package com.example.ui.adapter

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.data.model.Attachment
import com.example.databinding.ItemAttachmentPreviewBinding

class AttachmentAdapter(
    private val onRemove: (String) -> Unit
) : ListAdapter<Attachment, AttachmentAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Attachment>() {
            override fun areItemsTheSame(oldItem: Attachment, newItem: Attachment) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Attachment, newItem: Attachment) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttachmentPreviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAttachmentPreviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(attachment: Attachment) {
            if (!attachment.base64Data.isNullOrBlank()) {
                try {
                    val bytes = Base64.decode(attachment.base64Data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.ivPreview.setImageBitmap(bitmap)
                } catch (e: Exception) {}
            }
            binding.btnRemove.setOnClickListener {
                onRemove(attachment.id)
            }
        }
    }
}
