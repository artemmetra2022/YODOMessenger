package app.yodo.messenger.features.canvas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Общая доска чата (этап 6): одна постоянная доска на чат, которую видят и могут дополнять все участники.
 * В чате она показывается живой карточкой, а чтобы что-то добавить, нужно нажать на неё (открывается редактор).
 *
 * chats/{chatId}/sharedBoard/meta            — название, сетка, кто и когда менял в последний раз
 * chats/{chatId}/sharedBoardObjects/{id}     — по документу на каждый объект рисунка (JSON одного объекта)
 *
 * Объекты неизменяемы: их только добавляют и удаляют целиком — как в протоколе совместной доски,
 * поэтому правки разных людей не конфликтуют. Фон-картинок на общей доске нет.
 * Данные идут через сервер без сквозного шифрования.
 */
sealed interface SharedBoardState {
    data object Loading : SharedBoardState
    data object Missing : SharedBoardState
    data class Ready(val board: SharedBoard) : SharedBoardState
    data class Failed(val message: String) : SharedBoardState
}

data class SharedBoard(
    val chatId: String, val title: String, val grid: CanvasGrid, val objects: List<CanvasObject>,
    val updatedAtMs: Long?, val truncated: Boolean, val generation: Long = 0L, val clearing: Boolean = false
) {
    /** Стабильный id документа для редактора: один и тот же для одного чата. */
    fun toDocument(): CanvasDocument = CanvasDocument(
        id = SharedBoardOps.documentId(chatId), title = title, width = CanvasSharedCodec.WIDTH, height = CanvasSharedCodec.HEIGHT,
        grid = grid, objects = objects
    )
}

/** Чистая логика общей доски — без Firebase, проверяется JVM-тестами. */
object SharedBoardOps {
    data class Delta(val adds: List<CanvasObject>, val removes: List<CanvasObject>) { val isEmpty get() = adds.isEmpty() && removes.isEmpty() }
    fun documentId(chatId: String): String = UUID.nameUUIDFromBytes("yodo-shared-board:$chatId".toByteArray()).toString()
    /** Что изменилось между текущим состоянием и результатом жеста редактора (только по id объектов). */
    fun delta(current: List<CanvasObject>, after: List<CanvasObject>): Delta {
        val cur = current.associateBy { it.id }; val aft = after.associateBy { it.id }
        return Delta(adds = after.filter { it.id !in cur }, removes = current.filter { it.id !in aft })
    }
    fun checkLimit(currentCount: Int, d: Delta) =
        require(currentCount - d.removes.size + d.adds.size <= CanvasLimits.MAX_OBJECTS) { "Достигнут предел объектов на общей доске (${CanvasLimits.MAX_OBJECTS})" }
    /** Обратное действие с учётом того, что у доски уже могли поменяться другие участники. */
    fun reverse(presentIds: Set<String>, step: Delta) =
        if(step.adds.size==1 && step.removes.size==1 && step.adds.single().id !in presentIds) Delta(emptyList(),emptyList())
        else Delta(adds = step.removes.filter { it.id !in presentIds }, removes = step.adds.filter { it.id in presentIds })
}

/** Хранение одного объекта в документе Firestore: JSON того же формата, что и у файла доски. */
object CanvasSharedCodec {
    const val WIDTH = 1200
    const val HEIGHT = 900
    const val MAX_JSON = 120_000
    private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"
    fun encodeObject(obj: CanvasObject): String {
        val json = CanvasCodec.encode(CanvasDocument(id = ZERO_ID, title = "o", width = WIDTH, height = HEIGHT, grid = CanvasGrid.NONE, objects = listOf(obj)))
        require(json.length <= MAX_JSON) { "Объект слишком большой для общей доски" }
        return json
    }
    /** Строгая проверка: чужой или испорченный документ просто пропускается. */
    fun decodeObject(json: String): CanvasObject? {
        if (json.isEmpty() || json.length > MAX_JSON) return null
        return runCatching {
            val doc = CanvasCodec.decode(json)
            if (doc.width != WIDTH || doc.height != HEIGHT || doc.backgroundBase64 != null || doc.objects.size != 1) null else doc.objects.single()
        }.getOrNull()
    }
}

@Singleton
class SharedBoardRepository @Inject constructor(private val firestore: FirebaseFirestore, private val auth: FirebaseAuth, private val syncSettings: BoardSyncSettings) {
    private fun meta(chatId: String) = firestore.collection("chats").document(chatId).collection("sharedBoard").document("meta")
    private fun objects(chatId: String) = firestore.collection("chats").document(chatId).collection("sharedBoardObjects")

    fun observe(chatId: String): Flow<SharedBoardState> = callbackFlow {
        var metaSnap: DocumentSnapshot? = null
        var objectsSnap: QuerySnapshot? = null
        var pending: Job? = null
        var lastPublish = 0L
        var firstReady = true
        var lastGeneration = -1L
        fun publish() {
            val m = metaSnap ?: return
            if (!m.exists() || m.getBoolean("deleted") == true) {
                pending?.cancel(); pending = null
                if (m.exists() || !m.metadata.isFromCache) trySend(SharedBoardState.Missing)
                return
            }
            val o = objectsSnap ?: return
            val generation = m.getLong("generation") ?: 0L
            val immediate = firstReady || generation != lastGeneration || m.getBoolean("clearing") == true ||
                m.metadata.hasPendingWrites() || o.metadata.hasPendingWrites()
            fun emitLatest() {
                val latestMeta = metaSnap ?: return
                val latestObjects = objectsSnap ?: return
                if (!latestMeta.exists() || latestMeta.getBoolean("deleted") == true) return
                firstReady = false
                lastGeneration = latestMeta.getLong("generation") ?: 0L
                lastPublish = android.os.SystemClock.elapsedRealtime()
                trySend(SharedBoardState.Ready(build(chatId, latestMeta, latestObjects)))
            }
            val wait = syncSettings.intervalMs.value - (android.os.SystemClock.elapsedRealtime() - lastPublish)
            if (immediate || wait <= 0) {
                pending?.cancel(); pending = null; emitLatest()
            } else if (pending?.isActive != true) {
                pending = launch { delay(wait); emitLatest() }
            }
        }
        val r1 = meta(chatId).addSnapshotListener { snap, error ->
            if (error != null) { trySend(SharedBoardState.Failed(describe(error))); return@addSnapshotListener }
            metaSnap = snap; publish()
        }
        val r2 = objects(chatId).orderBy("at").limit(CanvasLimits.MAX_OBJECTS + 1L).addSnapshotListener { snap, error ->
            if (error != null) { trySend(SharedBoardState.Failed(describe(error))); return@addSnapshotListener }
            objectsSnap = snap; publish()
        }
        val configJob = launch { syncSettings.intervalMs.collect { publish() } }
        awaitClose { pending?.cancel(); configJob.cancel(); r1.remove(); r2.remove() }
    }.distinctUntilChanged()

    private fun build(chatId: String, m: DocumentSnapshot, o: QuerySnapshot): SharedBoard {
        val epoch = m.getLong("generation") ?: 0L
        val clearing = m.getBoolean("clearing") == true
        val current = o.documents.filter { (it.getLong("generation") ?: 0L) == epoch }
        val decoded = if (clearing) emptyList() else current.mapNotNull { d ->
            d.getString("json")?.let(CanvasSharedCodec::decodeObject)?.takeIf { it.id == d.id }
        }
        return SharedBoard(
            chatId = chatId, title = m.getString("title")?.take(100)?.takeIf { it.isNotBlank() } ?: "Общая доска",
            grid = m.getString("grid")?.let { runCatching { CanvasGrid.valueOf(it) }.getOrNull() } ?: CanvasGrid.SQUARES,
            objects = decoded.take(CanvasLimits.MAX_OBJECTS),
            updatedAtMs = m.getTimestamp("updatedAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            truncated = o.documents.size > CanvasLimits.MAX_OBJECTS,
            generation = epoch, clearing = clearing
        )
    }

    /** Only an explicit user action revives a tombstone; existing listeners never recreate it. */
    suspend fun create(chatId: String, grid: CanvasGrid, revive: Boolean = false) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        withTimeout(20_000) {
            firestore.runTransaction { tx ->
                val ref = meta(chatId); val old = tx.get(ref)
                if (old.exists() && !(revive && old.getBoolean("deleted") == true)) return@runTransaction
                check(old.getBoolean("clearing") != true) { "Очистка ещё не завершена. Повторите удаление." }
                val fields = mapOf(
                    "title" to "Общая доска", "width" to CanvasSharedCodec.WIDTH, "height" to CanvasSharedCodec.HEIGHT, "grid" to grid.name,
                    "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid,
                    "generation" to ((old.getLong("generation") ?: -1L) + 1L), "deleted" to false, "clearing" to false
                )
                if (old.exists()) tx.update(ref, mapOf("grid" to grid.name, "deleted" to false, "clearing" to false,
                    "generation" to ((old.getLong("generation") ?: 0L) + 1L), "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
                else tx.set(ref, fields)
            }.await()
        }
    }

    /** Generation fence immediately invalidates old gestures/undo, including objects beyond the UI limit. */
    suspend fun reset(chatId: String, deleteBoard: Boolean) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        val token = UUID.randomUUID().toString()
        val epoch = withTimeout(20_000) {
            firestore.runTransaction { tx ->
                val ref = meta(chatId); val old = tx.get(ref)
                check(old.exists()) { "Доска не найдена" }
                val next = (old.getLong("generation") ?: 0L) + 1L
                tx.update(ref, mapOf("generation" to next, "clearing" to true, "deleted" to deleteBoard,
                    "resetToken" to token, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
                next
            }.await()
        }
        // Read the entire collection, not the limited preview. New-generation objects are never deleted.
        var after: DocumentSnapshot? = null
        while (true) {
            var query = objects(chatId).orderBy(com.google.firebase.firestore.FieldPath.documentId()).limit(100)
            after?.let { query = query.startAfter(it) }
            val page = withTimeout(30_000) { query.get(Source.SERVER).await() }
            if (page.isEmpty) break
            for (group in page.documents.filter { (it.getLong("generation") ?: 0L) < epoch }.chunked(10)) {
                val batch = firestore.batch()
                group.forEach { batch.delete(it.reference) }
                withTimeout(30_000) { batch.commit().await() }
            }
            after = page.documents.last()
        }
        withTimeout(20_000) {
            firestore.runTransaction { tx ->
                val ref = meta(chatId); val latest = tx.get(ref)
                check(latest.getString("resetToken") == token) { "Доска уже изменена другим участником" }
                tx.update(ref, mapOf("clearing" to false, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
            }.await()
        }
    }

    suspend fun updateGrid(chatId: String, grid: CanvasGrid) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        withTimeout(20_000) { meta(chatId).update(mapOf("grid" to grid.name, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid)).await() }
    }

    /** Добавляет и удаляет объекты. Записи идут группами ≤ 10: у правил Firestore есть лимит обращений get() на запрос. */
    suspend fun apply(chatId: String, adds: List<CanvasObject>, removes: List<String>, expectedGeneration: Long) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        require(adds.isNotEmpty() || removes.isNotEmpty())
        val current = withTimeout(20_000) { meta(chatId).get(Source.SERVER).await() }
        check(current.exists() && current.getBoolean("deleted") != true && current.getBoolean("clearing") != true &&
            (current.getLong("generation") ?: 0L) == expectedGeneration) { "Доска была очищена или удалена. Повторите действие." }
        val encoded = adds.map { it.id to CanvasSharedCodec.encodeObject(it) }
        val ops = encoded.map { WriteOp(add = it) } + removes.map { WriteOp(remove = it) }
        val groups = ops.chunked(10)
        groups.forEachIndexed { index, group ->
            val batch = firestore.batch()
            group.forEach { op ->
                op.add?.let { (id, json) -> batch.set(objects(chatId).document(id), mapOf("json" to json, "authorUid" to uid, "at" to FieldValue.serverTimestamp(), "generation" to expectedGeneration)) }
                op.remove?.let { id -> batch.delete(objects(chatId).document(id)) }
            }
            if (index == groups.lastIndex) batch.update(meta(chatId), mapOf("updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
            withTimeout(30_000) { batch.commit().await() }
        }
    }

    /** Compare-and-replace: two users moving the same object must not create duplicates. */
    suspend fun replaceObject(chatId: String, expected: CanvasObject, replacement: CanvasObject, expectedGeneration: Long) {
        val uid=requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        require(expected.id != replacement.id)
        val json=CanvasSharedCodec.encodeObject(replacement)
        withTimeout(30_000) {
            firestore.runTransaction { tx ->
                val ref=meta(chatId);val oldRef=objects(chatId).document(expected.id);val newRef=objects(chatId).document(replacement.id)
                val board=tx.get(ref);val old=tx.get(oldRef);val new=tx.get(newRef)
                check(board.exists() && board.getBoolean("deleted")!=true && board.getBoolean("clearing")!=true &&
                    (board.getLong("generation")?:0L)==expectedGeneration) { "Доска была очищена или удалена" }
                check(old.exists() && (old.getLong("generation")?:0L)==expectedGeneration &&
                    old.getString("json")?.let(CanvasSharedCodec::decodeObject)==expected && !new.exists()) {
                    "Объект уже изменён другим участником. Выделите его заново."
                }
                tx.delete(oldRef)
                tx.set(newRef,mapOf("json" to json,"authorUid" to uid,"at" to FieldValue.serverTimestamp(),"generation" to expectedGeneration))
                tx.update(ref,mapOf("updatedAt" to FieldValue.serverTimestamp(),"updatedBy" to uid))
            }.await()
        }
    }

    private data class WriteOp(val add: Pair<String, String>? = null, val remove: String? = null)

    fun describe(e: Throwable): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Нет доступа к общей доске: проверьте, что вы участник чата и что правила Firestore развёрнуты"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> "Нет связи с сервером"
        e is TimeoutCancellationException -> "Сервер не ответил вовремя"
        e is IllegalArgumentException || e is IllegalStateException -> e.message?.take(150) ?: "Ошибка общей доски"
        else -> "Ошибка общей доски: ${e.localizedMessage.orEmpty().take(100)}"
    }
}
