package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val attachmentsJson: String = "[]",
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val latencyMs: Long = 0L,
    val isThinkingExpanded: Boolean = false,
    val modelUsed: String? = null
)
