package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.Message
import kotlinx.coroutines.flow.Flow

sealed class SendMessageResult {
    data object Success : SendMessageResult()
    data class Error(val message: String) : SendMessageResult()
}

data class ReplyContext(
    val messageId: String,
    val senderName: String,
    val text: String
)

interface MessageRepository {
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun sendMessage(chatId: String, text: String, replyTo: ReplyContext? = null): SendMessageResult
    suspend fun sendImageMessage(chatId: String, imageBase64: String, caption: String = ""): SendMessageResult
    suspend fun forwardMessage(targetChatId: String, originalMessage: Message, fromSenderName: String): SendMessageResult
    suspend fun editMessage(chatId: String, messageId: String, newText: String): SendMessageResult
    suspend fun deleteMessage(chatId: String, messageId: String): SendMessageResult
    suspend fun markChatAsRead(chatId: String)
    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String)
    suspend fun togglePinMessage(chatId: String, messageId: String): SendMessageResult
    fun observePinnedMessages(chatId: String): Flow<List<Message>>
    suspend fun toggleBookmark(messageId: String, chatId: String)
    fun observeBookmarkedMessages(): Flow<List<Message>>
    suspend fun exportChatHistory(chatId: String): String
}
