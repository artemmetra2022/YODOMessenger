package app.yodo.messenger.domain.model

/**
 * Папка чатов — пользовательская группировка чатов (как в Telegram).
 * Хранится локально в DataStore, не в Firestore (папки — это локальная
 * организация, а не серверная сущность).
 */
data class ChatFolder(
    val id: String,
    val name: String,
    val chatIds: List<String> = emptyList(),
    val isDefault: Boolean = false,
    val order: Int = 0
)