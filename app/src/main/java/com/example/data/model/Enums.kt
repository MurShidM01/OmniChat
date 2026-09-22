package com.example.data.model

enum class ApiCompatibilityType(val displayName: String) {
    OPENAI_CHAT_COMPLETIONS("OpenAI Chat (/chat/completions)"),
    OPENAI_RESPONSES("OpenAI Responses (/responses)"),
    ANTHROPIC_MESSAGES("Anthropic Messages (/messages)"),
    CUSTOM_GENERIC("Custom Generic")
}

enum class AuthType(val displayName: String) {
    BEARER_TOKEN("Bearer Token (Authorization: Bearer <key>)"),
    API_KEY_HEADER("API Key Header (e.g. x-api-key)"),
    QUERY_PARAM("Query Parameter (?key=<key>)"),
    CUSTOM_HEADER("Custom Header")
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
