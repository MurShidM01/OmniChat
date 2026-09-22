package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val base64Data: String? = null,
    val uriString: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("mimeType", mimeType)
        put("base64Data", base64Data ?: "")
        put("uriString", uriString ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): Attachment = Attachment(
            id = json.optString("id"),
            name = json.optString("name"),
            mimeType = json.optString("mimeType"),
            base64Data = json.optString("base64Data").takeIf { it.isNotEmpty() },
            uriString = json.optString("uriString").takeIf { it.isNotEmpty() }
        )

        fun parseList(jsonString: String): List<Attachment> {
            if (jsonString.isBlank() || jsonString == "[]") return emptyList()
            return try {
                val array = JSONArray(jsonString)
                (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun serializeList(attachments: List<Attachment>): String {
            val array = JSONArray()
            attachments.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
