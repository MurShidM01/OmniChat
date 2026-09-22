package com.example.data.model

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val latencyMs: Long = 0L,
    val isThinkingExpanded: Boolean = false,
    val modelUsed: String? = null
) {
    companion object {
        fun fromEntity(entity: MessageEntity): ChatMessage = ChatMessage(
            id = entity.id,
            conversationId = entity.conversationId,
            role = entity.role,
            content = entity.content,
            reasoningContent = entity.reasoningContent,
            attachments = Attachment.parseList(entity.attachmentsJson),
            isStreaming = entity.isStreaming,
            isError = entity.isError,
            errorMessage = entity.errorMessage,
            timestamp = entity.timestamp,
            tokenCount = entity.tokenCount,
            latencyMs = entity.latencyMs,
            isThinkingExpanded = entity.isThinkingExpanded,
            modelUsed = entity.modelUsed
        )
    }

    fun toEntity(): MessageEntity = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        reasoningContent = reasoningContent,
        attachmentsJson = Attachment.serializeList(attachments),
        isStreaming = isStreaming,
        isError = isError,
        errorMessage = errorMessage,
        timestamp = timestamp,
        tokenCount = tokenCount,
        latencyMs = latencyMs,
        isThinkingExpanded = isThinkingExpanded,
        modelUsed = modelUsed
    )
}
