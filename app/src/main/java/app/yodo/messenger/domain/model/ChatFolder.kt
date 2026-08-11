package app.yodo.messenger.domain.model

import kotlinx.serialization.Serializable

/**
 * Папка чатов — пользовательская группировка чатов (как в Telegram).
 * Хранится локально в DataStore, не в Firestore (папки — это локальная
 * организация, а не серверная сущность).
 */
@Serializable
data class ChatFolder(
    val id: String,
    val name: String,
    val chatIds: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val order: Int = 0
)