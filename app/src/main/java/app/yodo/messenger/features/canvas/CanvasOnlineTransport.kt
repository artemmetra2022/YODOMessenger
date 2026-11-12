package app.yodo.messenger.features.canvas

import android.util.Base64
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import javax.inject.Inject

/**
 * Совместная доска через интернет (этап 5). Те же сообщения и тот же «ведущий-арбитр», что и в Nearby;
 * меняется только канал: Firestore вместо Bluetooth/Wi-Fi.
 *
 * canvasInvites/{КОД}                  — одноразовое приглашение на 10 минут (код → id сессии)
 * canvasSessions/{id}                  — участники, статус open → joined → active → ended, «пульс» обоих
 * canvasSessions/{id}/toGuest/{0000000001…} и /toHost/{…} — кадры протокола (CanvasFrames), создаются один раз
 * canvasSessions/{id}/cursors/{uid}    — последний курсор участника
 *
 * Содержимое доски идёт через сервер и НЕ шифруется сквозным шифрованием.
 */
class CanvasOnlineTransport @Inject constructor(
    private val auth: FirebaseAuth, private val firestore: FirebaseFirestore, private val syncSettings: BoardSyncSettings
) : CanvasTransport {
    private val _state = MutableStateFlow(CanvasLinkState()); override val state = _state.asStateFlow()
    private val packets = Channel<ByteArray>(16); override val incoming = packets.receiveAsFlow()
    override val connectTimeoutMs: Long = 180_000L
    private val root = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val sendMutex = Mutex()
    @Volatile private var generation = 0L
    override val epoch: Long get() = generation
    private var sessionJob: Job? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    @Volatile private var sid: String? = null
    @Volatile private var me: String? = null
    @Volatile private var peer: String? = null
    @Volatile private var hosting = false
    private var invite: String? = null
    private var lastEditSend = 0L
    @Volatile private var nextOut = 1L
    private var reorder = CanvasPacketReorder()
    private val assembler = CanvasFrames.Assembler()
    @Volatile private var lastPeerSeen: Long? = null
    @Volatile private var lastPeerChange = 0L
    @Volatile private var lastCursorWrite = 0L
    private fun now() = System.nanoTime() / 1_000_000
    private fun sessionRef(id: String) = firestore.collection("canvasSessions").document(id)
    private fun inviteRef(code: String) = firestore.collection("canvasInvites").document(code)
    private fun displayName() = auth.currentUser?.displayName?.trim()?.take(40)?.takeIf { it.isNotEmpty() } ?: "Участник"
    private fun expiry(ms: Long) = Timestamp(Date(System.currentTimeMillis() + ms))

    override fun host() {
        val gen = begin(true) ?: return
        val id = UUID.randomUUID().toString(); val code = CanvasInviteCode.generate(); val uid = requireNotNull(me)
        sid = id; invite = code
        _state.value = CanvasLinkState(CanvasLinkPhase.HOSTING, true, id, code = CanvasInviteCode.display(code), online = true)
        launchSession(gen) {
            val batch = firestore.batch()
            batch.set(sessionRef(id), mapOf(
                "hostUid" to uid, "hostName" to displayName(), "guestUid" to null, "guestName" to null,
                "status" to "open", "inviteCode" to code, "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to expiry(SESSION_TTL_MS), "hostSeen" to FieldValue.serverTimestamp(), "guestSeen" to null))
            batch.set(inviteRef(code), mapOf("sid" to id, "hostUid" to uid, "expireAt" to expiry(INVITE_TTL_MS)))
            withTimeout(20_000) { batch.commit().await() }
            if (gen != generation) return@launchSession
            watch(gen); heartbeat(gen)
        }
    }

    /** Для онлайн-доски вместо поиска вводится код: см. join(). */
    override fun discover() { stop("Для онлайн-доски введите код приглашения") }
    override fun connect(peer: CanvasPeer) = Unit

    fun join(rawCode: String) {
        val code = CanvasInviteCode.normalize(rawCode)
        val gen = begin(false) ?: return
        if (code == null) { stop("Неверный код приглашения: нужны 8 символов, буквы и цифры"); return }
        invite = code
        _state.value = CanvasLinkState(CanvasLinkPhase.CONNECTING, false, null, online = true)
        launchSession(gen) {
            val uid = requireNotNull(me)
            val inv = withTimeout(20_000) { inviteRef(code).get().await() }
            check(inv.exists()) { "Приглашение не найдено, истекло или уже использовано" }
            val id = inv.getString("sid").orEmpty(); val host = inv.getString("hostUid").orEmpty()
            require(CanvasLimits.validId(id) && host.isNotBlank()) { "Некорректное приглашение" }
            require(host != uid) { "Это ваше собственное приглашение" }
            require((inv.getTimestamp("expireAt")?.toDate()?.time ?: 0L) > System.currentTimeMillis()) { "Срок приглашения истёк" }
            if (gen != generation) return@launchSession
            sid = id; peer = host
            _state.update { it.copy(room = id) }
            withTimeout(20_000) {
                sessionRef(id).update(mapOf("guestUid" to uid, "guestName" to displayName(), "status" to "joined", "guestSeen" to FieldValue.serverTimestamp())).await()
            }
            if (gen != generation) return@launchSession
            watch(gen); heartbeat(gen)
        }
    }

    /** Ведущий разрешает (true) или отклоняет (false) вход участника. */
    override fun verify(matches: Boolean) {
        if (!hosting || _state.value.phase != CanvasLinkPhase.VERIFYING) return
        val gen = generation; val id = sid ?: return
        if (!matches) { stop("Вы отклонили подключение"); return }
        _state.update { it.copy(phase = CanvasLinkPhase.CONNECTING) }
        launchSession(gen) {
            withTimeout(20_000) { sessionRef(id).update(mapOf("status" to "active", "hostSeen" to FieldValue.serverTimestamp())).await() }
            invite?.let { code -> runCatching { inviteRef(code).delete().await() } }
        }
    }

    private fun begin(host: Boolean): Long? {
        stop()
        val uid = auth.currentUser?.uid
        if (uid == null) { stop("Войдите в аккаунт, чтобы рисовать онлайн"); return null }
        synchronized(lock) {
            me = uid; hosting = host; peer = null; sid = null; invite = null
            reorder = CanvasPacketReorder(); assembler.clear(); nextOut = 1L
            lastPeerSeen = null; lastPeerChange = now(); lastCursorWrite = 0L; lastEditSend = 0L
            sessionJob = SupervisorJob()
        }
        return generation
    }

    private fun launchSession(gen: Long, block: suspend () -> Unit) {
        val job = sessionJob ?: return
        root.launch(job) {
            try { block() }
            catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                if (gen == generation) stop(explain(e))
            }
        }
    }

    private fun explain(e: Throwable): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Нет доступа: приглашение недействительно или на сервере не развёрнуты правила онлайн-доски"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> "Нет связи с сервером"
        e is TimeoutCancellationException -> "Сервер не ответил вовремя"
        e is IllegalArgumentException || e is IllegalStateException -> e.message?.take(150) ?: "Ошибка онлайн-доски"
        else -> "Ошибка онлайн-доски: ${e.localizedMessage.orEmpty().take(100)}"
    }

    private fun watch(gen: Long) {
        val id = sid ?: return
        val registration = sessionRef(id).addSnapshotListener { snap, error ->
            if (gen != generation) return@addSnapshotListener
            if (error != null) { stop(explain(error)); return@addSnapshotListener }
            if (snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                if (!snap.metadata.isFromCache) stop(if (hosting) "Сессия завершена" else "Ведущий завершил сессию")
                return@addSnapshotListener
            }
            try { onSession(gen, snap) } catch (e: Exception) { stop(explain(e)) }
        }
        attach(gen, registration)
    }

    private fun attach(gen: Long, registration: ListenerRegistration) {
        synchronized(lock) { if (gen == generation) listeners.add(registration) else registration.remove() }
    }

    private fun onSession(gen: Long, snap: DocumentSnapshot) {
        val status = snap.getString("status") ?: return
        if (status == "ended") { stop(if (hosting) "Собеседник завершил сессию" else "Ведущий завершил сессию или отклонил подключение"); return }
        val seen = snap.getTimestamp(if (hosting) "guestSeen" else "hostSeen")?.toDate()?.time
        if (seen != null && seen != lastPeerSeen) { lastPeerSeen = seen; lastPeerChange = now() }
        val phase = _state.value.phase
        if (hosting) {
            val guest = snap.getString("guestUid")
            when {
                status == "joined" && phase == CanvasLinkPhase.HOSTING -> {
                    if (guest.isNullOrBlank() || guest == me) { stop("Некорректное подключение"); return }
                    peer = guest
                    _state.update { it.copy(phase = CanvasLinkPhase.VERIFYING, peerName = snap.getString("guestName")?.take(40) ?: "Участник") }
                }
                status == "active" && phase == CanvasLinkPhase.CONNECTING -> connected(gen)
            }
        } else if (status == "active" && phase == CanvasLinkPhase.CONNECTING) {
            val host = snap.getString("hostUid")
            if (host.isNullOrBlank() || host != peer) { stop("Некорректная сессия"); return }
            _state.update { it.copy(peerName = snap.getString("hostName")?.take(40)) }
            connected(gen)
        }
    }

    private fun connected(gen: Long) {
        val id = sid ?: return; val other = peer ?: return
        lastPeerChange = now()
        val inbox = sessionRef(id).collection(if (hosting) "toHost" else "toGuest")
            .addSnapshotListener { snap, error -> onPackets(gen, snap, error) }
        val cursors = sessionRef(id).collection("cursors").document(other)
            .addSnapshotListener { snap, error -> onCursor(gen, snap, error) }
        attach(gen, inbox); attach(gen, cursors)
        _state.update { it.copy(phase = CanvasLinkPhase.CONNECTED, code = null) }
    }

    private fun onPackets(gen: Long, snap: com.google.firebase.firestore.QuerySnapshot?, error: FirebaseFirestoreException?) {
        if (gen != generation) return
        if (error != null) { stop(explain(error)); return }
        val result = snap ?: return
        try {
            synchronized(lock) {
                if (gen != generation) return
                for (change in result.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val number = CanvasInviteCode.packetNumber(change.document.id) ?: continue
                    val frame = Base64.decode(change.document.getString("frame") ?: error("Пустой пакет"), Base64.NO_WRAP)
                    for (bytes in reorder.accept(number, frame, now())) {
                        val whole = assembler.accept(bytes, now()) ?: continue
                        check(packets.trySend(whole).isSuccess) { "Перегрузка канала доски" }
                    }
                }
            }
        } catch (e: Exception) { stop("Получен некорректный пакет доски. Подключитесь заново.") }
    }

    private fun onCursor(gen: Long, snap: DocumentSnapshot?, error: FirebaseFirestoreException?) {
        if (gen != generation || error != null || snap == null || !snap.exists()) return
        runCatching {
            val bytes = Base64.decode(snap.getString("b") ?: return, Base64.NO_WRAP)
            if (bytes.size in 1..2048) packets.trySend(bytes)
        }
    }

    private fun heartbeat(gen: Long) = launchSession(gen) {
        val id = sid ?: return@launchSession
        val field = if (hosting) "hostSeen" else "guestSeen"
        val started = now()
        while (gen == generation) {
            delay(HEARTBEAT_MS)
            if (gen != generation) break
            val phase = _state.value.phase
            if (phase == CanvasLinkPhase.HOSTING && now() - started > INVITE_TTL_MS) { stop("Приглашение истекло. Создайте новое."); break }
            if (phase == CanvasLinkPhase.CONNECTED) {
                if (now() - lastPeerChange > DEAD_MS) { stop("Собеседник не отвечает. Подтверждённые изменения сохранены локально."); break }
                if (synchronized(lock) { reorder.gapExceeded(now(), GAP_MS) }) { stop("Потеряна часть данных. Подключитесь заново."); break }
            }
            runCatching { withTimeout(10_000) { sessionRef(id).update(field, FieldValue.serverTimestamp()).await() } }
        }
    }

    override suspend fun send(bytes: ByteArray) = sendMutex.withLock {
        check(_state.value.phase == CanvasLinkPhase.CONNECTED) { "Нет подключения" }
        val id = requireNotNull(sid); val gen = generation
        val message = CanvasCollabWire.decode(bytes)
        if (message is CanvasWire.Propose || message is CanvasWire.Committed) {
            val wait = syncSettings.intervalMs.value - (now() - lastEditSend)
            if (lastEditSend != 0L && wait > 0) delay(wait)
            check(gen == generation && _state.value.phase == CanvasLinkPhase.CONNECTED) { "Соединение закрыто" }
            lastEditSend = now()
        }
        val frames = CanvasFrames.split(bytes)
        val outbox = sessionRef(id).collection(if (hosting) "toGuest" else "toHost")
        val expire = expiry(SESSION_TTL_MS)
        val first = nextOut; nextOut += frames.size
        // Не больше 15 пакетов в одной записи: у правил Firestore есть лимит обращений get() на запрос.
        // Порядок гарантируют номера пакетов, получатель складывает их по порядку.
        for (group in frames.withIndex().chunked(15)) {
            val batch = firestore.batch()
            group.forEach { (index, frame) ->
                batch.set(outbox.document(CanvasInviteCode.packetId(first + index)), mapOf("frame" to Base64.encodeToString(frame, Base64.NO_WRAP), "expireAt" to expire))
            }
            withTimeout(30_000) { batch.commit().await() }
        }
        check(gen == generation) { "Соединение закрыто" }
    }

    override suspend fun cursor(bytes: ByteArray) {
        val id = sid ?: return; val uid = me ?: return
        if (_state.value.phase != CanvasLinkPhase.CONNECTED || bytes.size > 1500) return
        val time = now(); if (time - lastCursorWrite < CURSOR_INTERVAL_MS) return
        lastCursorWrite = time
        withTimeoutOrNull(5_000) {
            sessionRef(id).collection("cursors").document(uid)
                .set(mapOf("b" to Base64.encodeToString(bytes, Base64.NO_WRAP), "expireAt" to expiry(SESSION_TTL_MS))).await()
        }
    }

    override fun stop(error: String?) {
        val old = synchronized(lock) {
            val previous = Triple(sid, hosting, invite)
            generation++
            listeners.forEach { runCatching { it.remove() } }; listeners.clear()
            sessionJob?.cancel(); sessionJob = null
            sid = null; peer = null; me = null; invite = null
            reorder = CanvasPacketReorder(); assembler.clear(); nextOut = 1L; lastPeerSeen = null
            previous
        }
        while (packets.tryReceive().isSuccess) Unit
        _state.value = CanvasLinkState(error = error)
        old.first?.let { cleanup(it, old.second, old.third) }
    }

    /** Best-effort: пометить сессию завершённой и стереть пакеты. Остатки удалит TTL по полю expireAt. */
    private fun cleanup(id: String, host: Boolean, code: String?) {
        root.launch {
            runCatching { withTimeout(10_000) { sessionRef(id).update("status", "ended").await() } }
            runCatching {
                withTimeout(20_000) {
                    for (name in listOf("toGuest", "toHost")) {
                        sessionRef(id).collection(name).get().await().documents.chunked(400).forEach { part ->
                            val batch = firestore.batch(); part.forEach { batch.delete(it.reference) }; batch.commit().await()
                        }
                    }
                }
            }
            if (host) {
                runCatching { code?.let { inviteRef(it).delete().await() } }
                runCatching { sessionRef(id).collection("cursors").get().await().documents.forEach { it.reference.delete().await() } }
                runCatching { delay(2_000); sessionRef(id).delete().await() } // даём участнику увидеть статус «ended»
            }
        }
    }

    private companion object {
        const val INVITE_TTL_MS = 10 * 60 * 1000L
        const val SESSION_TTL_MS = 12 * 60 * 60 * 1000L
        const val HEARTBEAT_MS = 20_000L
        const val DEAD_MS = 75_000L
        const val GAP_MS = 20_000L
        const val CURSOR_INTERVAL_MS = 350L
    }
}
