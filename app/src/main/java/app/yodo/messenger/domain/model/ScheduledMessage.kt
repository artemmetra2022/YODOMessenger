package app.yodo.messenger.domain.model

/**
 * НОВОЕ: отложенное сообщение — текст (и/или фото) для этого чата, который должен
 * быть автоматически опубликован как обычное сообщение в момент scheduledFor.
 * Хранится отдельно от обычных сообщений (chats/{chatId}/scheduledMessages),
 * поэтому не отображается в общей ленте переписки до момента отправки.
 */
data class ScheduledMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageBase64: String? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val scheduledFor: Long = 0L,
    val createdAt: Long = 0L
)
