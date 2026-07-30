package app.yodo.messenger.data.repository

import android.graphics.Bitmap
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelSearchItem
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatInfo
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.GroupInfo
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    override fun observeChatList(): Flow<ChatListResult> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(ChatListResult.Success(emptyList())); close(); return@callbackFlow }

        data class PresenceData(val rawOnline: Boolean, val lastSeen: Long, val hidden: Boolean)

        val avatarCache = mutableMapOf<String, Triple<String?, String?, String?>>()
        val presenceCache = mutableMapOf<String, PresenceData>()
        val presenceListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
        var latestChats = emptyList<ChatPreview>()
        var officialChannel: ChatPreview? = null

        fun isEffectivelyOnline(data: PresenceData?): Boolean {
            if (data == null || data.hidden || !data.rawOnline) return false
            val isStale = (System.currentTimeMillis() - data.lastSeen) >
                    PresenceRepository.PRESENCE_STALE_THRESHOLD_MILLIS
            return !isStale
        }

        fun emitList() {
            val enriched = latestChats.map { chat ->
                val cached = chat.otherUserId?.let { avatarCache[it] }
                val presence = chat.otherUserId?.let { presenceCache[it] }
                val online = isEffectivelyOnline(presence)
                val lastSeen = if (online || presence == null || presence.hidden) 0L else presence.lastSeen
                chat.copy(
                    avatarUrl = cached?.first ?: chat.avatarUrl,
                    avatarBase64 = cached?.second ?: chat.avatarBase64,
                    username = cached?.third ?: chat.username,
                    isOnline = online,
                    lastSeenMillis = lastSeen
                )
            }
            val withChannel = listOfNotNull(officialChannel) + enriched
            val sorted = withChannel.sortedByDescending { it.isPinned }
            trySend(ChatListResult.Success(sorted))
        }

        // п.41: автосоздание канала для админа
        val currentUserEmail = firebaseAuth.currentUser?.email?.lowercase()
        if (currentUserEmail != null && currentUserEmail in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }) {
            launch {
                try {
                    val channelRef = firestore.collection("chats").document(ChatRepository.OFFICIAL_CHANNEL_ID)
                    val channelDoc = channelRef.get().await()
                    if (!channelDoc.exists()) {
                        channelRef.set(
                            mapOf(
                                "participantIds" to listOf(uid),
                                "type" to "CHANNEL",
                                "title" to "YodoMessenger",
                                // НОВОЕ (переработка каналов): поля для поиска и профиля канала
                                "titleLowercase" to "yodomessenger",
                                "createdAt" to System.currentTimeMillis(),
                                "isVerified" to true,
                                "lastMessage" to "",
                                "lastMessageTimestamp" to System.currentTimeMillis(),
                                "lastMessageSenderId" to uid,
                                "lastMessageStatus" to "SENT",
                                "unreadCounts" to mapOf(uid to 0),
                                "isOnline" to false,
                                "createdBy" to uid
                            )
                        ).await()
                    }
                } catch (e: Exception) { }
            }
        }

        // Listener на официальный канал
        val channelListener = firestore.collection("chats")
            .document(ChatRepository.OFFICIAL_CHANNEL_ID)
            .addSnapshotListener { channelSnapshot, _ ->
                if (channelSnapshot != null && channelSnapshot.exists()) {
                    officialChannel = ChatPreview(
                        chatId = channelSnapshot.id,
                        title = channelSnapshot.getString("title") ?: "YodoMessenger",
                        username = null,
                        avatarUrl = null,
                        // НОВОЕ (переработка каналов): реальная аватарка официального канала, если загружена
                        avatarBase64 = channelSnapshot.getString("avatarBase64"),
                        lastMessage = channelSnapshot.getString("lastMessage") ?: "",
                        lastMessageTimestamp = channelSnapshot.getLong("lastMessageTimestamp") ?: 0L,
                        lastMessageSenderId = channelSnapshot.getString("lastMessageSenderId"),
                        lastMessageStatus = channelSnapshot.getString("lastMessageStatus"),
                        unreadCount = 0,
                        isOnline = false,
                        isVerified = channelSnapshot.getBoolean("isVerified") ?: true,
                        type = ChatType.CHANNEL,
                        isPinned = false,
                        isMuted = false,
                        otherUserId = null
                    )
                } else {
                    officialChannel = null
                }
                emitList()
            }

        val query = firestore.collection("chats")
            .whereArrayContains("participantIds", uid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ChatListResult.Error(error.message ?: "Неизвестная ошибка Firestore"))
                return@addSnapshotListener
            }
            latestChats = snapshot?.documents.orEmpty()
                .filter { it.id != ChatRepository.OFFICIAL_CHANNEL_ID }
                .mapNotNull { doc ->
                    try {
                        val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val unreadCounts = doc.get("unreadCounts") as? Map<*, *>
                        val unreadForMe = (unreadCounts?.get(uid) as? Long)?.toInt() ?: 0
                        val titles = doc.get("titles") as? Map<*, *>
                        val personalizedTitle = titles?.get(uid) as? String
                        val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"
                        val pinnedMap = doc.get("pinned") as? Map<*, *>
                        val mutedMap = doc.get("muted") as? Map<*, *>
                        val archivedMap = doc.get("archived") as? Map<*, *>
                        val type = doc.getString("type")?.let { rawType ->
                            runCatching { ChatType.valueOf(rawType) }.getOrDefault(ChatType.PRIVATE)
                        } ?: ChatType.PRIVATE
                        val otherUserId = if (type == ChatType.PRIVATE) {
                            participantIds.firstOrNull { it != uid }
                        } else null
                        ChatPreview(
                            chatId = doc.id,
                            title = title,
                            username = doc.getString("otherUsername"),
                            avatarUrl = doc.getString("avatarUrl"),
                            // У каналов и групп аватарка лежит в самом документе чата
                            // (поле avatarBase64), а не в профиле пользователя.
                            avatarBase64 = if (type == ChatType.CHANNEL || type == ChatType.GROUP) doc.getString("avatarBase64") else null,
                            lastMessage = doc.getString("lastMessage") ?: "",
                            lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                            lastMessageSenderId = doc.getString("lastMessageSenderId"),
                            lastMessageStatus = doc.getString("lastMessageStatus"),
                            unreadCount = unreadForMe,
                            isOnline = false,
                            isVerified = doc.getBoolean("isVerified") ?: false,
                            type = type,
                            isPinned = pinnedMap?.get(uid) as? Boolean ?: false,
                            isMuted = mutedMap?.get(uid) as? Boolean ?: false,
                            otherUserId = otherUserId,
                            subscriberCount = if (type == ChatType.CHANNEL) participantIds.size else 0,
                            isArchived = archivedMap?.get(uid) as? Boolean ?: false
                        )
                    } catch (e: Exception) { null }
                }
            emitList()

            val missingIds = latestChats.mapNotNull { it.otherUserId }.filter { it !in avatarCache }.distinct()
            if (missingIds.isNotEmpty()) {
                launch {
                    missingIds.forEach { otherId ->
                        try {
                            val otherDoc = firestore.collection("users").document(otherId).get().await()
                            avatarCache[otherId] = Triple(
                                otherDoc.getString("avatarUrl"),
                                otherDoc.getString("avatarBase64"),
                                otherDoc.getString("username")
                            )
                        } catch (e: Exception) { }
                    }
                    emitList()
                }
            }

            val newParticipantIds = latestChats.mapNotNull { it.otherUserId }
                .filter { it !in presenceListeners }.distinct()
            newParticipantIds.forEach { otherId ->
                val presenceListener = firestore.collection("users").document(otherId)
                    .addSnapshotListener { presenceSnapshot, _ ->
                        presenceCache[otherId] = if (presenceSnapshot == null || !presenceSnapshot.exists()) {
                            PresenceData(rawOnline = false, lastSeen = 0L, hidden = false)
                        } else {
                            PresenceData(
                                rawOnline = presenceSnapshot.getBoolean("isOnline") ?: false,
                                lastSeen = presenceSnapshot.getLong("lastSeen") ?: 0L,
                                hidden = presenceSnapshot.getBoolean("hideOnlineStatus") ?: false
                            )
                        }
                        emitList()
                    }
                presenceListeners[otherId] = presenceListener
            }
        }

        launch {
            while (true) {
                delay(30_000L)
                emitList()
            }
        }

        awaitClose {
            listener.remove()
            channelListener.remove()
            presenceListeners.values.forEach { it.remove() }
        }
    }

    override suspend fun getOrCreateSavedChat(): String {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Пользователь не авторизован")
        val existing = firestore.collection("chats")
            .whereArrayContains("participantIds", uid)
            .whereEqualTo("type", "SAVED")
            .limit(1)
            .get().await()
        val existingDoc = existing.documents.firstOrNull()
        if (existingDoc != null) return existingDoc.id
        val newChatRef = firestore.collection("chats").document()
        newChatRef.set(
            mapOf(
                "participantIds" to listOf(uid),
                "type" to "SAVED",
                "title" to "Избранное",
                "lastMessage" to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "unreadCounts" to mapOf(uid to 0),
                "isOnline" to false
            )
        ).await()
        return newChatRef.id
    }

    override suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        if (uid == otherUserId) return CreateChatResult.Error("Нельзя создать чат с самим собой")
        return try {
            val existing = firestore.collection("chats")
                .whereArrayContains("participantIds", uid)
                .whereEqualTo("type", "PRIVATE")
                .get().await()
            val existingChat = existing.documents.firstOrNull { doc ->
                val participants = doc.get("participantIds") as? List<*>
                participants?.contains(otherUserId) == true
            }
            if (existingChat != null) return CreateChatResult.Success(existingChat.id)
            val myDoc = firestore.collection("users").document(uid).get().await()
            val otherDoc = firestore.collection("users").document(otherUserId).get().await()
            val myName = myDoc.getString("displayName") ?: "Пользователь"
            val otherName = otherDoc.getString("displayName") ?: "Пользователь"
            val newChatRef = firestore.collection("chats").document()
            newChatRef.set(
                mapOf(
                    "participantIds" to listOf(uid, otherUserId),
                    "type" to "PRIVATE",
                    "titles" to mapOf(uid to otherName, otherUserId to myName),
                    "lastMessage" to "",
                    "lastMessageTimestamp" to System.currentTimeMillis(),
                    "unreadCounts" to mapOf(uid to 0, otherUserId to 0),
                    "isOnline" to false,
                    "otherUsername" to (otherDoc.getString("username") ?: "")
                )
            ).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            CreateChatResult.Error(e.toUserMessage("Не удалось создать чат"))
        }
    }

    override suspend fun createGroupChat(
        title: String,
        memberIds: List<String>,
        description: String,
        avatarBitmap: android.graphics.Bitmap?
    ): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return CreateChatResult.Error("Введите название группы")
        val allParticipants = (memberIds + uid).distinct()
        if (allParticipants.size < 3) return CreateChatResult.Error("Выберите хотя бы 2 участников")
        return try {
            // Аватарка группы — сжатый Base64 (та же логика, что у каналов)
            val avatarBase64 = avatarBitmap?.let { bmp ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    app.yodo.messenger.util.ImageUtils.compressAvatarToBase64(bmp)
                }
            }
            val newChatRef = firestore.collection("chats").document()
            val data = mutableMapOf<String, Any?>(
                "participantIds" to allParticipants,
                "type" to "GROUP",
                "title" to trimmedTitle,
                "description" to description.trim(),
                "lastMessage" to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "unreadCounts" to allParticipants.associateWith { 0 },
                "isOnline" to false,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis()
            )
            if (avatarBase64 != null) data["avatarBase64"] = avatarBase64
            newChatRef.set(data).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            CreateChatResult.Error(e.toUserMessage("Не удалось создать группу"))
        }
    }

    override suspend fun getChatInfo(chatId: String): ChatInfo? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists()) return null
            val type = doc.getString("type") ?: "PRIVATE"
            val titles = doc.get("titles") as? Map<*, *>
            val personalizedTitle = titles?.get(uid) as? String
            val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"
            val otherUserId = if (type == "PRIVATE") {
                val participantIds = doc.get("participantIds") as? List<*>
                participantIds?.filterIsInstance<String>()?.firstOrNull { it != uid }
            } else null
            var otherPhotoUrl: String? = null
            var otherAvatarBase64: String? = null
            if (otherUserId != null) {
                val otherDoc = firestore.collection("users").document(otherUserId).get().await()
                otherPhotoUrl = otherDoc.getString("avatarUrl")
                otherAvatarBase64 = otherDoc.getString("avatarBase64")
            }
            val channelOwnerId = if (type == "CHANNEL") doc.getString("createdBy") else null
            val channelAdminIds = if (type == "CHANNEL") {
                (doc.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            } else emptyList()
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            ChatInfo(
                title = title, otherUserId = otherUserId, type = type,
                avatarUrl = doc.getString("avatarUrl"),
                // НОВОЕ (переработка каналов): у каналов аватарка в самом документе чата —
                // используется в шапке чата и в профиле канала.
                avatarBase64 = doc.getString("avatarBase64"),
                otherUserPhotoUrl = otherPhotoUrl, otherUserAvatarBase64 = otherAvatarBase64,
                isVerified = doc.getBoolean("isVerified") ?: false,
                channelOwnerId = channelOwnerId,
                channelAdminIds = channelAdminIds,
                subscriberCount = if (type == "CHANNEL") participantIds.size else 0,
                isSubscribed = type == "CHANNEL" && uid in participantIds,
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) { null }
    }

    // НОВОЕ (переработка каналов): создание пользовательского канала с аватаркой.
    // Создатель = владелец (может публиковать посты и назначать/снимать админов).
    // Каждый подписчик добавляется в participantIds, поэтому существующий механизм
    // списка чатов (whereArrayContains) сразу подхватывает канал для всех подписчиков.
    override suspend fun createChannel(title: String, description: String, avatarBitmap: Bitmap?): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return CreateChatResult.Error("Введите название канала")
        return try {
            // Аватарка канала — сжатый Base64 прямо в документ чата (без Storage).
            val avatarBase64 = avatarBitmap?.let { bmp ->
                withContext(Dispatchers.Default) { ImageUtils.compressAvatarToBase64(bmp) }
            }
            val newChatRef = firestore.collection("chats").document()
            val data = mutableMapOf<String, Any?>(
                "participantIds" to listOf(uid),
                "type" to "CHANNEL",
                "title" to trimmedTitle,
                // Для поиска каналов по префиксу без учёта регистра
                "titleLowercase" to trimmedTitle.lowercase(),
                "description" to description.trim(),
                "isVerified" to false,
                "lastMessage" to "",
                "lastMessageTimestamp" to System.currentTimeMillis(),
                "lastMessageSenderId" to uid,
                "unreadCounts" to mapOf(uid to 0),
                "isOnline" to false,
                "createdBy" to uid,
                "adminIds" to emptyList<String>(),
                "createdAt" to System.currentTimeMillis()
            )
            if (avatarBase64 != null) data["avatarBase64"] = avatarBase64
            newChatRef.set(data).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            CreateChatResult.Error(e.toUserMessage("Не удалось создать канал"))
        }
    }

    override suspend fun subscribeToChannel(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("chats").document(chatId)
                .update(
                    mapOf(
                        "participantIds" to FieldValue.arrayUnion(uid),
                        "unreadCounts.$uid" to 0
                    )
                ).await()
        } catch (e: Exception) { }
    }

    override suspend fun unsubscribeFromChannel(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            // НОВОЕ: владелец канала не может отписаться — это защита и на уровне данных,
            // не только в UI (на случай прямого вызова репозитория).
            if (doc.getString("createdBy") == uid) return
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) { }
    }

    // НОВОЕ: полное удаление канала владельцем — удаляет все сообщения и сам документ чата.
    override suspend fun deleteChannel(chatId: String): ChannelUpdateResult {
        val uid = firebaseAuth.currentUser?.uid ?: return ChannelUpdateResult.Error("Не авторизован")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val doc = chatRef.get().await()
            if (!doc.exists()) return ChannelUpdateResult.Error("Канал не найден")
            if (doc.getString("createdBy") != uid) {
                return ChannelUpdateResult.Error("Удалить канал может только его владелец")
            }
            val messagesRef = chatRef.collection("messages")
            val messagesSnapshot = messagesRef.get().await()
            val batch = firestore.batch()
            messagesSnapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            chatRef.delete().await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось удалить канал"))
        }
    }

    // НОВОЕ: приглашение пользователей в канал владельцем/админом — подписывает их напрямую.
    override suspend fun inviteUsersToChannel(chatId: String, userIds: List<String>) {
        if (userIds.isEmpty()) return
        try {
            val updates = mutableMapOf<String, Any>(
                "participantIds" to FieldValue.arrayUnion(*userIds.toTypedArray())
            )
            userIds.forEach { uid -> updates["unreadCounts.$uid"] = 0 }
            firestore.collection("chats").document(chatId).update(updates).await()
        } catch (e: Exception) { }
    }

    override suspend fun addChannelAdmin(chatId: String, userId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .update("adminIds", FieldValue.arrayUnion(userId)).await()
        } catch (e: Exception) { }
    }

    override suspend fun removeChannelAdmin(chatId: String, userId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .update("adminIds", FieldValue.arrayRemove(userId)).await()
        } catch (e: Exception) { }
    }

    // НОВОЕ (переработка каналов): поиск каналов по префиксу названия.
    // Требует composite index (type + titleLowercase) — см. firestore.indexes.json.
    override suspend fun searchChannels(query: String): List<ChannelSearchItem> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        val uid = firebaseAuth.currentUser?.uid
        return try {
            val snapshot = firestore.collection("chats")
                .whereEqualTo("type", "CHANNEL")
                .orderBy("titleLowercase")
                .startAt(normalized)
                .endAt(normalized + "\uf8ff")
                .limit(20)
                .get().await()
            snapshot.documents.mapNotNull { doc ->
                val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                ChannelSearchItem(
                    chatId = doc.id,
                    title = doc.getString("title") ?: "Без названия",
                    description = doc.getString("description").orEmpty(),
                    avatarBase64 = doc.getString("avatarBase64"),
                    subscriberCount = participantIds.size,
                    isVerified = doc.getBoolean("isVerified") ?: false,
                    isSubscribed = uid != null && uid in participantIds
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // НОВОЕ (переработка каналов): полный профиль канала для ChannelProfileScreen.
    override suspend fun getChannelProfile(chatId: String): ChannelProfile? {
        val uid = firebaseAuth.currentUser?.uid
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists() || doc.getString("type") != "CHANNEL") return null
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            ChannelProfile(
                chatId = chatId,
                title = doc.getString("title") ?: "Без названия",
                description = doc.getString("description").orEmpty(),
                avatarBase64 = doc.getString("avatarBase64"),
                subscriberCount = participantIds.size,
                isVerified = doc.getBoolean("isVerified") ?: false,
                ownerId = doc.getString("createdBy"),
                adminIds = (doc.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                isSubscribed = uid != null && uid in participantIds,
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        } catch (e: Exception) { null }
    }

    // НОВОЕ (переработка каналов): обновление названия и описания канала (владелец/админ).
    override suspend fun updateChannelInfo(chatId: String, title: String, description: String): ChannelUpdateResult {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return ChannelUpdateResult.Error("Название не может быть пустым")
        return try {
            firestore.collection("chats").document(chatId).update(
                mapOf(
                    "title" to trimmed,
                    "titleLowercase" to trimmed.lowercase(),
                    "description" to description.trim()
                )
            ).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось сохранить изменения"))
        }
    }

    // НОВОЕ (переработка каналов): загрузка аватарки канала — сжатый Base64 в документ чата.
    override suspend fun uploadChannelAvatar(chatId: String, bitmap: Bitmap): ChannelUpdateResult {
        return try {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressAvatarToBase64(bitmap)
            } ?: return ChannelUpdateResult.Error("Не удалось обработать изображение")
            firestore.collection("chats").document(chatId).update("avatarBase64", base64).await()
            ChannelUpdateResult.Success
        } catch (e: Exception) {
            ChannelUpdateResult.Error(e.toUserMessage("Не удалось загрузить фото"))
        }
    }

    override suspend fun getGroupInfo(chatId: String): GroupInfo? {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists()) return null
            val title = doc.getString("title") ?: "Группа"
            val createdBy = doc.getString("createdBy")
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val members = participantIds.mapNotNull { id ->
                val memberDoc = firestore.collection("users").document(id).get().await()
                if (!memberDoc.exists()) return@mapNotNull null
                YodoUser(
                    uid = memberDoc.id,
                    displayName = memberDoc.getString("displayName") ?: "Пользователь",
                    username = memberDoc.getString("username"),
                    bio = memberDoc.getString("bio"),
                    email = memberDoc.getString("email"),
                    phoneNumber = memberDoc.getString("phoneNumber"),
                    photoUrl = memberDoc.getString("avatarUrl"),
                    avatarBase64 = memberDoc.getString("avatarBase64")
                )
            }
            GroupInfo(title = title, members = members, createdBy = createdBy)
        } catch (e: Exception) { null }
    }

    override suspend fun leaveGroup(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) { }
    }

    override suspend fun togglePinChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val pinnedMap = snapshot.get("pinned") as? Map<*, *>
            val currentlyPinned = pinnedMap?.get(uid) as? Boolean ?: false
            chatRef.update("pinned.$uid", !currentlyPinned).await()
        } catch (e: Exception) { }
    }

    override suspend fun toggleMuteChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val mutedMap = snapshot.get("muted") as? Map<*, *>
            val currentlyMuted = mutedMap?.get(uid) as? Boolean ?: false
            chatRef.update("muted.$uid", !currentlyMuted).await()
        } catch (e: Exception) { }
    }

    // НОВОЕ (архивация чатов): та же схема хранения, что у pinned/muted —
    // map<uid, Boolean> в документе чата, персонально для каждого участника.
    override suspend fun toggleArchiveChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val archivedMap = snapshot.get("archived") as? Map<*, *>
            val currentlyArchived = archivedMap?.get(uid) as? Boolean ?: false
            chatRef.update("archived.$uid", !currentlyArchived).await()
        } catch (e: Exception) { }
    }

    override suspend fun clearChatHistory(chatId: String) {
        try {
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            val snapshot = messagesRef.get().await()
            val batch = firestore.batch()
            snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
            firestore.collection("chats").document(chatId).update(
                mapOf("lastMessage" to "", "lastMessageTimestamp" to System.currentTimeMillis())
            ).await()
        } catch (e: Exception) { throw e }
    }

    override suspend fun deleteChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            val snapshot = messagesRef.get().await()
            val batch = firestore.batch()
            snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid)).await()
        } catch (e: Exception) { throw e }
    }

    override suspend fun getOtherUserAvatar(chatId: String): Pair<String?, String?>? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: return null
            val otherId = participantIds.firstOrNull { it != uid } ?: return null
            val otherDoc = firestore.collection("users").document(otherId).get().await()
            Pair(otherDoc.getString("avatarUrl"), otherDoc.getString("avatarBase64"))
        } catch (e: Exception) { null }
    }

    override fun observeDisappearingTtl(chatId: String): Flow<Long?> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, _ ->
                val ttl = snapshot?.getLong("disappearingTtlSeconds")
                trySend(ttl)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun setDisappearingTtl(chatId: String, ttlSeconds: Long?) {
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            if (ttlSeconds == null) {
                chatRef.update("disappearingTtlSeconds", FieldValue.delete()).await()
            } else {
                chatRef.update("disappearingTtlSeconds", ttlSeconds).await()
            }
        } catch (e: Exception) { }
    }
}