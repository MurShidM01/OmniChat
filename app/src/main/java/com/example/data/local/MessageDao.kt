package com.example.data.local

import androidx.room.*
import com.example.data.model.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesList(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET content = :content, isStreaming = :isStreaming, reasoningContent = :reasoningContent, latencyMs = :latencyMs, tokenCount = :tokenCount WHERE id = :id")
    suspend fun updateStreamChunk(id: String, content: String, reasoningContent: String?, isStreaming: Boolean, latencyMs: Long, tokenCount: Int)

    @Query("UPDATE messages SET isError = 1, errorMessage = :errorMessage, isStreaming = 0 WHERE id = :id")
    suspend fun markError(id: String, errorMessage: String)

    @Query("UPDATE messages SET isThinkingExpanded = :expanded WHERE id = :id")
    suspend fun setThinkingExpanded(id: String, expanded: Boolean)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)
}
