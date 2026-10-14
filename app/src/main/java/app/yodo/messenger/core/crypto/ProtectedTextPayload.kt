package app.yodo.messenger.core.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Version 2 encrypts text and reply snippets in the same envelope. Metadata is not hidden. */
data class ProtectedTextPayload(val text: String, val replyText: String? = null, val replySenderName: String? = null) {
    fun encode(): String = buildJsonObject {
        put("text", JsonPrimitive(text))
        replyText?.let { put("replyText", JsonPrimitive(it)) }
        replySenderName?.let { put("replySenderName", JsonPrimitive(it)) }
    }.toString()

    companion object {
        fun decode(raw: String, version: Int): ProtectedTextPayload {
            if (version == 1) return ProtectedTextPayload(raw)
            if (version != 2) throw SecureMessagingException("Обновите приложение: версия шифрования не поддерживается")
            val json = Json.parseToJsonElement(raw).jsonObject
            return ProtectedTextPayload(
                json["text"]?.jsonPrimitive?.content ?: error("Missing encrypted text"),
                json["replyText"]?.jsonPrimitive?.content,
                json["replySenderName"]?.jsonPrimitive?.content
            )
        }
    }
}
