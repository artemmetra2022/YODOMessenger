package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.YodoUser
import kotlinx.coroutines.flow.Flow

sealed class CreateChatResult {
    data class Success(val chatId: String) : CreateChatResult()
    data class Error(val message: String) : CreateChatResult()
}

data class ChatInfo(
    val title: String,
    val otherUserId: String?, // null для групповых чатов
    val type: String
)

sealed class ChatListResult {
    data class Success(val chats: List<ChatPreview>) : ChatListResult()
    data class Error(val message: String) : ChatListResult()
}

data class GroupInfo(
    val title: String,
    val members: List<YodoUser>,
    val createdBy: String?
)

interface ChatRepository {
    /** Реалтайм-поток списка чатов текущего пользователя, отсортированный: закреплённые сверху, затем по времени. */
    fun observeChatList(): Flow<ChatListResult>

    /** Возвращает существующий приватный чат с этим пользователем или создаёт новый. */
    suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult

    /** Создаёт новый групповой чат с указанным названием и списком участников (создатель добавляется автоматически). */
    suspend fun createGroupChat(title: String, memberIds: List<String>): CreateChatResult

    /** Название чата (персонализированное для текущего пользователя) и id собеседника для приватных чатов. */
    suspend fun getChatInfo(chatId: String): ChatInfo?

    /** Полная информация о группе — название и профили участников (для экрана "Информация о группе"). */
    suspend fun getGroupInfo(chatId: String): GroupInfo?

    suspend fun leaveGroup(chatId: String)

    suspend fun togglePinChat(chatId: String)

    suspend fun toggleMuteChat(chatId: String)
}
