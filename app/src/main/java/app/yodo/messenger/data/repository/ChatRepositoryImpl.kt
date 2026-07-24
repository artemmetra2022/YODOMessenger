package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatInfo
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.GroupInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

        // ИСПРАВЛЕНИЕ (баг "аватарки не видны у других"): документ чата сам по себе не хранит
        // аватар собеседника (и не должен — он может меняться). Раньше avatarBase64 нигде не
        // подгружался для списка чатов вообще. Кэш ниже — чтобы не дёргать Firestore за одним
        // и тем же пользователем повторно при каждом обновлении списка.
        // Кэш хранит триплет (avatarUrl, avatarBase64, username), т.к. и то и другое
        // может меняться у собеседника уже после создания чата, а поле "otherUsername"
        // в самом документе чата — статичный снимок на момент создания.
        val avatarCache = mutableMapOf<String, Triple<String?, String?, String?>>() // uid -> (avatarUrl, avatarBase64, username)
        // uid -> (isOnline, hideOnlineStatus). Поле "isOnline" в самом документе чата никогда
        // не обновляется (пишется один раз при создании чата как false), поэтому раньше значок
        // "в сети" в списке чатов всегда был потушен. Берём актуальный статус из документа
        // пользователя и сразу учитываем его настройку конфиденциальности.
        val presenceCache = mutableMapOf<String, Boolean>()
        val presenceListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()

        val query = firestore.collection("chats")
            .whereArrayContains("participantIds", uid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(ChatListResult.Error(error.message ?: "Неизвестная ошибка Firestore"))
                return@addSnapshotListener
            }

            val rawChats = snapshot?.documents.orEmpty().mapNotNull { doc ->
                try {
                    val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val unreadCounts = doc.get("unreadCounts") as? Map<*, *>
                    val unreadForMe = (unreadCounts?.get(uid) as? Long)?.toInt() ?: 0
                    val titles = doc.get("titles") as? Map<*, *>
                    val personalizedTitle = titles?.get(uid) as? String
                    val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"
                    val pinnedMap = doc.get("pinned") as? Map<*, *>
                    val mutedMap = doc.get("muted") as? Map<*, *>
                    val type = doc.getString("type")?.let { rawType ->
                        runCatching { ChatType.valueOf(rawType) }.getOrDefault(ChatType.PRIVATE)
                    } ?: ChatType.PRIVATE
                    val otherUserId = if (type == ChatType.PRIVATE) {
                        participantIds.firstOrNull { it != uid }
                    } else null
                    val cachedAvatar = otherUserId?.let { avatarCache[it] }
                    val cachedPresence = otherUserId?.let { presenceCache[it] } ?: false

                    ChatPreview(
                        chatId = doc.id,
                        title = title,
                        username = cachedAvatar?.third ?: doc.getString("otherUsername"),
                        avatarUrl = cachedAvatar?.first ?: doc.getString("avatarUrl"),
                        avatarBase64 = cachedAvatar?.second,
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                        unreadCount = unreadForMe,
                        isOnline = cachedPresence,
                        type = type,
                        isPinned = pinnedMap?.get(uid) as? Boolean ?: false,
                        isMuted = mutedMap?.get(uid) as? Boolean ?: false,
                        otherUserId = otherUserId
                    )
                } catch (e: Exception) { null }
            }

            val sorted = rawChats.sortedByDescending { it.isPinned }
            trySend(ChatListResult.Success(sorted))

            // Догружаем аватарки и актуальный username собеседников, которых ещё нет
            // в кэше, и присылаем обновлённый список вторым событием — UI просто
            // перерисуется с уже готовыми данными.
            val missingIds = rawChats.mapNotNull { it.otherUserId }.filter { it !in avatarCache }.distinct()
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
                        } catch (e: Exception) { /* попробуем в следующий раз */ }
                    }
                    val updated = rawChats.map { chat ->
                        val cached = chat.otherUserId?.let { avatarCache[it] }
                        val cachedPresence = chat.otherUserId?.let { presenceCache[it] } ?: chat.isOnline
                        val withAvatar = if (cached != null) {
                            chat.copy(
                                avatarUrl = cached.first ?: chat.avatarUrl,
                                avatarBase64 = cached.second,
                                username = cached.third ?: chat.username
                            )
                        } else chat
                        withAvatar.copy(isOnline = cachedPresence)
                    }.sortedByDescending { it.isPinned }
                    trySend(ChatListResult.Success(updated))
                }
            }

            // Подписываемся на живой статус "в сети" для новых собеседников (значок в списке
            // чатов раньше никогда не загорался, т.к. читал неизменяемое поле из документа чата).
            val newParticipantIds = rawChats.mapNotNull { it.otherUserId }
                .filter { it !in presenceListeners }.distinct()
            newParticipantIds.forEach { otherId ->
                val presenceListener = firestore.collection("users").document(otherId)
                    .addSnapshotListener { presenceSnapshot, _ ->
                        if (presenceSnapshot == null || !presenceSnapshot.exists()) {
                            presenceCache[otherId] = false
                        } else {
                            val hidden = presenceSnapshot.getBoolean("hideOnlineStatus") ?: false
                            presenceCache[otherId] = !hidden && (presenceSnapshot.getBoolean("isOnline") ?: false)
                        }
                        val updated = rawChats.map { chat ->
                            if (chat.otherUserId == otherId) chat.copy(isOnline = presenceCache[otherId] ?: false) else chat
                        }.sortedByDescending { it.isPinned }
                        trySend(ChatListResult.Success(updated))
                    }
                presenceListeners[otherId] = presenceListener
            }
        }
        awaitClose {
            listener.remove()
            presenceListeners.values.forEach { it.remove() }
        }
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
            CreateChatResult.Error(e.message ?: "Не удалось создать чат")
        }
    }

    override suspend fun createGroupChat(title: String, memberIds: List<String>): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid ?: return CreateChatResult.Error("Вы не авторизованы")
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return CreateChatResult.Error("Введите название группы")
        val allParticipants = (memberIds + uid).distinct()
        if (allParticipants.size < 3) return CreateChatResult.Error("Выберите хотя бы 2 участников")

        return try {
            val newChatRef = firestore.collection("chats").document()
            newChatRef.set(
                mapOf(
                    "participantIds" to allParticipants,
                    "type" to "GROUP",
                    "title" to trimmedTitle,
                    "lastMessage" to "",
                    "lastMessageTimestamp" to System.currentTimeMillis(),
                    "unreadCounts" to allParticipants.associateWith { 0 },
                    "isOnline" to false,
                    "createdBy" to uid
                )
            ).await()
            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            CreateChatResult.Error(e.message ?: "Не удалось создать группу")
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

            ChatInfo(
                title = title,
                otherUserId = otherUserId,
                type = type,
                avatarUrl = doc.getString("avatarUrl"),
                avatarBase64 = null,
                otherUserPhotoUrl = otherPhotoUrl,
                otherUserAvatarBase64 = otherAvatarBase64
            )
        } catch (e: Exception) { null }
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
}
