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
    val otherUserId: String?,
    val type: String,
    val avatarUrl: String? = null,
    val avatarBase64: String? = null,
    val otherUserPhotoUrl: String? = null,
    val otherUserAvatarBase64: String? = null
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
    fun observeChatList(): Flow<ChatListResult>
    suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult
    suspend fun createGroupChat(title: String, memberIds: List<String>): CreateChatResult
    suspend fun getChatInfo(chatId: String): ChatInfo?
    suspend fun getGroupInfo(chatId: String): GroupInfo?
    suspend fun leaveGroup(chatId: String)
    suspend fun togglePinChat(chatId: String)
    suspend fun toggleMuteChat(chatId: String)
    suspend fun clearChatHistory(chatId: String)
    suspend fun deleteChat(chatId: String)
    suspend fun getOtherUserAvatar(chatId: String): Pair<String?, String?>?
}
