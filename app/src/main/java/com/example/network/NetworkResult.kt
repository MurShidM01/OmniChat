package com.example.network

sealed interface NetworkResult<out T> {
    data class Success<out T>(val data: T, val latencyMs: Long) : NetworkResult<T>
    data class Error(val message: String, val statusCode: Int? = null, val cause: Throwable? = null) : NetworkResult<Nothing>
}

data class StreamChunk(
    val deltaContent: String = "",
    val deltaReasoning: String? = null,
    val isFinished: Boolean = false,
    val finishReason: String? = null,
    val totalTokens: Int = 0
)

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val message: String,
    val latencyMs: Long,
    val discoveredModelsCount: Int = 0
)
