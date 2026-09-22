package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.model.ProviderEntity
import com.example.data.presets.ProviderPreset
import com.example.databinding.ItemProviderBinding
import com.example.security.KeystoreSecretManager

class ProviderAdapterUi(
    private val onToggleEnabled: (ProviderEntity, Boolean) -> Unit,
    private val onConfigureKey: (ProviderEntity) -> Unit,
    private val onTest: (ProviderEntity) -> Unit,
    private val onFetchModels: (ProviderEntity) -> Unit,
    private val onEdit: (ProviderEntity) -> Unit,
    private val onDelete: (ProviderEntity) -> Unit
) : ListAdapter<ProviderEntity, ProviderAdapterUi.ViewHolder>(DiffCallback) {

    private val latencyMap = mutableMapOf<String, Long>()

    fun setLatency(providerId: String, latencyMs: Long) {
        latencyMap[providerId] = latencyMs
        notifyItemChanged(currentList.indexOfFirst { it.id == providerId })
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ProviderEntity>() {
            override fun areItemsTheSame(oldItem: ProviderEntity, newItem: ProviderEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ProviderEntity, newItem: ProviderEntity) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProviderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemProviderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProviderEntity) {
            val preset = ProviderPreset.findPreset(item.id)
            val isOfficial = preset != null || ProviderPreset.OFFICIAL_IDS.contains(item.id)

            binding.tvProviderName.text = item.name
            binding.badgeOfficial.visibility = if (isOfficial) View.VISIBLE else View.GONE

            // Brand Icon and Summary
            if (preset != null) {
                binding.ivBrandLogo.setImageResource(preset.iconRes)
                binding.tvModelsSummary.text = preset.subtitle
            } else {
                binding.ivBrandLogo.setImageResource(R.drawable.ic_tune)
                binding.tvModelsSummary.text = item.baseUrl
            }

            // Key Status
            val hasKey = item.encryptedApiKey.isNotBlank()
            if (hasKey) {
                binding.badgeKeyStatus.text = "● Key Configured"
                binding.badgeKeyStatus.setBackgroundResource(R.drawable.bg_pill_emerald)
                binding.badgeKeyStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_green_text))

                val decrypted = KeystoreSecretManager.decrypt(item.encryptedApiKey)
                val masked = if (decrypted.length > 8) {
                    decrypted.take(4) + "••••••" + decrypted.takeLast(3)
                } else {
                    "••••••••"
                }
                binding.tvMaskedKey.text = masked
                binding.tvMaskedKey.visibility = View.VISIBLE
                binding.btnConfigureKey.text = "Edit Key"
                binding.btnConfigureKey.setIconResource(R.drawable.ic_key)
            } else {
                binding.badgeKeyStatus.text = "○ Needs API Key"
                binding.badgeKeyStatus.setBackgroundResource(R.drawable.bg_pill_amber)
                binding.badgeKeyStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.status_amber_text))
                binding.tvMaskedKey.visibility = View.GONE
                binding.btnConfigureKey.text = "Enter API Key"
                binding.btnConfigureKey.setIconResource(R.drawable.ic_key)
            }

            // Latency Ping
            val latency = latencyMap[item.id]
            if (latency != null && latency > 0) {
                binding.badgeLatency.text = "⚡ ${latency}ms"
                binding.badgeLatency.visibility = View.VISIBLE
            } else {
                binding.badgeLatency.visibility = View.GONE
            }

            // Switch toggle
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = item.isEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(item, isChecked)
            }

            // Delete is only allowed for custom non-official providers
            binding.btnDelete.visibility = if (isOfficial) View.GONE else View.VISIBLE

            // Action clicks
            binding.btnConfigureKey.setOnClickListener { onConfigureKey(item) }
            binding.cardProvider.setOnClickListener { onConfigureKey(item) }
            binding.btnTest.setOnClickListener { onTest(item) }
            binding.btnFetchModels.setOnClickListener { onFetchModels(item) }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
