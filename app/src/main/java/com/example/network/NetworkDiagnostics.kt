package com.example.network

import java.util.concurrent.atomic.AtomicReference

data class DiagnosticRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val providerName: String,
    val adapterType: String,
    val requestMethod: String,
    val endpointUrl: String,
    val sanitizedHeaders: Map<String, String>,
    val statusCode: Int?,
    val latencyMs: Long,
    val isStreaming: Boolean,
    val errorDetails: String? = null
)

object NetworkDiagnostics {
    private val lastRecordRef = AtomicReference<DiagnosticRecord?>(null)

    fun record(
        providerName: String,
        adapterType: String,
        requestMethod: String,
        endpointUrl: String,
        headers: Map<String, String>,
        statusCode: Int?,
        latencyMs: Long,
        isStreaming: Boolean,
        errorDetails: String? = null
    ) {
        // Sanitize headers to NEVER leak authorization or api-key
        val sanitized = headers.mapValues { (key, value) ->
            if (key.contains("auth", ignoreCase = true) ||
                key.contains("key", ignoreCase = true) ||
                key.contains("token", ignoreCase = true) ||
                key.contains("secret", ignoreCase = true)
            ) {
                "••••••••[PROTECTED]"
            } else {
                value
            }
        }

        lastRecordRef.set(
            DiagnosticRecord(
                providerName = providerName,
                adapterType = adapterType,
                requestMethod = requestMethod,
                endpointUrl = endpointUrl,
                sanitizedHeaders = sanitized,
                statusCode = statusCode,
                latencyMs = latencyMs,
                isStreaming = isStreaming,
                errorDetails = errorDetails
            )
        )
    }

    fun getLastRecord(): DiagnosticRecord? = lastRecordRef.get()
}
