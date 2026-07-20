package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

sealed class SendMessageResult {
    data object Success : SendMessageResult()
    data class Error(val message: String) : SendMessageResult()
}

/** Данные о сообщении, на которое отвечают — передаются при отправке нового сообщения. */
data class ReplyContext(
    val messageId: String,
    val senderName: String,
    val text: String
)

interface MessageRepository {
    /** Реалтайм-поток сообщений чата, по возрастанию времени (старые сверху). */
    fun observeMessages(chatId: String): Flow<List<Message>>

    suspend fun sendMessage(chatId: String, text: String, replyTo: ReplyContext? = null): SendMessageResult

    /** Отправка фото (сжатого в Base64 — тот же приём, что у аватарок, без платного Storage). */
    suspend fun sendImageMessage(chatId: String, imageBase64: String, caption: String = ""): SendMessageResult

    /** Пересылка текста существующего сообщения в другой чат. */
    suspend fun forwardMessage(targetChatId: String, originalMessage: Message, fromSenderName: String): SendMessageResult

    suspend fun editMessage(chatId: String, messageId: String, newText: String): SendMessageResult

    suspend fun deleteMessage(chatId: String, messageId: String): SendMessageResult

    /** Сбросить счётчик непрочитанных для текущего пользователя в этом чате. */
    suspend fun markChatAsRead(chatId: String)

    /** Переключить реакцию текущего пользователя на сообщении (добавить, если её не было, убрать — если была). */
    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String)
}
