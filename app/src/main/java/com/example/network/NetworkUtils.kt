package com.example.network

import com.example.data.model.AuthType
import com.example.data.model.ProviderEntity
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkUtils {

    fun createClient(timeoutSeconds: Int): OkHttpClient {
        val timeout = if (timeoutSeconds > 0) timeoutSeconds.toLong() else 60L
        return OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun buildFullUrl(baseUrl: String, endpoint: String): String {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        val cleanEndpoint = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
        return cleanBase + cleanEndpoint
    }

    fun applyAuthAndHeaders(
        builder: Request.Builder,
        provider: ProviderEntity,
        decryptedApiKey: String,
        url: String
    ): String {
        var finalUrl = url

        // Authentication
        if (decryptedApiKey.isNotBlank()) {
            when (provider.authType) {
                AuthType.BEARER_TOKEN -> {
                    builder.header("Authorization", "Bearer $decryptedApiKey")
                }
                AuthType.API_KEY_HEADER -> {
                    val headerName = if (provider.customAuthHeader.isNotBlank()) provider.customAuthHeader else "x-api-key"
                    builder.header(headerName, decryptedApiKey)
                }
                AuthType.QUERY_PARAM -> {
                    val httpUrl = finalUrl.toHttpUrlOrNull()
                    if (httpUrl != null) {
                        finalUrl = httpUrl.newBuilder()
                            .addQueryParameter("key", decryptedApiKey)
                            .build()
                            .toString()
                    }
                }
                AuthType.CUSTOM_HEADER -> {
                    val headerName = if (provider.customAuthHeader.isNotBlank()) provider.customAuthHeader else "Authorization"
                    builder.header(headerName, decryptedApiKey)
                }
            }
        }

        // Custom headers from JSON
        if (provider.customHeadersJson.isNotBlank()) {
            try {
                val json = JSONObject(provider.customHeadersJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    builder.header(key, json.optString(key))
                }
            } catch (e: Exception) {
                // Ignore malformed custom headers JSON
            }
        }

        return finalUrl
    }

    fun extractHeadersMap(headers: Headers): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until headers.size) {
            map[headers.name(i)] = headers.value(i)
        }
        return map
    }
}
