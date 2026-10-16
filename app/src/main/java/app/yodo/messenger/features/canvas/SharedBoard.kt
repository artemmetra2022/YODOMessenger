package app.yodo.messenger.features.canvas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
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
    val updatedAtMs: Long?, val truncated: Boolean
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
        Delta(adds = step.removes.filter { it.id !in presentIds }, removes = step.adds.filter { it.id in presentIds })
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
class SharedBoardRepository @Inject constructor(private val firestore: FirebaseFirestore, private val auth: FirebaseAuth) {
    private fun meta(chatId: String) = firestore.collection("chats").document(chatId).collection("sharedBoard").document("meta")
    private fun objects(chatId: String) = firestore.collection("chats").document(chatId).collection("sharedBoardObjects")

    fun observe(chatId: String): Flow<SharedBoardState> = callbackFlow {
        var metaSnap: DocumentSnapshot? = null
        var objectsSnap: QuerySnapshot? = null
        fun publish() {
            val m = metaSnap ?: return
            if (!m.exists()) { if (!m.metadata.isFromCache) trySend(SharedBoardState.Missing); return }
            val o = objectsSnap ?: return
            trySend(SharedBoardState.Ready(build(chatId, m, o)))
        }
        val r1 = meta(chatId).addSnapshotListener { snap, error ->
            if (error != null) { trySend(SharedBoardState.Failed(describe(error))); return@addSnapshotListener }
            metaSnap = snap; publish()
        }
        val r2 = objects(chatId).orderBy("at").limit(CanvasLimits.MAX_OBJECTS + 1L).addSnapshotListener { snap, error ->
            if (error != null) { trySend(SharedBoardState.Failed(describe(error))); return@addSnapshotListener }
            objectsSnap = snap; publish()
        }
        awaitClose { r1.remove(); r2.remove() }
    }.distinctUntilChanged()

    private fun build(chatId: String, m: DocumentSnapshot, o: QuerySnapshot): SharedBoard {
        val decoded = o.documents.mapNotNull { d ->
            d.getString("json")?.let(CanvasSharedCodec::decodeObject)?.takeIf { it.id == d.id }
        }
        return SharedBoard(
            chatId = chatId, title = m.getString("title")?.take(100)?.takeIf { it.isNotBlank() } ?: "Общая доска",
            grid = m.getString("grid")?.let { runCatching { CanvasGrid.valueOf(it) }.getOrNull() } ?: CanvasGrid.SQUARES,
            objects = decoded.take(CanvasLimits.MAX_OBJECTS),
            updatedAtMs = m.getTimestamp("updatedAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            truncated = o.documents.size > CanvasLimits.MAX_OBJECTS
        )
    }

    suspend fun create(chatId: String, grid: CanvasGrid) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        withTimeout(20_000) {
            meta(chatId).set(mapOf(
                "title" to "Общая доска", "width" to CanvasSharedCodec.WIDTH, "height" to CanvasSharedCodec.HEIGHT, "grid" to grid.name,
                "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid
            )).await()
        }
    }

    suspend fun updateGrid(chatId: String, grid: CanvasGrid) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        withTimeout(20_000) { meta(chatId).update(mapOf("grid" to grid.name, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid)).await() }
    }

    /** Добавляет и удаляет объекты. Записи идут группами ≤ 10: у правил Firestore есть лимит обращений get() на запрос. */
    suspend fun apply(chatId: String, adds: List<CanvasObject>, removes: List<String>) {
        val uid = requireNotNull(auth.currentUser?.uid) { "Войдите в аккаунт" }
        require(adds.isNotEmpty() || removes.isNotEmpty())
        val encoded = adds.map { it.id to CanvasSharedCodec.encodeObject(it) }
        val ops = encoded.map { WriteOp(add = it) } + removes.map { WriteOp(remove = it) }
        val groups = ops.chunked(10)
        groups.forEachIndexed { index, group ->
            val batch = firestore.batch()
            group.forEach { op ->
                op.add?.let { (id, json) -> batch.set(objects(chatId).document(id), mapOf("json" to json, "authorUid" to uid, "at" to FieldValue.serverTimestamp())) }
                op.remove?.let { id -> batch.delete(objects(chatId).document(id)) }
            }
            if (index == groups.lastIndex) batch.update(meta(chatId), mapOf("updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
            withTimeout(30_000) { batch.commit().await() }
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
