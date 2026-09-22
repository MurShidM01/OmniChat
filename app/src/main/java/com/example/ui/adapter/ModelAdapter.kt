package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.model.ModelEntity
import com.example.data.presets.ProviderPreset
import com.example.databinding.ItemModelBinding

class ModelAdapter(
    private val onSelect: (ModelEntity) -> Unit,
    private val onFavoriteToggle: (ModelEntity) -> Unit
) : ListAdapter<ModelEntity, ModelAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ModelEntity>() {
            override fun areItemsTheSame(oldItem: ModelEntity, newItem: ModelEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ModelEntity, newItem: ModelEntity) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemModelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ModelEntity) {
            binding.tvModelName.text = item.displayName
            binding.tvModelId.text = item.modelId

            // Brand Logo
            val iconRes = when (item.providerId) {
                ProviderPreset.PROVIDER_ID_OPENAI -> R.drawable.ic_brand_openai
                ProviderPreset.PROVIDER_ID_ANTHROPIC -> R.drawable.ic_brand_anthropic
                ProviderPreset.PROVIDER_ID_DEEPSEEK -> R.drawable.ic_brand_deepseek
                ProviderPreset.PROVIDER_ID_GEMINI -> R.drawable.ic_brand_gemini
                else -> {
                    when {
                        item.modelId.contains("gpt", ignoreCase = true) || item.modelId.contains("o1", ignoreCase = true) || item.modelId.contains("o3", ignoreCase = true) -> R.drawable.ic_brand_openai
                        item.modelId.contains("claude", ignoreCase = true) -> R.drawable.ic_brand_anthropic
                        item.modelId.contains("deepseek", ignoreCase = true) -> R.drawable.ic_brand_deepseek
                        item.modelId.contains("gemini", ignoreCase = true) -> R.drawable.ic_brand_gemini
                        else -> R.drawable.ic_sparkle
                    }
                }
            }
            binding.ivModelBrand.setImageResource(iconRes)

            // Description
            if (item.description.isNotBlank()) {
                binding.tvModelDescription.text = item.description
                binding.tvModelDescription.visibility = View.VISIBLE
            } else {
                binding.tvModelDescription.visibility = View.GONE
            }

            // Context window
            val ctxK = item.contextWindow / 1000
            binding.badgeContext.text = if (ctxK >= 1000) "${ctxK / 1000}M ctx" else "${ctxK}k ctx"

            binding.badgeReasoning.visibility = if (item.supportsReasoning) View.VISIBLE else View.GONE
            binding.badgeVision.visibility = if (item.supportsVision) View.VISIBLE else View.GONE
            binding.badgeTools.visibility = if (item.supportsToolCalling) View.VISIBLE else View.GONE

            binding.btnFavorite.setImageResource(
                if (item.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            binding.root.setOnClickListener { onSelect(item) }
            binding.btnFavorite.setOnClickListener { onFavoriteToggle(item) }
        }
    }
}
