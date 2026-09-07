package app.yodo.messenger.data.repository

import app.yodo.messenger.core.crypto.CryptoManager
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.Comment
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.domain.model.Poll
import app.yodo.messenger.domain.model.ScheduledMessage
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val cryptoManager: CryptoManager
) : MessageRepository {

    override fun observeMessages(chatId: String, topicId: String?): Flow<List<Message>> = callbackFlow {
        // п.7: последний успешно отправленный список — отправляется повторно при ошибке
        // листенера, чтобы сбой сети не очищал уже показанные на экране сообщения.
        var lastEmittedMessages: List<Message>? = null
        var query: Query = firestore.collection("chats").document(chatId)
            .collection("messages")
        // НОВОЕ (форумные группы): если открыта конкретная тема — показываем только
        // сообщения этой темы. Общая (нефильтрованная) лента используется, когда
        // тема не выбрана (обычный чат, группа без форума, или список всех сообщений).
        if (topicId != null) {
            query = query.whereEqualTo("topicId", topicId)
        }
        val listener = query
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                // п.7 («вышел из чата и зашёл — сообщений нет»): при ошибке листенера
                // (сбой сети в момент подписки, permission denied при устаревшем токене)
                // НЕ отправляем пустой список — показываем последнее известное состояние,
                // иначе уже загруженные сообщения мгновенно пропадали с экрана.
                if (error != null) { trySend(lastEmittedMessages ?: emptyList<Message>()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> mapDocToMessage(doc, chatId) }
                    // п.38: истёкшие исчезающие сообщения не показываем в UI
                    .filter { msg -> msg.expiresAt == null || msg.expiresAt > System.currentTimeMillis() }
                lastEmittedMessages = messages
                trySend(messages)
                // ИСПРАВЛЕНО (индикатор доставлено): как только чужое сообщение со
                // статусом SENT дошло до нашего устройства (мы получили снапшот не из
                // локального кэша, а подтверждённый сервером), помечаем его DELIVERED.
                // Раньше статус мог перескакивать сразу в READ при входе в чат, минуя
                // "доставлено" — отправитель никогда не видел промежуточное состояние.
                markIncomingMessagesAsDelivered(snapshot)
            }
        awaitClose { listener.remove() }
    }

    /**
     * ИСПРАВЛЕНО (индикатор доставлено): проставляет статус DELIVERED чужим сообщениям,
     * которые дошли до нашего устройства (сервер подтвердил снапшот — hasPendingWrites
     * == false), но всё ещё числятся SENT. Best-effort и не блокирует UI: ошибки просто
     * логируются молча, чат продолжает работать даже если это обновление не удалось.
     */
    private fun markIncomingMessagesAsDelivered(snapshot: QuerySnapshot?) {
        val myUid = firebaseAuth.currentUser?.uid ?: return
        if (snapshot == null || snapshot.metadata.hasPendingWrites()) return
        val toUpdate = snapshot.documents.filter { doc ->
            doc.getString("senderId") != myUid && doc.getString("status") == MessageStatus.SENT.name
        }
        if (toUpdate.isEmpty()) return
        toUpdate.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.update(doc.reference, "status", MessageStatus.DELIVERED.name) }
            // Best-effort: если batch не закоммитится (нет сети, отказ правил и т.п.),
            // просто проглатываем ошибку — это не должно ронять экран чата или ретраить
            // бесконечно, следующий снапшот всё равно попробует снова.
            batch.commit().addOnFailureListener { }
        }
        // ИСПРАВЛЕНО (галочка "доставлено" не показывалась в списке чатов): раньше
        // здесь обновлялся только статус самого документа сообщения — поле
        // chats/{chatId}.lastMessageStatus, которое читает ChatListScreen для
        // отрисовки одной/двух галочек напротив своего последнего сообщения, оставалось
        // "SENT" вплоть до READ. У отправителя в списке чатов галочка "доставлено"
        // никогда не появлялась — сразу перескакивало SENT -> READ (или не менялось
        // совсем, если получатель ещё не открывал чат). Теперь, если только что
        // доставленное сообщение — последнее в чате, синхронно поднимаем и статус чата.
        val chatId = toUpdate.firstOrNull()?.reference?.parent?.parent?.id ?: return
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.get().addOnSuccessListener { chatDoc ->
            val isStillSent = chatDoc.getString("lastMessageStatus") == MessageStatus.SENT.name
            val lastMessageIsTheOneJustDelivered =
                chatDoc.getString("lastMessageSenderId") != myUid
            if (isStillSent && lastMessageIsTheOneJustDelivered) {
                chatRef.update("lastMessageStatus", MessageStatus.DELIVERED.name)
                    .addOnFailureListener { }
            }
        }.addOnFailureListener { }
    }

    // НОВОЕ (переработка каналов): одно сообщение — для превью поста в экране комментариев.
    override fun observeMessage(chatId: String, messageId: String): Flow<Message?> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null); return@addSnapshotListener
                }
                trySend(mapDocToMessage(snapshot, chatId))
            }
        awaitClose { listener.remove() }
    }

    // НОВОЕ: hasTtlOverride/ttlOverrideSeconds — per-message выбор таймера, переопределяющий
    // TTL чата по умолчанию только для этого сообщения (как иконка часов рядом с полем ввода
    // в Telegram). Когда override не задан, поведение полностью совпадает со старым —
    // используется disappearingTtlSeconds из документа чата.
    private suspend fun sendRawMessage(
        chatId: String,
        data: MutableMap<String, Any?>,
        hasTtlOverride: Boolean = false,
        ttlOverrideSeconds: Long? = null,
        silent: Boolean = false,
        topicId: String? = null
    ): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val chatSnapshot = chatRef.get().await()
            val participantIds = (chatSnapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()
            // БАГ-ФИКС («Кто может мне писать» игнорировалось): раньше настройка
            // whoCanMessageMe проверялась ТОЛЬКО при создании нового чата
            // (ChatRepositoryImpl.createOrGetPrivateChat) — если чат уже существовал
            // (например, переписывались раньше, потом сменили настройку на «Никто»),
            // отправлять сообщения в него можно было всегда. Теперь проверяем настройку
            // получателя и при каждой отправке в уже существующий личный (PRIVATE) чат,
            // с учётом списка исключений (messagePrivacyExceptions) — пользователи из
            // исключений могут писать всегда, даже если выбрано «Никто».
            if (chatSnapshot.getString("type") == "PRIVATE" && participantIds.size == 2) {
                val otherUid = participantIds.firstOrNull { it != uid }
                if (otherUid != null) {
                    val otherDoc = firestore.collection("users").document(otherUid).get().await()
                    val who = app.yodo.messenger.domain.model.PrivacyWho.fromString(
                        otherDoc.getString("whoCanMessageMe")
                    )
                    if (who != app.yodo.messenger.domain.model.PrivacyWho.EVERYONE) {
                        val exceptions = otherDoc.get("messagePrivacyExceptions") as? List<*>
                        val isException = exceptions?.contains(uid) == true
                        if (!isException) {
                            val allowedByContacts = who == app.yodo.messenger.domain.model.PrivacyWho.CONTACTS &&
                                run {
                                    val contactIds = (otherDoc.get("contactIds") as? List<*>)
                                        ?.filterIsInstance<String>() ?: emptyList()
                                    if (uid in contactIds) return@run true
                                    val existing = firestore.collection("chats")
                                        .whereArrayContains("participantIds", uid)
                                        .whereEqualTo("type", "PRIVATE")
                                        .get().await()
                                    existing.documents.any { doc ->
                                        doc.id != chatId &&
                                            (doc.get("participantIds") as? List<*>)?.contains(otherUid) == true
                                    }
                                }
                            if (!allowedByContacts) {
                                return SendMessageResult.Error(
                                    if (who == app.yodo.messenger.domain.model.PrivacyWho.NOBODY)
                                        "Пользователь запретил писать ему"
                                    else
                                        "Пользователь принимает личные сообщения только от знакомых"
                                )
                            }
                        }
                    }
                }
            }
            // НОВОЕ (закрытие темы): владелец/админ закрыл раздел — писать в него больше нельзя.
            if (topicId != null) {
                val topicSnapshot = chatRef.collection("topics").document(topicId).get().await()
                if (topicSnapshot.getBoolean("isClosed") == true) {
                    return SendMessageResult.Error("Раздел закрыт для новых сообщений")
                }
            }
            val now = System.currentTimeMillis()
            data["senderId"] = uid
            data["timestamp"] = now
            data["status"] = "SENT"
            topicId?.let { data["topicId"] = it }
            data["notified"] = false
            // НОВОЕ (секретная фича «тихие публикации»): silent-пост не триггерит push
            // (серверная функция уведомлений пропускает notified=true) и не «пикает».
            if (silent) {
                data["silent"] = true
                data["notified"] = true
            }
            // п.38 + переработка per-message: если пользователь явно выбрал таймер для этого
            // сообщения — используем его (в т.ч. явное "выключено"); иначе — TTL чата по умолчанию.
            val ttlSeconds = if (hasTtlOverride) ttlOverrideSeconds else chatSnapshot.getLong("disappearingTtlSeconds")
            if (ttlSeconds != null && ttlSeconds > 0) {
                data["expiresAt"] = now + ttlSeconds * 1000L
            }
            val isEncrypted = data["encrypted"] == true
            // Транзитное поле _plainPreview кладётся tryEncryptTextData перед очисткой
            // текста — здесь забираем его для открытого превью и сразу убираем,
            // чтобы в документ сообщения оно не попало.
            val plainPreview = data.remove("_plainPreview") as? String
            val previewText = if (isEncrypted) "🔒 Сообщение" else (data["text"] as? String)?.takeIf { it.isNotBlank() }
                ?: if (data.containsKey("voiceBase64")) "🎤 Голосовое сообщение"
                else if (data["isViewOnce"] == true) "📷 Фото (один просмотр)"
                else if (data.containsKey("imagesBase64")) "📷 Фото (${(data["imagesBase64"] as? List<*>)?.size ?: 1})"
                else if (data.containsKey("imageBase64")) "📷 Фото"
                else if (data.containsKey("locationLat")) "📍 Геопозиция"
                else if (data.containsKey("fileBase64")) "📎 ${data["fileName"] as? String ?: "Файл"}"
                else ""
            val unreadUpdates = mutableMapOf<String, Any?>(
                "lastMessage" to previewText, "lastMessageTimestamp" to now,
                "lastMessageSenderId" to uid, "lastMessageStatus" to "SENT"
            )
            // Открытое превью для списка чатов: для шифрованных сообщений — реальный
            // текст (транзитный _plainPreview), для обычных lastMessage уже содержит
            // текст и дублировать не нужно.
            if (isEncrypted && !plainPreview.isNullOrBlank()) {
                unreadUpdates["lastMessagePlain"] = plainPreview
            }
            participantIds.filterIsInstance<String>().filter { it != uid }.forEach { otherUid ->
                unreadUpdates["unreadCounts.$otherUid"] = FieldValue.increment(1)
            }
            // ИСПРАВЛЕНО (дубли сообщений при сетевом сбое): раньше документ сообщения
            // создавался отдельным вызовом add(), а превью чата (lastMessage/unreadCounts)
            // и счётчики темы — отдельными update(). Если второй вызов падал при сетевом
            // сбое, пользователь получал ошибку «не удалось отправить», хотя сообщение
            // УЖЕ было сохранено в Firestore — повторная отправка создавала дубликат
            // (и «лишний» unread-инкремент). Теперь все записи идут одним атомарным
            // WriteBatch: либо сообщение и сопутствующие обновления применяются вместе,
            // либо не применяется ничего, и повторная отправка действительно оправдана.
            val newDocRef = chatRef.collection("messages").document()
            unreadUpdates["lastMessageId"] = newDocRef.id
            val topicUpdates: MutableMap<String, Any?>? = if (topicId != null) {
                mutableMapOf<String, Any?>(
                    "lastMessage" to previewText,
                    "lastMessageTimestamp" to now,
                    "lastMessageSenderId" to uid
                ).also { updates ->
                    participantIds.filterIsInstance<String>().filter { it != uid }.forEach { otherUid ->
                        updates["unreadCounts.$otherUid"] = FieldValue.increment(1)
                    }
                }
            } else null

            // БАГ-ФИКС (п.18: лимит «1 активное обращение в поддержку» не срабатывал):
            // раньше проверка hasAwaitingSupportReply() читала документ чата ДО отправки,
            // отдельным вызовом в ViewModel, а сама запись сообщения шла отдельным
            // WriteBatch. Если пользователь отправлял два сообщения почти одновременно
            // (например, быстро нажимал Enter/кнопку отправки дважды), оба запроса
            // читали ещё не обновлённое состояние чата ДО того, как первый успевал
            // закоммититься — классический race condition, оба сообщения проходили
            // проверку и отправлялись. Для чата поддержки (не админом) запись теперь
            // идёт через Firestore-транзакцию: проверка «есть ли уже неотвеченное моё
            // сообщение» и сама запись атомарны относительно друг друга — при гонке
            // одна из транзакций увидит уже обновлённый lastMessageSenderId и будет
            // автоматически повторена (runTransaction), увидит блокировку и завершится ошибкой.
            val isMySupportChatSend = chatId.startsWith("support_") &&
                chatId == "support_$uid" && data["senderId"] != "support_system"
            if (isMySupportChatSend) {
                try {
                    firestore.runTransaction { txn ->
                        val freshChat = txn.get(chatRef)
                        val lastSenderId = freshChat.getString("lastMessageSenderId")
                        val lastMessage = freshChat.getString("lastMessage").orEmpty()
                        if (lastSenderId == uid && lastMessage.isNotBlank()) {
                            throw AwaitingSupportReplyException()
                        }
                        txn.set(newDocRef, data)
                        txn.update(chatRef, unreadUpdates)
                        if (topicId != null && topicUpdates != null) {
                            txn.update(chatRef.collection("topics").document(topicId), topicUpdates)
                        }
                        null
                    }.await()
                } catch (e: AwaitingSupportReplyException) {
                    return SendMessageResult.Error(
                        "У вас уже есть активное обращение в поддержку без ответа. Дождитесь ответа оператора, прежде чем писать снова."
                    )
                }
                return SendMessageResult.Success(messageId = newDocRef.id)
            }

            val batch = firestore.batch()
            batch.set(newDocRef, data)
            batch.update(chatRef, unreadUpdates)
            if (topicId != null && topicUpdates != null) {
                batch.update(chatRef.collection("topics").document(topicId), topicUpdates)
            }
            batch.commit().await()
            SendMessageResult.Success(messageId = newDocRef.id)
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось отправить сообщение")) }
    }

    // Служебное исключение для runTransaction: сигнализирует, что у пользователя уже
    // есть отправленное в поддержку сообщение без ответа оператора (см. sendRawMessage).
    private class AwaitingSupportReplyException : Exception()

    override suspend fun sendMessage(
        chatId: String, text: String, replyTo: ReplyContext?,
        hasTtlOverride: Boolean, ttlOverrideSeconds: Long?, silent: Boolean,
        topicId: String?
    ): SendMessageResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Сообщение не может быть пустым")
        val data = mutableMapOf<String, Any?>("text" to trimmed)
        if (replyTo != null) {
            data["replyToMessageId"] = replyTo.messageId
            data["replyToSenderName"] = replyTo.senderName
            data["replyToText"] = replyTo.text
        }
        topicId?.let { data["topicId"] = it }
        // НОВОЕ (сквозное шифрование): для личных чатов шифруем текст под ключи участников.
        tryEncryptTextData(chatId, data)
        return sendRawMessage(chatId, data, hasTtlOverride, ttlOverrideSeconds, silent, topicId)
    }

    /**
     * НОВОЕ (сквозное шифрование, E2EE). Пытается зашифровать текст сообщения для личного
     * (1-на-1) чата под публичные ключи ОБОИХ участников. При успехе кладёт encByUid
     * (карту uid -> шифртекст base64), флаг encrypted=true и очищает открытый text, чтобы
     * на сервере не оставалось читаемого текста. Если чат не личный, участников не двое или
     * у кого-то нет опубликованного ключа — оставляет обычный текст (плавная деградация).
     */
    private suspend fun tryEncryptTextData(chatId: String, data: MutableMap<String, Any?>) {
        try {
            val text = data["text"] as? String
            if (text.isNullOrEmpty()) return
            val chatSnap = firestore.collection("chats").document(chatId).get().await()
            val type = chatSnap.getString("type")
            // Чаты создаются с "type" to "PRIVATE" (заглавными), поэтому сравнение
            // с "private" строчным регистром отключало E2EE для всех личных чатов.
            if (type != null && !type.equals("private", ignoreCase = true)) return
            val participantIds = (chatSnap.get("participantIds") as? List<*>)
                ?.filterIsInstance<String>() ?: return
            if (participantIds.size != 2) return
            val encMap = cryptoManager.encryptForParticipants(participantIds, text) ?: return
            data["encByUid"] = encMap
            data["encrypted"] = true
            // Транзитное поле для открытого превью в списке чатов: sendRawMessage
            // заберёт его при формировании lastMessagePlain и удалит из data.
            data["_plainPreview"] = text.take(120)
            data["text"] = ""
        } catch (e: Exception) {
            android.util.Log.w("MessageRepositoryImpl", "tryEncryptTextData failed; sending plaintext", e)
        }
    }

    override suspend fun sendImageMessage(
        chatId: String, imageBase64: String, caption: String, isViewOnce: Boolean,
        topicId: String?
    ): SendMessageResult {
        val data = mutableMapOf<String, Any?>("imageBase64" to imageBase64, "text" to caption.trim())
        if (isViewOnce) {
            data["isViewOnce"] = true
            data["viewOnceOpened"] = false
        }
        return sendRawMessage(chatId, data, topicId = topicId)
    }

    // НОВОЕ (несколько фото): все выбранные фото сохраняются одним массивом
    // imagesBase64 в одном документе — так они отображаются как единое сообщение-альбом,
    // а не несколько отдельных сообщений подряд.
    override suspend fun sendImagesMessage(
        chatId: String, imagesBase64: List<String>, caption: String, topicId: String?
    ): SendMessageResult {
        val images = imagesBase64.filter { it.isNotBlank() }
        if (images.isEmpty()) return SendMessageResult.Error("Нет фото для отправки")
        // Одно фото — отправляем как обычное imageBase64 (совместимость со старыми клиентами).
        if (images.size == 1) {
            return sendRawMessage(
                chatId,
                mutableMapOf("imageBase64" to images.first(), "text" to caption.trim()),
                topicId = topicId
            )
        }
        val data = mutableMapOf<String, Any?>(
            "imagesBase64" to images,
            // Для старых клиентов без поддержки альбомов показываем хотя бы первое фото.
            "imageBase64" to images.first(),
            "text" to caption.trim()
        )
        return sendRawMessage(chatId, data, topicId = topicId)
    }

    // НОВОЕ (одноразовые медиа): удаляем imageBase64 из документа в Firestore, как только
    // получатель открыл view-once фото полноэкранно — так фото реально стирается на сервере,
    // а не просто прячется в UI (иначе оно осталось бы читаемым при повторном открытии чата
    // или с другого устройства). senderId не трогаем, обновление разрешено правилами и для
    // получателя (см. firestore.rules — отдельное условие для view-once полей).
    override suspend fun markViewOnceImageOpened(chatId: String, messageId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "imageBase64" to FieldValue.delete(),
                        "viewOnceOpened" to true
                    )
                ).await()
        } catch (_: Exception) {
            // Если не получилось стереть на сервере (например, нет сети) — просто не открываем
            // повторно локально; при следующем успешном подключении получатель может повторить
            // попытку, открыв фото ещё раз (idempotent: повторный delete/true ничего не ломает).
        }
    }

    override suspend fun sendVoiceMessage(chatId: String, voiceBase64: String, durationMs: Long, topicId: String?): SendMessageResult {
        val data = mutableMapOf<String, Any?>(
            "text" to "",
            "voiceBase64" to voiceBase64,
            "voiceDurationMs" to durationMs
        )
        return sendRawMessage(chatId, data, topicId = topicId)
    }

    override suspend fun sendFileMessage(
        chatId: String, fileBase64: String, fileName: String, mimeType: String, sizeBytes: Long,
        topicId: String?
    ): SendMessageResult {
        val data = mutableMapOf<String, Any?>(
            "text" to "",
            "fileBase64" to fileBase64,
            "fileName" to fileName,
            "fileMimeType" to mimeType,
            "fileSizeBytes" to sizeBytes
        )
        return sendRawMessage(chatId, data, topicId = topicId)
    }

    override suspend fun sendLocationMessage(chatId: String, lat: Double, lng: Double, topicId: String?): SendMessageResult {
        val data = mutableMapOf<String, Any?>(
            "text" to "",
            "locationLat" to lat,
            "locationLng" to lng
        )
        return sendRawMessage(chatId, data, topicId = topicId)
    }

    // НОВОЕ (расширенные опросы): опрос хранится как вложенная карта poll внутри документа
    // сообщения — так же, как остальные типы вложений (fileBase64, locationLat и т.д.),
    // чтобы не заводить отдельную подколлекцию только ради него.
    override suspend fun sendPollMessage(
        chatId: String,
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowMultipleAnswers: Boolean,
        closesAtMillis: Long?,
        isQuiz: Boolean,
        correctOptionIndex: Int?,
        explanation: String?,
        topicId: String?
    ): SendMessageResult {
        val trimmedQuestion = question.trim()
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmedQuestion.isEmpty()) return SendMessageResult.Error("Введите вопрос опроса")
        if (cleanOptions.size < 2) return SendMessageResult.Error("Добавьте минимум 2 варианта ответа")
        if (cleanOptions.size > 10) return SendMessageResult.Error("Максимум 10 вариантов ответа")
        if (isQuiz && (correctOptionIndex == null || correctOptionIndex !in cleanOptions.indices)) {
            return SendMessageResult.Error("Укажите правильный вариант ответа для викторины")
        }

        val pollMap = mutableMapOf<String, Any?>(
            "question" to trimmedQuestion,
            "options" to cleanOptions,
            "votesByOption" to emptyMap<String, List<String>>(),
            "isAnonymous" to isAnonymous,
            "allowMultipleAnswers" to allowMultipleAnswers,
            "isClosed" to false,
            "isQuiz" to isQuiz
        )
        closesAtMillis?.let { pollMap["closesAt"] = it }
        if (isQuiz) {
            correctOptionIndex?.let { pollMap["correctOptionIndex"] = it }
            explanation?.trim()?.takeIf { it.isNotEmpty() }?.let { pollMap["explanation"] = it }
        }

        val data = mutableMapOf<String, Any?>(
            "text" to "",
            "poll" to pollMap
        )
        return sendRawMessage(chatId, data, topicId = topicId)
    }

    // НОВОЕ (расширенные опросы): голосование хранится по индексам вариантов, чтобы
    // избежать проблем с одинаковыми/изменёнными текстами вариантов (как votesByOption
    // в модели Poll). Транзакция гарантирует атомарность при одновременном голосовании.
    override suspend fun voteOnPoll(chatId: String, messageId: String, optionIndex: Int): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val pollRaw = snapshot.get("poll") as? Map<*, *>
                    ?: throw IllegalStateException("Это сообщение не является опросом")

                val isClosed = pollRaw["isClosed"] as? Boolean ?: false
                val closesAt = (pollRaw["closesAt"] as? Number)?.toLong()
                val now = System.currentTimeMillis()
                if (isClosed || (closesAt != null && closesAt <= now)) {
                    throw IllegalStateException("Голосование завершено")
                }

                val allowMultiple = pollRaw["allowMultipleAnswers"] as? Boolean ?: false
                val optionsCount = (pollRaw["options"] as? List<*>)?.size ?: 0
                if (optionIndex !in 0 until optionsCount) {
                    throw IllegalStateException("Такого варианта ответа не существует")
                }

                @Suppress("UNCHECKED_CAST")
                val votesRaw = (pollRaw["votesByOption"] as? Map<String, List<String>>) ?: emptyMap()
                val updatedVotes = votesRaw.mapValues { it.value.toMutableList() }.toMutableMap()

                val key = optionIndex.toString()
                val currentUids = updatedVotes[key] ?: mutableListOf()
                if (uid in currentUids) {
                    // Повторный тап по уже выбранному варианту — снять голос.
                    currentUids.remove(uid)
                    updatedVotes[key] = currentUids
                } else {
                    if (!allowMultiple) {
                        // Одиночный выбор: убрать голос пользователя из всех остальных вариантов.
                        updatedVotes.keys.toList().forEach { otherKey ->
                            updatedVotes[otherKey] = (updatedVotes[otherKey] ?: mutableListOf())
                                .filterNot { it == uid }.toMutableList()
                        }
                    }
                    currentUids.add(uid)
                    updatedVotes[key] = currentUids
                }

                transaction.update(messageRef, "poll.votesByOption", updatedVotes)
            }.await()
            SendMessageResult.Success()
        } catch (e: Exception) {
            SendMessageResult.Error(e.toUserMessage(e.message ?: "Не удалось проголосовать"))
        }
    }

    // НОВОЕ (расширенные опросы): досрочное закрытие опроса (например, автором сообщения).
    override suspend fun closePoll(chatId: String, messageId: String): SendMessageResult {
        return try {
            val ref = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            ref.update("poll.isClosed", true).await()
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось закрыть опрос")) }
    }

    // НОВОЕ (расширенные опросы): парсинг вложенной карты poll в доменную модель Poll.
    private fun mapDocToPoll(doc: DocumentSnapshot): Poll? {
        val pollRaw = doc.get("poll") as? Map<*, *> ?: return null
        return try {
            val question = pollRaw["question"] as? String ?: return null
            val options = (pollRaw["options"] as? List<*>)?.filterIsInstance<String>() ?: return null
            val votesRaw = pollRaw["votesByOption"] as? Map<*, *>
            val votesByOption = votesRaw?.mapNotNull { (key, value) ->
                val optionIndex = (key as? String)?.toIntOrNull() ?: return@mapNotNull null
                val uids = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                optionIndex to uids
            }?.toMap() ?: emptyMap()
            val correctOptionIndex = (pollRaw["correctOptionIndex"] as? Number)?.toInt()
                ?.takeIf { it in options.indices }
            Poll(
                question = question,
                options = options,
                votesByOption = votesByOption,
                isAnonymous = pollRaw["isAnonymous"] as? Boolean ?: true,
                allowMultipleAnswers = pollRaw["allowMultipleAnswers"] as? Boolean ?: false,
                isClosed = pollRaw["isClosed"] as? Boolean ?: false,
                closesAt = (pollRaw["closesAt"] as? Number)?.toLong(),
                isQuiz = pollRaw["isQuiz"] as? Boolean ?: false,
                correctOptionIndex = correctOptionIndex,
                explanation = pollRaw["explanation"] as? String
            )
        } catch (e: Exception) { null }
    }

    override suspend fun scheduleMessage(
        chatId: String, text: String, scheduledFor: Long,
        imageBase64: String?, replyTo: ReplyContext?
    ): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        val trimmed = text.trim()
        if (trimmed.isEmpty() && imageBase64 == null) return SendMessageResult.Error("Сообщение не может быть пустым")
        return try {
            val data = mutableMapOf<String, Any?>(
                "senderId" to uid,
                "text" to trimmed,
                "scheduledFor" to scheduledFor,
                "createdAt" to System.currentTimeMillis()
            )
            imageBase64?.let { data["imageBase64"] = it }
            if (replyTo != null) {
                data["replyToMessageId"] = replyTo.messageId
                data["replyToSenderName"] = replyTo.senderName
                data["replyToText"] = replyTo.text
            }
            firestore.collection("chats").document(chatId)
                .collection("scheduledMessages").add(data).await()
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось запланировать сообщение")) }
    }

    override fun observeScheduledMessages(chatId: String): Flow<List<ScheduledMessage>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("scheduledMessages")
            .orderBy("scheduledFor", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        ScheduledMessage(
                            id = doc.id, chatId = chatId,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            text = doc.getString("text") ?: "",
                            imageBase64 = doc.getString("imageBase64"),
                            replyToMessageId = doc.getString("replyToMessageId"),
                            replyToSenderName = doc.getString("replyToSenderName"),
                            replyToText = doc.getString("replyToText"),
                            scheduledFor = doc.getLong("scheduledFor") ?: 0L,
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun cancelScheduledMessage(chatId: String, scheduledMessageId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("scheduledMessages").document(scheduledMessageId).delete().await()
        } catch (e: Exception) { }
    }

    override suspend fun editScheduledMessage(chatId: String, scheduledMessageId: String, newText: String, newScheduledFor: Long) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("scheduledMessages").document(scheduledMessageId)
                .update(mapOf("text" to newText.trim(), "scheduledFor" to newScheduledFor)).await()
        } catch (e: Exception) { }
    }

    override suspend fun publishDueScheduledMessages(chatId: String) {
        try {
            val now = System.currentTimeMillis()
            val dueRef = firestore.collection("chats").document(chatId).collection("scheduledMessages")
            val due = dueRef.whereLessThanOrEqualTo("scheduledFor", now).get().await()
            if (due.isEmpty) return
            due.documents.forEach { doc ->
                val data = mutableMapOf<String, Any?>("text" to (doc.getString("text") ?: ""))
                doc.getString("imageBase64")?.let { data["imageBase64"] = it }
                doc.getString("replyToMessageId")?.let { data["replyToMessageId"] = it }
                doc.getString("replyToSenderName")?.let { data["replyToSenderName"] = it }
                doc.getString("replyToText")?.let { data["replyToText"] = it }
                sendRawMessage(chatId, data)
                doc.reference.delete().await()
            }
        } catch (e: Exception) { }
    }

    override suspend fun forwardMessage(
        targetChatId: String,
        originalMessage: Message,
        fromSenderName: String,
        fromSenderId: String,
        fromSenderPhotoUrl: String?,
        fromSenderAvatarBase64: String?
    ): SendMessageResult {
        // НОВОЕ (одноразовые медиа): view-once фото пересылать нельзя — иначе пересланная
        // копия превратилась бы в обычное, сколько угодно раз открываемое изображение,
        // что противоречит смыслу "один просмотр".
        if (originalMessage.isViewOnce) {
            return SendMessageResult.Error("Фото «на один просмотр» нельзя переслать")
        }
        val data = mutableMapOf<String, Any?>(
            "text" to originalMessage.text,
            "forwardedFromSenderName" to fromSenderName,
            "forwardedFromSenderId" to fromSenderId
        )
        fromSenderPhotoUrl?.let { data["forwardedFromSenderPhotoUrl"] = it }
        fromSenderAvatarBase64?.let { data["forwardedFromSenderAvatarBase64"] = it }
        if (originalMessage.imagesBase64.isNotEmpty()) {
            data["imagesBase64"] = originalMessage.imagesBase64
        }
        originalMessage.imageBase64?.let { data["imageBase64"] = it }
        originalMessage.fileBase64?.let {
            data["fileBase64"] = it
            data["fileName"] = originalMessage.fileName
            data["fileMimeType"] = originalMessage.fileMimeType
            data["fileSizeBytes"] = originalMessage.fileSizeBytes
        }
        if (originalMessage.locationLat != null && originalMessage.locationLng != null) {
            data["locationLat"] = originalMessage.locationLat
            data["locationLng"] = originalMessage.locationLng
        }
        return sendRawMessage(targetChatId, data)
    }

    override suspend fun editMessage(chatId: String, messageId: String, newText: String): SendMessageResult {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Сообщение не может быть пустым")
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(mapOf("text" to trimmed, "isEdited" to true)).await()
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось отредактировать")) }
    }

    override suspend fun deleteMessage(chatId: String, messageId: String, deletedByAdmin: Boolean): SendMessageResult {
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(mapOf(
                    "isDeleted" to true, "text" to "",
                    // НОВОЕ (баг 10): административное удаление помечаем отдельным флагом,
                    // чтобы в чате показать «Сообщение удалено администратором».
                    "deletedByAdmin" to deletedByAdmin,
                    "imageBase64" to FieldValue.delete(),
                    "fileBase64" to FieldValue.delete(),
                    "fileName" to FieldValue.delete(),
                    "fileMimeType" to FieldValue.delete(),
                    "fileSizeBytes" to FieldValue.delete(),
                    "locationLat" to FieldValue.delete(),
                    "locationLng" to FieldValue.delete()
                )).await()
            // ИСПРАВЛЕНО (превью в списке чатов «врёт» после удаления): раньше удаление
            // чистило только сам документ сообщения, а chats/{chatId}.lastMessage — то, что
            // реально читает список чатов под именем собеседника, — не трогалось вообще.
            // Если удаляли именно последнее сообщение чата, старый текст так и оставался
            // висеть в превью. Теперь после удаления пересчитываем lastMessage, если
            // удалённое сообщение было последним.
            refreshLastMessagePreviewIfNeeded(chatId, listOf(messageId))
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось удалить")) }
    }

    // НОВОЕ (баг 10): «тихое удаление» — документ сообщения удаляется из Firestore целиком.
    // Snapshot-listener у всех участников мгновенно убирает сообщение из ленты, поэтому
    // на его месте не остаётся заглушки «удалено» — сообщение просто исчезает.
    override suspend fun hardDeleteMessage(chatId: String, messageId: String): SendMessageResult {
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .delete().await()
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось удалить")) }
    }

    override suspend fun deleteMessages(chatId: String, messageIds: List<String>, deletedByAdmin: Boolean): SendMessageResult {
        if (messageIds.isEmpty()) return SendMessageResult.Success()
        return try {
            val refs = messageIds.map { id ->
                firestore.collection("chats").document(chatId).collection("messages").document(id)
            }
            refs.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { ref ->
                    batch.update(ref, mapOf(
                        "isDeleted" to true,
                        "text" to "",
                        // НОВОЕ (баг 10): как и в deleteMessage — флаг административного удаления.
                        "deletedByAdmin" to deletedByAdmin,
                        "imageBase64" to FieldValue.delete(),
                        "fileBase64" to FieldValue.delete(),
                        "fileName" to FieldValue.delete(),
                        "fileMimeType" to FieldValue.delete(),
                        "fileSizeBytes" to FieldValue.delete(),
                        "locationLat" to FieldValue.delete(),
                        "locationLng" to FieldValue.delete()
                    ))
                }
                batch.commit().await()
            }
            // ИСПРАВЛЕНО (превью в списке чатов «врёт» после удаления): см. deleteMessage —
            // то же самое при массовом удалении.
            refreshLastMessagePreviewIfNeeded(chatId, messageIds)
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось удалить сообщения")) }
    }

    /**
     * ИСПРАВЛЕНО (превью в списке чатов «врёт» после удаления): если среди только что
     * удалённых сообщений было текущее последнее сообщение чата (chats/{chatId}.lastMessage*),
     * пересчитывает превью по самому новому из оставшихся неудалённых сообщений — иначе
     * список чатов продолжал бы показывать текст уже удалённого сообщения. Если сообщений
     * не осталось вовсе — очищает превью. Best-effort: ошибка здесь не должна превращать
     * уже совершённое удаление сообщения в ошибку для пользователя.
     */
    private suspend fun refreshLastMessagePreviewIfNeeded(chatId: String, deletedMessageIds: List<String>) {
        if (deletedMessageIds.isEmpty()) return
        try {
            val chatRef = firestore.collection("chats").document(chatId)
            val chatSnapshot = chatRef.get().await()
            // lastMessageId у старых документов чата может отсутствовать (поле новое,
            // появляется только у чатов, где хотя бы одно сообщение отправлено уже после
            // этого фикса) — тогда не можем точно знать, было ли удалено именно последнее
            // сообщение. В этом случае просто пересчитываем безусловно: лишний пересчёт
            // безопасен и в худшем случае просто ставит то же самое превью, что уже
            // показано, а не может испортить существующее корректное превью.
            val lastMessageId = chatSnapshot.getString("lastMessageId")
            val wasLastMessageDeleted = lastMessageId != null && lastMessageId in deletedMessageIds
            if (lastMessageId != null && !wasLastMessageDeleted) {
                return
            }
            // ИСПРАВЛЕНО: whereEqualTo("isDeleted", false) не находит сообщения, у которых
            // поля isDeleted вообще нет в документе (оно проставляется только при удалении,
            // а не при отправке) — Firestore не матчит отсутствующее поле никаким whereEqualTo,
            // поэтому такой запрос почти всегда возвращал пусто. Берём несколько последних по
            // времени и сами отфильтровываем удалённые на клиенте.
            val latestRemaining = chatRef.collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get().await()
                .documents.firstOrNull { it.getBoolean("isDeleted") != true }
            val updates = if (latestRemaining == null) {
                mapOf<String, Any?>(
                    "lastMessage" to "",
                    "lastMessagePlain" to FieldValue.delete(),
                    "lastMessageSenderId" to null,
                    "lastMessageStatus" to null,
                    "lastMessageId" to null
                )
            } else {
                val isEncrypted = latestRemaining.getBoolean("encrypted") ?: false
                val text = latestRemaining.getString("text")?.takeIf { it.isNotBlank() }
                val previewText = when {
                    isEncrypted -> "🔒 Сообщение"
                    text != null -> text
                    latestRemaining.get("voiceBase64") != null -> "🎤 Голосовое сообщение"
                    latestRemaining.getBoolean("isViewOnce") == true -> "📷 Фото (один просмотр)"
                    latestRemaining.get("imagesBase64") != null -> {
                        val count = (latestRemaining.get("imagesBase64") as? List<*>)?.size ?: 1
                        "📷 Фото ($count)"
                    }
                    latestRemaining.get("imageBase64") != null -> "📷 Фото"
                    latestRemaining.get("locationLat") != null -> "📍 Геопозиция"
                    latestRemaining.get("fileBase64") != null -> "📎 ${latestRemaining.getString("fileName") ?: "Файл"}"
                    else -> ""
                }
                mapOf(
                    "lastMessage" to previewText,
                    "lastMessagePlain" to FieldValue.delete(),
                    "lastMessageTimestamp" to (latestRemaining.getLong("timestamp") ?: 0L),
                    "lastMessageSenderId" to latestRemaining.getString("senderId"),
                    "lastMessageStatus" to (latestRemaining.getString("status") ?: "SENT"),
                    "lastMessageId" to latestRemaining.id
                )
            }
            chatRef.update(updates).await()
        } catch (e: Exception) {
            // Best-effort: если пересчёт не удался (нет сети, отказ правил и т.п.), само
            // удаление сообщения уже применилось — не превращаем это в ошибку пользователю.
        }
    }

    override suspend fun markChatAsRead(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val showReadReceipts = userSettingsPreferences.showReadReceipts.first()
            val chatRef = firestore.collection("chats").document(chatId)
            val messagesRef = chatRef.collection("messages")
            // whereNotEqualTo требует составного индекса — заменяем на фильтрацию
            // в памяти: загружаем SENT- и DELIVERED-сообщения (статусы до READ),
            // затем отбираем чужие. Читаем это всегда (не только при showReadReceipts),
            // так как ниже используем этот же список, чтобы синхронизировать unreadCounts
            // без гонки (см. комментарий ниже) — при выключенных read receipts переход
            // статусов в READ просто не выполняется, но список для подсчёта нужен всё равно.
            val sentSnapshot = messagesRef.whereEqualTo("status", "SENT").get().await()
            val deliveredSnapshot = messagesRef.whereEqualTo("status", "DELIVERED").get().await()
            val unreadDocs = (sentSnapshot.documents + deliveredSnapshot.documents)
                .filter { it.getString("senderId") != uid }
            if (showReadReceipts) {
                // ИСПРАВЛЕНО: раньше проверялся только "SENT" — сообщения, уже помеченные
                // DELIVERED, не переходили в READ при открытии чата. Batch делим по 500,
                // чтобы не превысить лимит Firestore.
                if (unreadDocs.isNotEmpty()) {
                    unreadDocs.chunked(500).forEach { chunk ->
                        val batch = firestore.batch()
                        chunk.forEach { doc -> batch.update(doc.reference, "status", "READ") }
                        batch.commit().await()
                    }
                }
                val chatSnapshot = chatRef.get().await()
                if (chatSnapshot.getString("lastMessageSenderId") != uid) {
                    chatRef.update("lastMessageStatus", "READ").await()
                }
            }
            // ФИКС (залипание бейджа при двух сообщениях подряд с разницей ~15 сек):
            // раньше здесь стояло безусловное chatRef.update("unreadCounts.$uid", 0).
            // Это гонка с отправителем: когда приходит первое сообщение, наш клиент
            // (уже открывший чат) тут же запускает этот suspend-вызов и в конце обнуляет
            // счётчик. Если пока он выполняется (сеть, несколько await() выше) отправитель
            // шлёт второе сообщение — на сервере выполняется unreadCounts.$uid =
            // increment(1) для него. Порядок применения двух конкурентных записей на
            // сервере Firestore не гарантирован: если increment(1) применится ПОСЛЕ нашей
            // записи 0, счётчик останется равным 1, хотя оба сообщения уже показаны и
            // прочитаны. А поскольку это изменение поля самого чата, а не нового документа
            // в /messages, слушатель observeMessages() на него не реагирует и повторно
            // markAsRead() не вызывает — бейдж "залипает" до выхода из чата и входа заново.
            // Решение: вместо безусловной записи 0 сверяем unreadCounts с реальным числом
            // чужих непрочитанных сообщений (unreadDocs, уже посчитанным выше — если
            // showReadReceipts включён, это ровно то, что мы только что пометили READ).
            // Если по факту чужих непрочитанных нет — обнуляем, даже если серверный
            // счётчик "уполз" выше из-за гонки с increment(1) отправителя. Если же
            // непрочитанные остались (пришло новое сообщение уже после чтения выше) —
            // подтягиваем счётчик к их реальному числу вместо того, чтобы оставлять
            // завышенное "залипшее" значение; свежее сообщение при этом всё равно придёт
            // отдельным snapshot'ом в observeMessages() и вызовет markAsRead() ещё раз.
            val stillUnreadCount = unreadDocs.size
            firestore.runTransaction { tx ->
                val snap = tx.get(chatRef)
                val unreadMap = snap.get("unreadCounts") as? Map<*, *>
                val current = (unreadMap?.get(uid) as? Long)?.toInt() ?: 0
                if (stillUnreadCount == 0) {
                    // Все чужие сообщения, которые видели выше, действительно прочитаны —
                    // безопасно обнулить, даже если current сейчас больше (это и есть
                    // симптом гонки, который мы чиним).
                    if (current != 0) tx.update(chatRef, "unreadCounts.$uid", 0)
                } else if (current > stillUnreadCount) {
                    // Появилось новое сообщение уже после нашей проверки выше (retry
                    // логика в observeMessages его подхватит и вызовет markAsRead ещё
                    // раз) — но раз старый счётчик явно завышен относительно того, что
                    // мы только что нашли, подтягиваем его к реальному значению вместо
                    // того чтобы оставлять "залипшим".
                    tx.update(chatRef, "unreadCounts.$uid", stillUnreadCount)
                }
            }.await()
        } catch (e: Exception) { }
    }

    // НОВОЕ (F3 статистика постов канала): регистрируем уникальный просмотр поста.
    // viewedBy — множество uid просмотревших, viewCount — денормализованный счётчик.
    // Разрешено любому авторизованному пользователю (правила ограничивают набор полей).
    override suspend fun registerPostView(chatId: String, messageId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val viewedBy = (snapshot.get("viewedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                if (uid !in viewedBy) {
                    transaction.update(
                        messageRef,
                        mapOf(
                            "viewedBy" to FieldValue.arrayUnion(uid),
                            "viewCount" to FieldValue.increment(1)
                        )
                    )
                }
                null
            }.await()
        } catch (e: Exception) { }
    }

    override suspend fun toggleReaction(chatId: String, messageId: String, emoji: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val reactionsRaw = snapshot.get("reactions") as? Map<*, *>
                val currentUids = (reactionsRaw?.get(emoji) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val updatedUids = if (uid in currentUids) currentUids - uid else currentUids + uid
                transaction.update(messageRef, "reactions.$emoji", updatedUids)
            }.await()
        } catch (e: Exception) { }
    }

    override suspend fun togglePinMessage(chatId: String, messageId: String): SendMessageResult {
        return try {
            val ref = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            val doc = ref.get().await()
            val isPinned = doc.getBoolean("isPinned") ?: false
            ref.update("isPinned", !isPinned).await()
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось закрепить")) }
    }

    override fun observePinnedMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages")
            .whereEqualTo("isPinned", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList<Message>()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        Message(
                            id = doc.id, chatId = chatId,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            status = MessageStatus.SENT, isPinned = true
                        )
                    } catch (e: Exception) { null }
                }.sortedByDescending { it.timestamp }
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleBookmark(messageId: String, chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val bookmarkRef = firestore.collection("users").document(uid)
                .collection("bookmarks").document(messageId)
            val doc = bookmarkRef.get().await()
            if (doc.exists()) {
                bookmarkRef.delete().await()
            } else {
                val msgDoc = firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId).get().await()
                // НОВОЕ (одноразовые медиа): не сохраняем imageBase64 view-once сообщения в
                // избранное — это была бы постоянная копия того, что должно исчезнуть после
                // одного просмотра.
                val isViewOnceMsg = msgDoc.getBoolean("isViewOnce") ?: false
                bookmarkRef.set(mapOf(
                    "messageId" to messageId, "chatId" to chatId,
                    "senderId" to (msgDoc.getString("senderId") ?: ""),
                    "text" to (msgDoc.getString("text") ?: ""),
                    "timestamp" to (msgDoc.getLong("timestamp") ?: 0L),
                    "imageBase64" to if (isViewOnceMsg) null else msgDoc.getString("imageBase64"),
                    "savedAt" to System.currentTimeMillis()
                )).await()
            }
        } catch (e: Exception) { }
    }

    override fun observeBookmarkedMessages(): Flow<List<Message>> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) { trySend(emptyList<Message>()); close(); return@callbackFlow }
        val listener = firestore.collection("users").document(uid)
            .collection("bookmarks")
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList<Message>()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        Message(
                            id = doc.getString("messageId") ?: doc.id,
                            chatId = doc.getString("chatId") ?: "",
                            senderId = doc.getString("senderId") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            status = MessageStatus.SENT,
                            imageBase64 = doc.getString("imageBase64")
                        )
                    } catch (e: Exception) { null }
                }
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun deleteExpiredMessages(chatId: String) {
        try {
            val now = System.currentTimeMillis()
            val messagesRef = firestore.collection("chats").document(chatId).collection("messages")
            val expired = messagesRef.whereLessThan("expiresAt", now).get().await()
            if (expired.isEmpty) return
            // Firestore batch ограничен 500 операциями — делим на чанки.
            expired.documents.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
        } catch (e: Exception) { }
    }

    override suspend fun exportChatHistory(chatId: String): String {
        return try {
            val snapshot = firestore.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING).get().await()
            val sb = StringBuilder()
            sb.appendLine("=== YODOMessenger — Экспорт чата ===")
            sb.appendLine("Дата экспорта: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date())}")
            sb.appendLine("=====================================")
            sb.appendLine()
            snapshot.documents.forEach { doc ->
                val senderId = doc.getString("senderId") ?: "?"
                val text = doc.getString("text") ?: ""
                val timestamp = doc.getLong("timestamp") ?: 0L
                val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru")).format(Date(timestamp))
                val hasImage = doc.getString("imageBase64") != null
                val fileName = doc.getString("fileName")
                val hasLocation = doc.getDouble("locationLat") != null
                sb.appendLine("[$time] $senderId:")
                if (text.isNotBlank()) sb.appendLine("  $text")
                if (hasImage) sb.appendLine("  [📷 Фото]")
                if (fileName != null) sb.appendLine("  [📎 Файл: $fileName]")
                if (hasLocation) sb.appendLine("  [📍 Геопозиция]")
                sb.appendLine()
            }
            sb.toString()
        } catch (e: Exception) { e.toUserMessage("Не удалось экспортировать чат") }
    }

    // ═══════════════ НОВОЕ (переработка каналов): комментарии к постам ═══════════════

    override fun observeComments(chatId: String, messageId: String): Flow<List<Comment>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val comments = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        Comment(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            senderName = doc.getString("senderName") ?: "Пользователь",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
                trySend(comments)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun addComment(chatId: String, messageId: String, text: String): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Комментарий не может быть пустым")
        return try {
            // НОВОЕ (настройки канала: кто может писать комментарии): раньше здесь не
            // было никакой проверки allowComments/commentPermission вообще — переключатель
            // в настройках канала был чисто декоративным, комментировать мог кто угодно.
            // Теперь проверяем и общий флаг, и (если комментарии включены) круг лиц.
            val chatRef = firestore.collection("chats").document(chatId)
            val chatDoc = chatRef.get().await()
            val allowComments = chatDoc.getBoolean("allowComments") ?: true
            if (!allowComments) {
                return SendMessageResult.Error("Комментарии к этому каналу отключены")
            }
            val commentPermission = app.yodo.messenger.domain.model.CommentPermission
                .fromRaw(chatDoc.getString("commentPermission"))
            val ownerId = chatDoc.getString("createdBy")
            val adminIds = (chatDoc.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val participantIds = (chatDoc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val isAdmin = uid == ownerId || uid in adminIds
            val isSubscriber = uid in participantIds
            val allowed = when (commentPermission) {
                app.yodo.messenger.domain.model.CommentPermission.EVERYONE -> true
                app.yodo.messenger.domain.model.CommentPermission.SUBSCRIBERS_ONLY -> isSubscriber || isAdmin
                app.yodo.messenger.domain.model.CommentPermission.ADMINS_ONLY -> isAdmin
            }
            if (!allowed) {
                val reason = when (commentPermission) {
                    app.yodo.messenger.domain.model.CommentPermission.SUBSCRIBERS_ONLY ->
                        "Комментировать могут только подписчики канала"
                    app.yodo.messenger.domain.model.CommentPermission.ADMINS_ONLY ->
                        "Комментировать могут только администраторы канала"
                    else -> "Недостаточно прав для комментирования"
                }
                return SendMessageResult.Error(reason)
            }
            val myDoc = firestore.collection("users").document(uid).get().await()
            val senderName = myDoc.getString("displayName")?.takeIf { it.isNotBlank() } ?: "Пользователь"
            val messageRef = chatRef
                .collection("messages").document(messageId)
            messageRef.collection("comments").add(
                mapOf(
                    "senderId" to uid,
                    "senderName" to senderName,
                    "text" to trimmed,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            // Счётчик на самом посте — для бейджа "Комментарии · N" в ленте канала
            messageRef.update("commentsCount", FieldValue.increment(1)).await()
            SendMessageResult.Success()
        } catch (e: Exception) {
            SendMessageResult.Error(e.toUserMessage("Не удалось отправить комментарий"))
        }
    }

    override suspend fun deleteComment(chatId: String, messageId: String, commentId: String): SendMessageResult {
        return try {
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            messageRef.collection("comments").document(commentId).delete().await()
            messageRef.update("commentsCount", FieldValue.increment(-1)).await()
            SendMessageResult.Success()
        } catch (e: Exception) {
            SendMessageResult.Error(e.toUserMessage("Не удалось удалить комментарий"))
        }
    }

    override suspend fun countMessages(chatId: String): Int {
        return try {
            val snapshot = firestore.collection("chats").document(chatId)
                .collection("messages")
                .count()
                .get(AggregateSource.SERVER)
                .await()
            snapshot.count.toInt()
        } catch (e: Exception) { 0 }
    }

    override suspend fun getRecentMessages(chatId: String, limit: Int): List<Message> {
        return try {
            val snapshot = firestore.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get().await()
            snapshot.documents.mapNotNull { mapDocToMessage(it, chatId) }.reversed()
        } catch (e: Exception) { emptyList() }
    }

    /** НОВОЕ: общий маппинг документа в Message — используется в observeMessages,
     *  observeMessage и getRecentMessages, чтобы не дублировать разбор полей. */
    /**
     * НОВОЕ (сквозное шифрование, E2EE). Возвращает отображаемый текст сообщения. Если оно
     * зашифровано (encrypted=true), достаёт наш шифртекст из encByUid по нашему uid и
     * расшифровывает локальным приватным ключом. Плейсхолдеры — если ключа/данных нет.
     */
    private fun decryptMessageText(doc: DocumentSnapshot): String {
        val encrypted = doc.getBoolean("encrypted") ?: false
        if (!encrypted) return doc.getString("text") ?: ""
        val myUid = firebaseAuth.currentUser?.uid
        val encMap = doc.get("encByUid") as? Map<*, *>
        val cipher = encMap?.get(myUid) as? String ?: return "🔒 Зашифрованное сообщение"
        return cryptoManager.decrypt(cipher) ?: "🔒 Не удалось расшифровать"
    }

    private fun mapDocToMessage(doc: DocumentSnapshot, chatId: String): Message? {
        return try {
            val reactionsRaw = doc.get("reactions") as? Map<*, *>
            val reactions = reactionsRaw?.mapNotNull { (key, value) ->
                val emoji = key as? String ?: return@mapNotNull null
                val uids = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                emoji to uids
            }?.toMap() ?: emptyMap()
            Message(
                id = doc.id, chatId = chatId,
                senderId = doc.getString("senderId") ?: return null,
                topicId = doc.getString("topicId"),
                text = decryptMessageText(doc),
                timestamp = doc.getLong("timestamp") ?: 0L,
                // ИСПРАВЛЕНО (индикатор прочитано/доставлено "врёт"): раньше статус брался
                // прямо из поля "status" без учёта hasPendingWrites, поэтому оптимистично
                // созданное локальное сообщение могло на мгновение показать неверную
                // (устаревшую из кэша) галочку. Теперь решение централизовано в
                // resolveMessageStatus и явно учитывает, подтверждена ли запись сервером.
                status = resolveMessageStatus(doc.getString("status"), doc.metadata.hasPendingWrites()),
                replyToMessageId = doc.getString("replyToMessageId"),
                replyToSenderName = doc.getString("replyToSenderName"),
                replyToText = doc.getString("replyToText"),
                reactions = reactions,
                imageBase64 = doc.getString("imageBase64"),
                imagesBase64 = (doc.get("imagesBase64") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                isEdited = doc.getBoolean("isEdited") ?: false,
                isDeleted = doc.getBoolean("isDeleted") ?: false,
                deletedByAdmin = doc.getBoolean("deletedByAdmin") ?: false,
                forwardedFromSenderName = doc.getString("forwardedFromSenderName"),
                forwardedFromSenderId = doc.getString("forwardedFromSenderId"),
                forwardedFromSenderPhotoUrl = doc.getString("forwardedFromSenderPhotoUrl"),
                forwardedFromSenderAvatarBase64 = doc.getString("forwardedFromSenderAvatarBase64"),
                isPinned = doc.getBoolean("isPinned") ?: false,
                expiresAt = doc.getLong("expiresAt"),
                voiceBase64 = doc.getString("voiceBase64"),
                voiceDurationMs = doc.getLong("voiceDurationMs"),
                fileBase64 = doc.getString("fileBase64"),
                fileName = doc.getString("fileName"),
                fileMimeType = doc.getString("fileMimeType"),
                fileSizeBytes = doc.getLong("fileSizeBytes"),
                locationLat = doc.getDouble("locationLat"),
                locationLng = doc.getDouble("locationLng"),
                commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                poll = mapDocToPoll(doc),
                isViewOnce = doc.getBoolean("isViewOnce") ?: false,
                viewOnceOpened = doc.getBoolean("viewOnceOpened") ?: false,
                viewCount = (doc.getLong("viewCount") ?: 0L).toInt()
            )
        } catch (e: Exception) { null }
    }
}