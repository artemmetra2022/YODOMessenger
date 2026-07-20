package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatInfo
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.GroupInfo
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Структура документа в коллекции "chats":
 * {
 *   participantIds: [uid1, uid2, ...],
 *   titles: { uid1: "Имя uid2 (для uid1)", uid2: "Имя uid1 (для uid2)" },  // только для PRIVATE
 *   title: String,                 // используется для GROUP/CHANNEL — общее для всех название
 *   avatarUrl: String?,
 *   type: "PRIVATE" | "GROUP" | "CHANNEL",
 *   lastMessage: String,
 *   lastMessageTimestamp: Long (millis),
 *   unreadCounts: { uid: Int },
 *   isOnline: Boolean,
 *   pinned: { uid: Boolean },      // персональное закрепление — своё для каждого участника
 *   muted: { uid: Boolean }        // персональное отключение уведомлений
 * }
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    override fun observeChatList(): Flow<ChatListResult> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            trySend(ChatListResult.Success(emptyList()))
            close()
            return@callbackFlow
        }

        val query = firestore.collection("chats")
            .whereArrayContains("participantIds", uid)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ChatRepository", "Ошибка подписки на список чатов: ${error.message}", error)
                trySend(ChatListResult.Error(error.message ?: "Неизвестная ошибка Firestore"))
                return@addSnapshotListener
            }

            val chats = snapshot?.documents.orEmpty().mapNotNull { doc ->
                try {
                    val unreadCounts = doc.get("unreadCounts") as? Map<*, *>
                    val unreadForMe = (unreadCounts?.get(uid) as? Long)?.toInt() ?: 0

                    val titles = doc.get("titles") as? Map<*, *>
                    val personalizedTitle = titles?.get(uid) as? String
                    val title = personalizedTitle ?: doc.getString("title") ?: "Без названия"

                    val pinnedMap = doc.get("pinned") as? Map<*, *>
                    val mutedMap = doc.get("muted") as? Map<*, *>

                    ChatPreview(
                        chatId = doc.id,
                        title = title,
                        avatarUrl = doc.getString("avatarUrl"),
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastMessageTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
                        unreadCount = unreadForMe,
                        isOnline = doc.getBoolean("isOnline") ?: false,
                        type = doc.getString("type")?.let { rawType ->
                            runCatching { ChatType.valueOf(rawType) }.getOrDefault(ChatType.PRIVATE)
                        } ?: ChatType.PRIVATE,
                        isPinned = pinnedMap?.get(uid) as? Boolean ?: false,
                        isMuted = mutedMap?.get(uid) as? Boolean ?: false
                    )
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Не удалось распарсить чат ${doc.id}: ${e.message}", e)
                    null
                }
            }

            // Закреплённые чаты — всегда сверху, внутри каждой группы сортировка по времени сохраняется
            val sorted = chats.sortedByDescending { it.isPinned }

            trySend(ChatListResult.Success(sorted))
        }

        awaitClose { listener.remove() }
    }

    override suspend fun createOrGetPrivateChat(otherUserId: String): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return CreateChatResult.Error("Вы не авторизованы")

        if (uid == otherUserId) {
            return CreateChatResult.Error("Нельзя создать чат с самим собой")
        }

        return try {
            val existing = firestore.collection("chats")
                .whereArrayContains("participantIds", uid)
                .whereEqualTo("type", "PRIVATE")
                .get().await()

            val existingChat = existing.documents.firstOrNull { doc ->
                val participants = doc.get("participantIds") as? List<*>
                participants?.contains(otherUserId) == true
            }

            if (existingChat != null) {
                return CreateChatResult.Success(existingChat.id)
            }

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
                    "isOnline" to false
                )
            ).await()

            CreateChatResult.Success(newChatRef.id)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Ошибка создания приватного чата: ${e.message}", e)
            CreateChatResult.Error(e.message ?: "Не удалось создать чат")
        }
    }

    override suspend fun createGroupChat(title: String, memberIds: List<String>): CreateChatResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return CreateChatResult.Error("Вы не авторизованы")

        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return CreateChatResult.Error("Введите название группы")

        val allParticipants = (memberIds + uid).distinct()
        if (allParticipants.size < 3) {
            return CreateChatResult.Error("Выберите хотя бы 2 участников для группы")
        }

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

            ChatInfo(title = title, otherUserId = otherUserId, type = type)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getGroupInfo(chatId: String): GroupInfo? {
        return try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (!doc.exists()) return null

            val title = doc.getString("title") ?: "Группа"
            val createdBy = doc.getString("createdBy")
            val participantIds = (doc.get("participantIds") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            val memberDocs = if (participantIds.isNotEmpty()) {
                val tasks = participantIds.map { firestore.collection("users").document(it).get() }
                com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(tasks).await()
            } else {
                emptyList()
            }

            val members = memberDocs.mapNotNull { memberDoc ->
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
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun leaveGroup(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("chats").document(chatId)
                .update("participantIds", FieldValue.arrayRemove(uid))
                .await()
        } catch (e: Exception) {
            // Не критично — можно повторить попытку
        }
    }

    override suspend fun togglePinChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val pinnedMap = snapshot.get("pinned") as? Map<*, *>
            val currentlyPinned = pinnedMap?.get(uid) as? Boolean ?: false
            chatRef.update("pinned.$uid", !currentlyPinned).await()
        } catch (e: Exception) {
            // Не критично
        }
    }

    override suspend fun toggleMuteChat(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val mutedMap = snapshot.get("muted") as? Map<*, *>
            val currentlyMuted = mutedMap?.get(uid) as? Boolean ?: false
            chatRef.update("muted.$uid", !currentlyMuted).await()
        } catch (e: Exception) {
            // Не критично
        }
    }
}
