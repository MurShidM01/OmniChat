package com.example.data.presets

import com.example.R
import com.example.data.model.ApiCompatibilityType
import com.example.data.model.AuthType
import com.example.data.model.ModelEntity
import com.example.data.model.ProviderEntity

data class ProviderPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val apiKeyPlaceholder: String,
    val apiKeyDocsUrl: String,
    val template: ProviderEntity,
    val defaultModels: List<ModelEntity>
) {
    companion object {
        const val PROVIDER_ID_OPENAI = "provider_openai"
        const val PROVIDER_ID_ANTHROPIC = "provider_anthropic"
        const val PROVIDER_ID_DEEPSEEK = "provider_deepseek"
        const val PROVIDER_ID_GEMINI = "provider_gemini"

        val OFFICIAL_IDS = setOf(
            PROVIDER_ID_OPENAI,
            PROVIDER_ID_ANTHROPIC,
            PROVIDER_ID_DEEPSEEK,
            PROVIDER_ID_GEMINI
        )

        fun getPresets(): List<ProviderPreset> = listOf(
            ProviderPreset(
                id = PROVIDER_ID_OPENAI,
                title = "OpenAI",
                subtitle = "GPT-4o, o3-mini, o1, GPT-4o Mini",
                iconRes = R.drawable.ic_brand_openai,
                apiKeyPlaceholder = "sk-proj-...",
                apiKeyDocsUrl = "https://platform.openai.com/api-keys",
                template = ProviderEntity(
                    id = PROVIDER_ID_OPENAI,
                    name = "OpenAI",
                    baseUrl = "https://api.openai.com/v1",
                    compatibilityType = ApiCompatibilityType.OPENAI_CHAT_COMPLETIONS,
                    chatEndpoint = "/chat/completions",
                    modelsEndpoint = "/models",
                    authType = AuthType.BEARER_TOKEN,
                    supportsStreaming = true,
                    isEnabled = true,
                    sortOrder = 0
                ),
                defaultModels = listOf(
                    ModelEntity(
                        id = "$PROVIDER_ID_OPENAI::gpt-4o",
                        modelId = "gpt-4o",
                        providerId = PROVIDER_ID_OPENAI,
                        displayName = "GPT-4o",
                        description = "Flagship multimodal intelligence & high-speed reasoning",
                        contextWindow = 128000,
                        supportsVision = true,
                        supportsReasoning = false,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = true
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_OPENAI::gpt-4o-mini",
                        modelId = "gpt-4o-mini",
                        providerId = PROVIDER_ID_OPENAI,
                        displayName = "GPT-4o Mini",
                        description = "Ultra-fast, lightweight multimodal assistant",
                        contextWindow = 128000,
                        supportsVision = true,
                        supportsReasoning = false,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = false
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_OPENAI::o3-mini",
                        modelId = "o3-mini",
                        providerId = PROVIDER_ID_OPENAI,
                        displayName = "o3-mini",
                        description = "Reasoning model designed for coding, math, and STEM",
                        contextWindow = 200000,
                        supportsVision = false,
                        supportsReasoning = true,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = true
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_OPENAI::o1",
                        modelId = "o1",
                        providerId = PROVIDER_ID_OPENAI,
                        displayName = "o1",
                        description = "Full deep-thought reasoning for complex problem solving",
                        contextWindow = 200000,
                        supportsVision = true,
                        supportsReasoning = true,
                        supportsToolCalling = false,
                        supportsStreaming = true,
                        isFavorite = false
                    )
                )
            ),
            ProviderPreset(
                id = PROVIDER_ID_ANTHROPIC,
                title = "Anthropic",
                subtitle = "Claude 3.7 Sonnet, Claude 3.5 Haiku",
                iconRes = R.drawable.ic_brand_anthropic,
                apiKeyPlaceholder = "sk-ant-api03-...",
                apiKeyDocsUrl = "https://console.anthropic.com/settings/keys",
                template = ProviderEntity(
                    id = PROVIDER_ID_ANTHROPIC,
                    name = "Anthropic",
                    baseUrl = "https://api.anthropic.com/v1",
                    compatibilityType = ApiCompatibilityType.ANTHROPIC_MESSAGES,
                    chatEndpoint = "/messages",
                    modelsEndpoint = "/models",
                    authType = AuthType.API_KEY_HEADER,
                    customAuthHeader = "x-api-key",
                    customHeadersJson = "{\"anthropic-version\": \"2023-06-01\"}",
                    supportsStreaming = true,
                    isEnabled = true,
                    sortOrder = 1
                ),
                defaultModels = listOf(
                    ModelEntity(
                        id = "$PROVIDER_ID_ANTHROPIC::claude-3-7-sonnet-20250219",
                        modelId = "claude-3-7-sonnet-20250219",
                        providerId = PROVIDER_ID_ANTHROPIC,
                        displayName = "Claude 3.7 Sonnet",
                        description = "Hybrid reasoning & intelligence for code and analysis",
                        contextWindow = 200000,
                        supportsVision = true,
                        supportsReasoning = true,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = true
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_ANTHROPIC::claude-3-5-sonnet-20241022",
                        modelId = "claude-3-5-sonnet-20241022",
                        providerId = PROVIDER_ID_ANTHROPIC,
                        displayName = "Claude 3.5 Sonnet",
                        description = "High intelligence, fast and versatile coding model",
                        contextWindow = 200000,
                        supportsVision = true,
                        supportsReasoning = false,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = false
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_ANTHROPIC::claude-3-5-haiku-20241022",
                        modelId = "claude-3-5-haiku-20241022",
                        providerId = PROVIDER_ID_ANTHROPIC,
                        displayName = "Claude 3.5 Haiku",
                        description = "Ultra-fast response latency and agile reasoning",
                        contextWindow = 200000,
                        supportsVision = false,
                        supportsReasoning = false,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = false
                    )
                )
            ),
            ProviderPreset(
                id = PROVIDER_ID_DEEPSEEK,
                title = "DeepSeek",
                subtitle = "DeepSeek-V3, DeepSeek-R1 (Reasoning)",
                iconRes = R.drawable.ic_brand_deepseek,
                apiKeyPlaceholder = "sk-...",
                apiKeyDocsUrl = "https://platform.deepseek.com/api_keys",
                template = ProviderEntity(
                    id = PROVIDER_ID_DEEPSEEK,
                    name = "DeepSeek",
                    baseUrl = "https://api.deepseek.com",
                    compatibilityType = ApiCompatibilityType.OPENAI_CHAT_COMPLETIONS,
                    chatEndpoint = "/chat/completions",
                    modelsEndpoint = "/models",
                    authType = AuthType.BEARER_TOKEN,
                    supportsStreaming = true,
                    isEnabled = true,
                    sortOrder = 2
                ),
                defaultModels = listOf(
                    ModelEntity(
                        id = "$PROVIDER_ID_DEEPSEEK::deepseek-chat",
                        modelId = "deepseek-chat",
                        providerId = PROVIDER_ID_DEEPSEEK,
                        displayName = "DeepSeek-V3",
                        description = "General conversation, high intelligence & coding power",
                        contextWindow = 64000,
                        supportsVision = false,
                        supportsReasoning = false,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = true
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_DEEPSEEK::deepseek-reasoner",
                        modelId = "deepseek-reasoner",
                        providerId = PROVIDER_ID_DEEPSEEK,
                        displayName = "DeepSeek-R1 (Reasoning)",
                        description = "Deep chain-of-thought mathematical reasoning model",
                        contextWindow = 64000,
                        supportsVision = false,
                        supportsReasoning = true,
                        supportsToolCalling = false,
                        supportsStreaming = true,
                        isFavorite = true
                    )
                )
            ),
            ProviderPreset(
                id = PROVIDER_ID_GEMINI,
                title = "Google Gemini",
                subtitle = "Gemini 2.5 Flash, Gemini 2.5 Pro",
                iconRes = R.drawable.ic_brand_gemini,
                apiKeyPlaceholder = "AIzaSy...",
                apiKeyDocsUrl = "https://aistudio.google.com/app/apikey",
                template = ProviderEntity(
                    id = PROVIDER_ID_GEMINI,
                    name = "Google Gemini",
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                    compatibilityType = ApiCompatibilityType.OPENAI_CHAT_COMPLETIONS,
                    chatEndpoint = "/chat/completions",
                    modelsEndpoint = "/models",
                    authType = AuthType.BEARER_TOKEN,
                    supportsStreaming = true,
                    isEnabled = true,
                    sortOrder = 3
                ),
                defaultModels = listOf(
                    ModelEntity(
                        id = "$PROVIDER_ID_GEMINI::gemini-2.5-flash",
                        modelId = "gemini-2.5-flash",
                        providerId = PROVIDER_ID_GEMINI,
                        displayName = "Gemini 2.5 Flash",
                        description = "Next-generation fast multimodal model with 1M context",
                        contextWindow = 1000000,
                        supportsVision = true,
                        supportsReasoning = true,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = true
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_GEMINI::gemini-2.5-pro",
                        modelId = "gemini-2.5-pro",
                        providerId = PROVIDER_ID_GEMINI,
                        displayName = "Gemini 2.5 Pro",
                        description = "Google's flagship reasoning, coding & analysis model",
                        contextWindow = 2000000,
                        supportsVision = true,
                        supportsReasoning = true,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = false
                    ),
                    ModelEntity(
                        id = "$PROVIDER_ID_GEMINI::gemini-2.0-flash",
                        modelId = "gemini-2.0-flash",
                        providerId = PROVIDER_ID_GEMINI,
                        displayName = "Gemini 2.0 Flash",
                        description = "High-speed multimodal agentic intelligence",
                        contextWindow = 1000000,
                        supportsVision = true,
                        supportsReasoning = false,
                        supportsToolCalling = true,
                        supportsStreaming = true,
                        isFavorite = false
                    )
                )
            )
        )

        fun findPreset(providerId: String): ProviderPreset? {
            return getPresets().find { it.id == providerId || it.title.equals(providerId, ignoreCase = true) }
        }
    }
}
