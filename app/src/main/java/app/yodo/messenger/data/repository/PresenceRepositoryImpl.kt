package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.UserPresence
import app.yodo.messenger.domain.repository.PresenceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presence хранится прямо в документе "users/{uid}":
 *   isOnline: Boolean, lastSeen: Long (millis)
 *
 * Индикатор "печатает" — в документе "chats/{chatId}":
 *   typingUsers: { uid: Boolean }
 *
 * Это простая (не "точная") presence-система: нет детекции разрыва соединения
 * (если приложение убито системой без вызова onStop, статус останется "online" до
 * следующего открытия/закрытия). Для MVP этого достаточно; полноценная presence
 * потребовала бы Realtime Database onDisconnect() — отдельная более сложная интеграция.
 */
@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : PresenceRepository {

    override fun setOnline(isOnline: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener {
                // Не критично — если документ ещё не существует (гонка при первой регистрации),
                // presence обновится при следующем переходе foreground/background.
            }
    }

    override fun observePresence(uid: String): Flow<UserPresence> = callbackFlow {
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(UserPresence(isOnline = false, lastSeenMillis = 0L))
                    return@addSnapshotListener
                }
                trySend(
                    UserPresence(
                        isOnline = snapshot.getBoolean("isOnline") ?: false,
                        lastSeenMillis = snapshot.getLong("lastSeen") ?: 0L
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    override fun observeTypingUsers(chatId: String): Flow<Set<String>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val typingMap = snapshot.get("typingUsers") as? Map<*, *>
                val typingUids = typingMap
                    ?.filterValues { it == true }
                    ?.keys
                    ?.filterIsInstance<String>()
                    ?.toSet()
                    ?: emptySet()
                trySend(typingUids)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun setTyping(chatId: String, isTyping: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            firestore.collection("chats").document(chatId)
                .update("typingUsers.$uid", isTyping)
                .await()
        } catch (e: Exception) {
            // Не критично — индикатор просто не обновится в этот раз
        }
    }
}
