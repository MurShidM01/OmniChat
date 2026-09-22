package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String, // composite or unique key, e.g. "$providerId::$modelId"
    val modelId: String,
    val providerId: String,
    val displayName: String,
    val description: String = "",
    val contextWindow: Int = 128000,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsToolCalling: Boolean = false,
    val supportsStreaming: Boolean = true,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
