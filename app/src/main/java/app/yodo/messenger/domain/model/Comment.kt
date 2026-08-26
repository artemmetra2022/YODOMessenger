package app.yodo.messenger.domain.model

/**
 * НОВОЕ (переработка каналов): комментарий к посту канала.
 * Хранится в chats/{chatId}/messages/{messageId}/comments.
 */
data class Comment(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long
)