package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.UserPresence
import app.yodo.messenger.domain.repository.PresenceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presence хранится прямо в документе "users/{uid}":
 *   isOnline: Boolean, lastSeen: Long (millis)
 *
 * Индикатор "печатает" — в документе "chats/{chatId}":
 *   typingUsers: { uid: Boolean }
 *
 * Presence-система приближена к точной без полноценного onDisconnect() из Realtime
 * Database: пока приложение на переднем плане, клиент шлёт heartbeat() каждые
 * PresenceRepository.HEARTBEAT_INTERVAL_MILLIS (см. PresenceLifecycleObserver), обновляя
 * lastSeen. Если heartbeat перестал приходить дольше PRESENCE_STALE_THRESHOLD_MILLIS
 * (например, процесс убила система без вызова onStop), наблюдатели в observePresence()
 * сами считают статус устаревшим и показывают собеседника оффлайн, вместо того чтобы
 * бесконечно "залипать" на isOnline = true.
 */
@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : PresenceRepository {

    override fun setOnline(isOnline: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        // ИСПРАВЛЕНО (баг 12): set-merge вместо update — update молча падает, если документ
        // пользователя ещё не создан (гонка при первой регистрации), и статус "в сети"
        // не публикуется до следующего перехода foreground/background. Merge создаёт
        // документ при необходимости, не затирая остальные поля.
        firestore.collection("users").document(uid)
            .set(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener {
                // Не критично — следующий пульс (heartbeat) скорректирует значение.
            }
    }

    override fun heartbeat() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        // Обновляем только lastSeen — если пользователь параллельно скрыл онлайн-статус,
        // isOnline трогать не нужно, этим управляет setOnline()/setOnlineStatusHidden().
        // ИСПРАВЛЕНО (баг 12): set-merge по той же причине, что и в setOnline().
        firestore.collection("users").document(uid)
            .set(mapOf("lastSeen" to System.currentTimeMillis()), SetOptions.merge())
            .addOnFailureListener {
                // Не критично — пропущенный пульс скорректируется следующим вызовом.
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
        val myHidden = AtomicBoolean(false)
        var otherListener: com.google.firebase.firestore.ListenerRegistration? = null

        // НОВОЕ (баг 12): последние сырые данные собеседника. Хранятся в атомарной ссылке,
        // чтобы секундный тикер (ниже) мог пересчитывать актуальность статуса без ожидания
        // новых событий Firestore.
        class RawPresence(val rawOnline: Boolean, val lastSeen: Long, val theirHidden: Boolean)
        val latest = AtomicReference<RawPresence?>(null)

        fun sendCurrent() {
            val raw = latest.get()
            if (raw == null) {
                trySend(UserPresence(isOnline = false, lastSeenMillis = 0L))
                return
            }
            // Скрыто, если ЛИБО собеседник скрыл свой статус, ЛИБО я скрыл(а) свой —
            // работает в обе стороны. Само себя пользователь видит всегда.
            if ((raw.theirHidden || myHidden.get()) && uid != myUid) {
                trySend(UserPresence(isOnline = false, lastSeenMillis = 0L))
                return
            }
            // Если isOnline = true, но lastSeen не обновлялся дольше порога — считаем,
            // что процесс собеседника был убит системой без вызова onStop (heartbeat
            // из PresenceLifecycleObserver перестал приходить). Показываем оффлайн,
            // чтобы статус "в сети" не "залипал" неточно на неопределённое время.
            val isStale = raw.rawOnline &&
                (System.currentTimeMillis() - raw.lastSeen) > PresenceRepository.PRESENCE_STALE_THRESHOLD_MILLIS
            trySend(
                UserPresence(
                    isOnline = raw.rawOnline && !isStale,
                    lastSeenMillis = raw.lastSeen
                )
            )
        }

        fun attachOtherListener() {
            otherListener?.remove()
            otherListener = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        latest.set(null)
                    } else {
                        latest.set(
                            RawPresence(
                                rawOnline = snapshot.getBoolean("isOnline") ?: false,
                                lastSeen = snapshot.getLong("lastSeen") ?: 0L,
                                theirHidden = snapshot.getBoolean("hideOnlineStatus") ?: false
                            )
                        )
                    }
                    sendCurrent()
                }
        }

        // НОВОЕ (баг 12): тикер каждую секунду пересчитывает актуальность статуса.
        // Раньше устаревание (isStale) вычислялось ТОЛЬКО при событии от Firestore:
        // если процесс собеседника убит системой (heartbeat прекратился, но документ
        // больше не меняется), события не приходили — и статус "в сети" висел бесконечно.
        // Теперь переключение на "был(а)…" происходит в пределах секунды после порога
        // устаревания, без единой дополнительной записи/чтения в Firestore.
        val ticker = launch {
            while (isActive) {
                delay(1_000L)
                sendCurrent()
            }
        }

        val myListener = if (myUid != null) {
            firestore.collection("users").document(myUid)
                .addSnapshotListener { snapshot, _ ->
                    val newHidden = snapshot?.getBoolean("hideOnlineStatus") ?: false
                    if (newHidden != myHidden.get() || otherListener == null) {
                        myHidden.set(newHidden)
                        attachOtherListener()
                    } else {
                        sendCurrent()
                    }
                }
        } else {
            attachOtherListener()
            null
        }

        awaitClose {
            ticker.cancel()
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

    override fun setTyping(chatId: String, isTyping: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        // Fire-and-forget: метод вызывается в т.ч. из onCleared() уже уничтожаемой
        // ViewModel (viewModelScope отменён), поэтому suspend + await() здесь нельзя —
        // Firestore-запрос уходит собственным потоком и завершается сам.
        firestore.collection("chats").document(chatId)
            .update("typingUsers.$uid", isTyping)
            .addOnFailureListener {
                // Не критично — индикатор просто не обновится в этот раз
            }
    }
}
