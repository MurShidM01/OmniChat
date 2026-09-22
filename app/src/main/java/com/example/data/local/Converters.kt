package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.ApiCompatibilityType
import com.example.data.model.AuthType
import com.example.data.model.MessageRole

class Converters {
    @TypeConverter
    fun fromCompatibilityType(value: ApiCompatibilityType): String = value.name

    @TypeConverter
    fun toCompatibilityType(value: String): ApiCompatibilityType =
        try { ApiCompatibilityType.valueOf(value) } catch (e: Exception) { ApiCompatibilityType.OPENAI_CHAT_COMPLETIONS }

    @TypeConverter
    fun fromAuthType(value: AuthType): String = value.name

    @TypeConverter
    fun toAuthType(value: String): AuthType =
        try { AuthType.valueOf(value) } catch (e: Exception) { AuthType.BEARER_TOKEN }

    @TypeConverter
    fun fromMessageRole(value: MessageRole): String = value.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole =
        try { MessageRole.valueOf(value) } catch (e: Exception) { MessageRole.USER }
}
