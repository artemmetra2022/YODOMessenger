package app.yodo.messenger.domain.repository

import android.graphics.Bitmap
import app.yodo.messenger.domain.model.AdminLogEntry
import app.yodo.messenger.domain.model.AdminLogFilter
import app.yodo.messenger.domain.model.AssignedRole
import app.yodo.messenger.domain.model.BuiltInRole
import app.yodo.messenger.domain.model.ChannelAccessMode
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.ChannelRestrictions
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.JoinRequest
import app.yodo.messenger.domain.model.CustomRole
import app.yodo.messenger.domain.model.ForumTopic
import app.yodo.messenger.domain.model.MemberPermissions
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
    val isSubscribed: Boolean,
    // НОВОЕ (F5): категория канала для выдачи поиска.
    val category: String? = null,
    // НОВОЕ (режимы доступа каналов): режим доступа (для отображения «по заявке» и т.п.).
    val accessMode: ChannelAccessMode = ChannelAccessMode.OPEN,
    // НОВОЕ (модерируемые каналы): пользователь уже подал заявку на вступление.
    val hasPendingJoinRequest: Boolean = false
)

// НОВОЕ (каталог/рекомендации каналов): подборка каналов по категориям для экрана
// «Каталог каналов» — открывается из списка чатов, без необходимости вводить запрос.
data class ChannelCategorySection(
    val category: String,
    val channels: List<ChannelSearchItem>
)

data class ChannelDirectory(
    // Каналы с наибольшим числом подписчиков (топ-подборка сверху экрана).
    val trending: List<ChannelSearchItem>,
    // Остальные каналы, сгруппированные по категории (без категории — не включаются).
    val byCategory: List<ChannelCategorySection>
)

// НОВОЕ (статистика для владельца): точка графика роста/убыли подписчиков за день.
data class ChannelSubscriberPoint(
    val dateLabel: String,
    val delta: Int
)

// НОВОЕ (статистика для владельца): краткая карточка лучших постов канала (по охвату/комментариям).
data class ChannelTopPost(
    val messageId: String,
    val previewText: String,
    val timestamp: Long,
    val viewCount: Int,
    val commentsCount: Int
)

// НОВОЕ (статистика для владельца): расширенная аналитика канала — в отличие от
// общей ChatStatsScreen (любой чат), здесь акцент именно на метриках канала как
// медиаресурса (рост аудитории, охват и вовлечённость постов).
data class ChannelStats(
    val subscriberCount: Int,
    val postsCount: Int,
    val totalViews: Int,
    val totalComments: Int,
    // Среднее число просмотров на пост — ключевая метрика «охвата».
    val avgViewsPerPost: Int,
    // Самые активные за последние 30 дней (чистый прирост за период).
    val subscribersGained30d: Int,
    val subscribersLost30d: Int,
    val subscriberHistory: List<ChannelSubscriberPoint>,
    val topPosts: List<ChannelTopPost>
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
    val createdAt: Long = 0L,
    // НОВОЕ (режимы доступа каналов): режим доступа и ограничения канала —
    // используются в ChatScreen для применения ограничений (пересылка, комментарии и т.д.).
    val accessMode: ChannelAccessMode = ChannelAccessMode.OPEN,
    val restrictions: ChannelRestrictions = ChannelRestrictions.DEFAULT
)

sealed class ChatListResult {
    data class Success(val chats: List<ChatPreview>) : ChatListResult()
    data class Error(val message: String) : ChatListResult()
}

// НОВОЕ (чат поддержки): одна беседа поддержки в админ-панели.
// Каждый пользователь пишет в свою единственную беседу поддержки (support_<uid>);
// админы (ADMIN_EMAILS) видят все беседы и отвечают в тот же чат.
data class SupportConversation(
    val chatId: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val avatarBase64: String?,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val lastMessageSenderId: String?,
    // true, если последнее сообщение от пользователя (требует ответа админа).
    val awaitingReply: Boolean
)

data class GroupInfo(
    val title: String,
    val members: List<YodoUser>,
    val createdBy: String?,
    // НОВОЕ (конфиденциальность групп): режим доступа (как у каналов).
    val accessMode: ChannelAccessMode = ChannelAccessMode.OPEN,
    // НОВОЕ (группы): описание группы.
    val description: String = "",
    val isForum: Boolean = false
)

interface ChatRepository {

    companion object {
        const val OFFICIAL_CHANNEL_ID = "yodo_official_channel"
        val ADMIN_EMAILS = listOf(
            "artemmetra2022spb@gmail.com",
            "artemmelnik2@yandex.ru"
        )
        // НОВОЕ (чат поддержки): id беседы поддержки детерминирован по uid.
        const val SUPPORT_CHAT_PREFIX = "support_"
        const val SUPPORT_TITLE = "Поддержка YodoMessenger"
        fun supportChatIdFor(uid: String) = SUPPORT_CHAT_PREFIX + uid
    }

    fun observeChatList(): Flow<ChatListResult>
    suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult
    suspend fun createGroupChat(
    title: String,
    memberIds: List<String>,
    description: String = "",
    avatarBitmap: android.graphics.Bitmap? = null,
    // НОВОЕ (конфиденциальность групп): такой же режим доступа, как у каналов.
    accessMode: ChannelAccessMode = ChannelAccessMode.OPEN,
    isForum: Boolean = false
): CreateChatResult

    // НОВОЕ: создание канала с необязательной аватаркой (Bitmap после кропа).
    // НОВОЕ (режимы доступа): при создании можно сразу задать режим доступа канала.
    suspend fun createChannel(
        title: String,
        description: String,
        avatarBitmap: Bitmap? = null,
        accessMode: ChannelAccessMode = ChannelAccessMode.OPEN
    ): CreateChatResult

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
    // НОВОЕ (каталог/рекомендации каналов): подборка без поискового запроса —
    // топ по подписчикам + группировка по категориям, для отдельного экрана каталога.
    // НОВОЕ (каталог/рекомендации каналов): подборка без поискового запроса —
    // топ по подписчикам + группировка остальных каналов по категориям, для отдельного экрана каталога.
    suspend fun getChannelDirectory(): ChannelDirectory
    // НОВОЕ (статистика для владельца): расширенная аналитика канала — рост аудитории,
    // охват и вовлечённость постов, топ постов. Доступно только владельцу/админам.
    suspend fun getChannelStats(chatId: String): ChannelStats?
    /** Полный профиль канала (экран ChannelProfileScreen). */
    suspend fun getChannelProfile(chatId: String): ChannelProfile?
    /** Обновление названия и описания канала (владелец/админ). */
    suspend fun updateChannelInfo(chatId: String, title: String, description: String): ChannelUpdateResult
    /** Загрузка аватарки канала — сжатый Base64 прямо в документ чата. */
    suspend fun uploadChannelAvatar(chatId: String, bitmap: Bitmap): ChannelUpdateResult
    // НОВОЕ (F5): категория и теги канала (для поиска/навигации).
    suspend fun updateChannelMeta(chatId: String, category: String?, tags: List<String>): ChannelUpdateResult
    // НОВОЕ (F5): обложка (баннер) канала, сжатый Base64.
    suspend fun uploadChannelCover(chatId: String, bitmap: Bitmap): ChannelUpdateResult

    // === НОВОЕ (режимы доступа и ограничения каналов) ===
    /** Сменить режим доступа канала (владелец/админ). */
    suspend fun updateChannelAccessMode(chatId: String, mode: ChannelAccessMode): ChannelUpdateResult
    /** Обновить набор ограничений канала (пересылка, комментарии, реакции и т.д.). */
    suspend fun updateChannelRestrictions(chatId: String, restrictions: ChannelRestrictions): ChannelUpdateResult
    /** Подать заявку на вступление в модерируемый канал. */
    suspend fun requestToJoinChannel(chatId: String): ChannelUpdateResult
    /** Отменить свою заявку на вступление. */
    suspend fun cancelJoinRequest(chatId: String): ChannelUpdateResult
    /** Список ожидающих заявок на вступление (владелец/админ). */
    suspend fun getJoinRequests(chatId: String): List<JoinRequest>
    /** Одобрить заявку — подписать пользователя на канал. */
    suspend fun approveJoinRequest(chatId: String, userId: String): ChannelUpdateResult
    /** Отклонить заявку. */
    suspend fun rejectJoinRequest(chatId: String, userId: String): ChannelUpdateResult

    // === НОВОЕ (чат поддержки) ===
    /** Является ли текущий пользователь админом поддержки (по email). */
    fun isSupportAdmin(): Boolean
    /** Создаёт (при необходимости) и возвращает id личной беседы поддержки текущего пользователя. */
    suspend fun getOrCreateSupportChat(): CreateChatResult
    /** Для админ-панели: поток всех бесед поддержки (новые сверху). */
    fun observeSupportConversations(): Flow<List<SupportConversation>>

    suspend fun getChatInfo(chatId: String): ChatInfo?
    suspend fun getGroupInfo(chatId: String): GroupInfo?
    fun observeForumTopics(chatId: String): Flow<List<ForumTopic>>
    suspend fun createForumTopic(chatId: String, title: String): ChannelUpdateResult
    /** Закрыть/открыть тему (запретить/разрешить писать). Владелец/админ группы. */
    suspend fun toggleTopicClosed(chatId: String, topicId: String): ChannelUpdateResult
    /** Удалить тему целиком. Владелец/админ группы. */
    suspend fun deleteForumTopic(chatId: String, topicId: String): ChannelUpdateResult
    /** Закрепить/открепить тему сверху списка (персонально для текущего пользователя). */
    suspend fun togglePinTopic(chatId: String, topicId: String): ChannelUpdateResult
    /** Отметить тему прочитанной для текущего пользователя. */
    suspend fun markTopicAsRead(chatId: String, topicId: String)
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

    // === НОВОЕ (система ролей с гранулярными правами, п.1 ТЗ) ===

    /** Роли, назначенные всем участникам чата (userId -> AssignedRole). Владелец не входит. */
    suspend fun getAssignedRoles(chatId: String): List<AssignedRole>
    /** Кастомные роли, созданные для этого чата. */
    suspend fun getCustomRoles(chatId: String): List<CustomRole>
    /** Итоговые права конкретного участника (учитывает встроенную/кастомную роль и owner). */
    suspend fun getMemberPermissions(chatId: String, userId: String): MemberPermissions
    /** Назначить участнику встроенную роль (Модератор/Помощник/Редактор контента). */
    suspend fun assignBuiltInRole(chatId: String, userId: String, role: BuiltInRole): ChannelUpdateResult
    /** Назначить участнику кастомную роль. */
    suspend fun assignCustomRole(chatId: String, userId: String, customRoleId: String): ChannelUpdateResult
    /** Снять роль с участника (возврат к обычному участнику без прав). */
    suspend fun revokeRole(chatId: String, userId: String): ChannelUpdateResult
    /** Создать новую кастомную роль с заданным набором прав. */
    suspend fun createCustomRole(chatId: String, name: String, permissions: Set<app.yodo.messenger.domain.model.Permission>): ChannelUpdateResult
    /** Изменить название/права существующей кастомной роли. */
    suspend fun updateCustomRole(chatId: String, roleId: String, name: String, permissions: Set<app.yodo.messenger.domain.model.Permission>): ChannelUpdateResult
    /** Удалить кастомную роль (участники с этой ролью теряют права). */
    suspend fun deleteCustomRole(chatId: String, roleId: String): ChannelUpdateResult

    // === НОВОЕ (журнал действий администраторов, п.2 ТЗ) ===

    /** Записать действие в журнал администраторов чата (вызывается после успешного действия). */
    suspend fun logAdminAction(
        chatId: String,
        actionType: app.yodo.messenger.domain.model.AdminActionType,
        details: String = "",
        targetUserId: String? = null,
        targetUserName: String? = null
    )
    /** Постранично получить записи журнала с фильтрацией по типу/автору/периоду. */
    suspend fun getAdminLog(chatId: String, filter: AdminLogFilter, limit: Int = 50, startAfterTimestamp: Long? = null): List<AdminLogEntry>

    // === НОВОЕ (система жалоб, п.5 ТЗ): бан участника чата/канала ===

    /** ��сключить и заблокировать участника — он теряет доступ к чату и не может вернуться по приглашению. */
    suspend fun banMember(chatId: String, userId: String): ChannelUpdateResult
    suspend fun unbanMember(chatId: String, userId: String): ChannelUpdateResult
    suspend fun getBannedMemberIds(chatId: String): List<String>
}