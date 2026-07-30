package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.Comment
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.ScheduledMessage
import kotlinx.coroutines.flow.Flow

sealed class SendMessageResult {
    data class Success(val messageId: String? = null) : SendMessageResult()
    data class Error(val message: String) : SendMessageResult()
}

data class ReplyContext(
    val messageId: String,
    val senderName: String,
    val text: String
)

interface MessageRepository {

    fun observeMessages(chatId: String): Flow<List<Message>>
    // НОВОЕ (переработка каналов): одно сообщение (для превью поста в экране комментариев).
    fun observeMessage(chatId: String, messageId: String): Flow<Message?>
    // НОВОЕ (per-message исчезающие сообщения, как в Telegram): hasTtlOverride = true означает,
    // что пользователь явно выбрал таймер для ЭТОГО сообщения (иконка часов у поля ввода) —
    // в этом случае ttlOverrideSeconds используется как есть (в т.ч. null = явно выключено),
    // ИГНОРИРУЯ TTL по умолчанию для чата. hasTtlOverride = false (по умолчанию) — берётся TTL
    // чата (disappearingTtlSeconds из документа chats/{chatId}), как и раньше.
    suspend fun sendMessage(
        chatId: String, text: String, replyTo: ReplyContext? = null,
        hasTtlOverride: Boolean = false, ttlOverrideSeconds: Long? = null
    ): SendMessageResult
    suspend fun sendImageMessage(chatId: String, imageBase64: String, caption: String = ""): SendMessageResult
    suspend fun sendVoiceMessage(chatId: String, voiceBase64: String, durationMs: Long): SendMessageResult
    suspend fun sendFileMessage(
        chatId: String, fileBase64: String, fileName: String, mimeType: String, sizeBytes: Long
    ): SendMessageResult
    suspend fun sendLocationMessage(chatId: String, lat: Double, lng: Double): SendMessageResult
    suspend fun scheduleMessage(
        chatId: String, text: String, scheduledFor: Long,
        imageBase64: String? = null, replyTo: ReplyContext? = null
    ): SendMessageResult
    fun observeScheduledMessages(chatId: String): Flow<List<ScheduledMessage>>
    suspend fun cancelScheduledMessage(chatId: String, scheduledMessageId: String)
    suspend fun editScheduledMessage(chatId: String, scheduledMessageId: String, newText: String, newScheduledFor: Long)
    suspend fun publishDueScheduledMessages(chatId: String)
    suspend fun forwardMessage(targetChatId: String, originalMessage: Message, fromSenderName: String, fromSenderId: String): SendMessageResult
    suspend fun editMessage(chatId: String, messageId: String, newText: String): SendMessageResult
    suspend fun deleteMessage(chatId: String, messageId: String): SendMessageResult
    suspend fun markChatAsRead(chatId: String)
    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String)
    suspend fun togglePinMessage(chatId: String, messageId: String): SendMessageResult
    fun observePinnedMessages(chatId: String): Flow<List<Message>>
    suspend fun toggleBookmark(messageId: String, chatId: String)
    fun observeBookmarkedMessages(): Flow<List<Message>>
    suspend fun exportChatHistory(chatId: String): String
    suspend fun deleteExpiredMessages(chatId: String)

    // НОВОЕ (переработка каналов): комментарии к постам.
    fun observeComments(chatId: String, messageId: String): Flow<List<Comment>>
    suspend fun addComment(chatId: String, messageId: String, text: String): SendMessageResult
    suspend fun deleteComment(chatId: String, messageId: String, commentId: String): SendMessageResult
    /** Число постов в канале (для профиля канала). */
    suspend fun countMessages(chatId: String): Int
    /** Последние посты канала (для превью в профиле канала). */
    suspend fun getRecentMessages(chatId: String, limit: Int): List<Message>
}