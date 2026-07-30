package app.yodo.messenger.domain.repository

import android.graphics.Bitmap
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.YodoUser
import kotlinx.coroutines.flow.Flow

sealed class CreateChatResult {
    data class Success(val chatId: String) : CreateChatResult()
    data class Error(val message: String) : CreateChatResult()
}

// НОВОЕ (переработка каналов): результат обновления данных канала.
sealed class ChannelUpdateResult {
    data object Success : ChannelUpdateResult()
    data class Error(val message: String) : ChannelUpdateResult()
}

// НОВОЕ (переработка каналов): элемент выдачи поиска по каналам.
data class ChannelSearchItem(
    val chatId: String,
    val title: String,
    val description: String,
    val avatarBase64: String?,
    val subscriberCount: Int,
    val isVerified: Boolean,
    val isSubscribed: Boolean
)

data class ChatInfo(
    val title: String,
    val otherUserId: String?,
    val type: String,
    val avatarUrl: String? = null,
    val avatarBase64: String? = null,
    val otherUserPhotoUrl: String? = null,
    val otherUserAvatarBase64: String? = null,
    val isVerified: Boolean = false,
    val channelOwnerId: String? = null,
    val channelAdminIds: List<String> = emptyList(),
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = false,
    // НОВОЕ: дата создания (для профиля канала).
    val createdAt: Long = 0L
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

    companion object {
        const val OFFICIAL_CHANNEL_ID = "yodo_official_channel"
        val ADMIN_EMAILS = listOf(
            "artemmetra2022spb@gmail.com",
            "artemmelnik2@yandex.ru"
        )
    }

    fun observeChatList(): Flow<ChatListResult>
    suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult
    suspend fun createGroupChat(
    title: String,
    memberIds: List<String>,
    description: String = "",
    avatarBitmap: android.graphics.Bitmap? = null
): CreateChatResult

    // НОВОЕ: создание канала с необязательной аватаркой (Bitmap после кропа).
    suspend fun createChannel(title: String, description: String, avatarBitmap: Bitmap? = null): CreateChatResult

    suspend fun subscribeToChannel(chatId: String)
    suspend fun unsubscribeFromChannel(chatId: String)
    // НОВОЕ: полное удаление канала владельцем — удаляет документ чата, все сообщения
    // и отписывает всех подписчиков. Разрешено только создателю канала (ownerId).
    suspend fun deleteChannel(chatId: String): ChannelUpdateResult
    suspend fun addChannelAdmin(chatId: String, userId: String)
    suspend fun removeChannelAdmin(chatId: String, userId: String)
    // НОВОЕ: приглашение пользователей в канал (подписка "за них", инициированная владельцем/админом).
    suspend fun inviteUsersToChannel(chatId: String, userIds: List<String>)

    // НОВОЕ (переработка каналов):
    /** Поиск каналов по префиксу названия (без учёта регистра). */
    suspend fun searchChannels(query: String): List<ChannelSearchItem>
    /** Полный профиль канала (экран ChannelProfileScreen). */
    suspend fun getChannelProfile(chatId: String): ChannelProfile?
    /** Обновление названия и описания канала (владелец/админ). */
    suspend fun updateChannelInfo(chatId: String, title: String, description: String): ChannelUpdateResult
    /** Загрузка аватарки канала — сжатый Base64 прямо в документ чата. */
    suspend fun uploadChannelAvatar(chatId: String, bitmap: Bitmap): ChannelUpdateResult

    suspend fun getChatInfo(chatId: String): ChatInfo?
    suspend fun getGroupInfo(chatId: String): GroupInfo?
    suspend fun leaveGroup(chatId: String)
    suspend fun togglePinChat(chatId: String)
    suspend fun toggleMuteChat(chatId: String)
    // НОВОЕ (архивация чатов): переключить архивный статус чата для текущего пользователя.
    suspend fun toggleArchiveChat(chatId: String)
    suspend fun clearChatHistory(chatId: String)
    suspend fun deleteChat(chatId: String)
    suspend fun getOtherUserAvatar(chatId: String): Pair<String?, String?>?
    suspend fun getOrCreateSavedChat(): String
    fun observeDisappearingTtl(chatId: String): Flow<Long?>
    suspend fun setDisappearingTtl(chatId: String, ttlSeconds: Long?)
}