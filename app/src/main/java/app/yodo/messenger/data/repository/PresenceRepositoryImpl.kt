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

    override fun setOnlineStatusHidden(hidden: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        // hideOnlineStatus — это флаг приватности: наблюдатели (observePresence) всегда
        // будут видеть isOnline = false, пока он включён, независимо от реального isOnline.
        // Дополнительно, при включении сразу пишем isOnline = false, чтобы значение в
        // документе не оставалось "залипшим" в true, если presence перестанут обновлять.
        val updates = mutableMapOf<String, Any>("hideOnlineStatus" to hidden)
        if (hidden) {
            updates["isOnline"] = false
            updates["lastSeen"] = System.currentTimeMillis()
        }
        firestore.collection("users").document(uid)
            .update(updates)
            .addOnFailureListener {
                // Не критично — если документ ещё не существует, значение применится
                // при следующей синхронизации профиля.
            }
    }

    override fun observePresence(uid: String): Flow<UserPresence> = callbackFlow {
        val myUid = firebaseAuth.currentUser?.uid

        // Правило "в обе стороны": если Я скрыл(а) свой статус "в сети", я тоже не вижу
        // статус других (кроме себя самого). Слушаем свой документ, чтобы знать текущее
        // значение hideOnlineStatus и реагировать на него сразу же, без перезахода в экран.
        var myHidden = false
        var otherListener: com.google.firebase.firestore.ListenerRegistration? = null

        fun attachOtherListener() {
            otherListener?.remove()
            otherListener = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        trySend(UserPresence(isOnline = false, lastSeenMillis = 0L))
                        return@addSnapshotListener
                    }
                    val theirHidden = snapshot.getBoolean("hideOnlineStatus") ?: false
                    // Скрыто, если ЛИБО собеседник скрыл свой статус, ЛИБО я скрыл(а) свой —
                    // работает в обе стороны. Само себя пользователь видит всегда.
                    if ((theirHidden || myHidden) && uid != myUid) {
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
        }

        val myListener = if (myUid != null) {
            firestore.collection("users").document(myUid)
                .addSnapshotListener { snapshot, _ ->
                    val newHidden = snapshot?.getBoolean("hideOnlineStatus") ?: false
                    if (newHidden != myHidden || otherListener == null) {
                        myHidden = newHidden
                        attachOtherListener()
                    }
                }
        } else {
            attachOtherListener()
            null
        }

        awaitClose {
            myListener?.remove()
            otherListener?.remove()
        }
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
