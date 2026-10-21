package app.yodo.messenger.data.repository

import app.yodo.messenger.core.crypto.CryptoManager
import app.yodo.messenger.core.crypto.ProtectedTextPayload
import app.yodo.messenger.core.crypto.SecureMessagingException
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.UnreadTail
import app.yodo.messenger.domain.model.UnreadBadgeSnapshot
import app.yodo.messenger.domain.model.normalizedUnreadCount
import app.yodo.messenger.domain.model.remainingUnreadAfterTopicRead
import app.yodo.messenger.domain.model.Comment
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.domain.model.SharedChecklist
import app.yodo.messenger.domain.model.Poll
import app.yodo.messenger.domain.model.ScheduledMessage
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.delay
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
    private val receiptScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val receiptInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun observeMessages(chatId: String, topicId: String?, limit: Int?): Flow<List<Message>> = callbackFlow {
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
        // НОВОЕ (пагинация истории): с limit слушаем только последние N сообщений —
        // без него Firestore читал всю историю чата при каждом открытии и на каждое
        // изменение. Слушатель отдаёт их по возрастанию, как и раньше, чтобы UI
        // не пришлось менять порядок отрисовки.
        val pageSize = limit?.takeIf { it > 0 }
        val paged = pageSize != null
        query = if (paged) {
            query.orderBy("timestamp", Query.Direction.DESCENDING).limit(pageSize!!.toLong())
        } else {
            query.orderBy("timestamp", Query.Direction.ASCENDING)
        }
        val listener = query
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                // п.7 («вышел из чата и зашёл — сообщений нет»): при ошибке листенера
                // (сбой сети в момент подписки, permission denied при устаревшем токене)
                // НЕ отправляем пустой список — показываем последнее известное состояние,
                // иначе уже загруженные сообщения мгновенно пропадали с экрана.
                if (error != null) { trySend(lastEmittedMessages ?: emptyList<Message>()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> mapDocToMessage(doc, chatId) }
                    // п.38: истёкшие исчезающие сообщения не показываем в UI
                    .filter { msg -> msg.expiresAt == null || msg.expiresAt > System.currentTimeMillis() }
                    // пейджинг читает историю в обратном порядке — возвращаем хронологию
                    .let { if (paged) it.sortedBy { msg -> msg.timestamp } else it }
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
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (snapshot == null || snapshot.metadata.isFromCache) return
        snapshot.documents.filter { doc ->
            doc.getString("senderId") != uid && !doc.metadata.hasPendingWrites() &&
                if (doc.contains("recipientIds")) uid !in (doc.get("deliveredTo") as? List<*>).orEmpty()
                else doc.getString("status") == "SENT"
        }.forEach { doc ->
            val key = doc.reference.path + ":" + uid
            if (receiptInFlight.add(key)) receiptScope.launch {
                try { updateReceipts(doc.reference.parent.parent!!.id, listOf(doc.id), false, null) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Retry on the next server snapshot / opening. */ }
                finally { receiptInFlight.remove(key) }
            }
        }
    }

    // НОВОЕ (переработка каналов): одно сообщение — для превью поста в экране комментариев.
    override fun observeMessage(chatId: String, messageId: String): Flow<Message?> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
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
        topicId: String? = null,
        clientMessageId: String? = null
    ): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val chatSnapshot = chatRef.get().await()
            val participantIds = (chatSnapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()
            protectTextData(chatId, data, chatSnapshot)
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
            if (firebaseAuth.currentUser?.uid != uid) throw SecureMessagingException("Аккаунт изменился. Сообщение не отправлено")
            data["senderId"] = uid
            data["timestamp"] = now
            data["status"] = "SENT"
            data["sentAt"] = FieldValue.serverTimestamp()
            data["recipientIds"] = participantIds.filter { it != uid }
            data["deliveredTo"] = emptyList<String>()
            data["readBy"] = emptyList<String>()
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
            val previewText = if (isEncrypted) "🔒 Сообщение" else (data["text"] as? String)?.takeIf { it.isNotBlank() }
                ?: if (data.containsKey("checklistTitle")) "☑ ${data["checklistTitle"]}"
                else if (data.containsKey("voiceBase64")) "🎤 Голосовое сообщение"
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
            // Legacy plaintext previews are removed, never replaced with message content.
            unreadUpdates["lastMessagePlain"] = FieldValue.delete()
            if (isProtectedPrivate(chatId, chatSnapshot.getString("type"))) {
                unreadUpdates["lastMessage"] = if (isEncrypted) "🔒 Сообщение" else "Личное сообщение"
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
            val newDocRef = clientMessageId?.let { chatRef.collection("messages").document(it) }
                ?: chatRef.collection("messages").document()
            unreadUpdates["lastMessageId"] = newDocRef.id
            val topicUpdates: MutableMap<String, Any?>? = if (topicId != null) {
                mutableMapOf<String, Any?>(
                    "lastMessage" to previewText,
                    "lastMessageTimestamp" to now,
                    "lastMessageSenderId" to uid,
                    "lastMessageId" to newDocRef.id
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
            if (isMySupportChatSend || clientMessageId != null) {
                try {
                    firestore.runTransaction { txn ->
                        val freshChat = txn.get(chatRef)
                        val existing = txn.get(newDocRef)
                        if (existing.exists()) {
                            check(existing.getString("senderId") == uid) { "ID сообщения уже занят" }
                            return@runTransaction null
                        }
                        val topicRef = topicId?.let { chatRef.collection("topics").document(it) }
                        val freshTopic = topicRef?.let { txn.get(it) }
                        check(freshTopic?.getBoolean("isClosed") != true) { "Раздел закрыт для новых сообщений" }
                        check(firebaseAuth.currentUser?.uid == uid) { "Аккаунт изменился" }
                        val lastSenderId = freshChat.getString("lastMessageSenderId")
                        val lastMessage = freshChat.getString("lastMessage").orEmpty()
                        if (isMySupportChatSend && lastSenderId == uid && lastMessage.isNotBlank()) {
                            throw AwaitingSupportReplyException()
                        }
                        txn.set(newDocRef, data)
                        val currentChatUpdates = if ((freshChat.getLong("lastMessageTimestamp") ?: 0L) > now)
                            unreadUpdates.filterKeys { it.startsWith("unreadCounts.") } else unreadUpdates
                        txn.update(chatRef, currentChatUpdates)
                        if (topicRef != null && topicUpdates != null) {
                            val currentTopicUpdates = if ((freshTopic?.getLong("lastMessageTimestamp") ?: 0L) > now)
                                topicUpdates.filterKeys { it.startsWith("unreadCounts.") } else topicUpdates
                            txn.update(topicRef, currentTopicUpdates)
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
            if (firebaseAuth.currentUser?.uid != uid) throw SecureMessagingException("Аккаунт изменился. Сообщение не отправлено")
            batch.commit().await()
            SendMessageResult.Success(messageId = newDocRef.id)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось отправить сообщение")) }
    }

    // Служебное исключение для runTransaction: сигнализирует, что у пользователя уже
    // есть отправленное в поддержку сообщение без ответа оператора (см. sendRawMessage).
    private class AwaitingSupportReplyException : Exception()

    override suspend fun sendMessage(
        chatId: String, text: String, replyTo: ReplyContext?,
        hasTtlOverride: Boolean, ttlOverrideSeconds: Long?, silent: Boolean,
        topicId: String?, clientMessageId: String?
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
        // All send paths are protected centrally by sendRawMessage, including captions and forwards.
        val result = sendRawMessage(chatId, data, hasTtlOverride, ttlOverrideSeconds, silent, topicId, clientMessageId)
        // НОВОЕ (автофильтр сообщений): если текст совпал с правилом из
        // config/autoModeration — сразу удаляем отправленное сообщение и пишем
        // запись в историю удалённых. Проверяем открытый текст ДО шифрования,
        // поэтому фильтр работает и в личных (E2EE) чатах.
        if (result is SendMessageResult.Success) {
            applyAutoFilter(chatId, result.messageId, trimmed)
        }
        return result
    }

    private fun isProtectedPrivate(chatId: String, type: String?): Boolean =
        type.equals("PRIVATE", ignoreCase = true) && !chatId.startsWith("saved_") && !chatId.startsWith("support_")

    /** Fail closed. Only an explicit group/channel/self/support scope may keep plaintext text. */
    private suspend fun protectTextData(chatId: String, data: MutableMap<String, Any?>, chat: DocumentSnapshot) {
        val type = chat.getString("type")
        if (chatId.startsWith("saved_") || chatId.startsWith("support_")) return
        if (!chat.exists() || type.isNullOrBlank()) throw SecureMessagingException("Не удалось подтвердить тип чата. Сообщение не отправлено")
        if (!isProtectedPrivate(chatId, type)) {
            if (type.uppercase() !in listOf("GROUP", "CHANNEL")) throw SecureMessagingException("Неизвестный тип чата. Сообщение не отправлено")
            return
        }
        val ids = (chat.get("participantIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val owner = firebaseAuth.currentUser?.uid ?: throw SecureMessagingException("Войдите в аккаунт")
        if (ids.distinct().size != 2 || owner !in ids) throw SecureMessagingException("Не удалось подтвердить участников личного чата")
        val text = data["text"] as? String ?: ""
        val replyText = data["replyToText"] as? String
        val replyName = data["replyToSenderName"] as? String
        if (text.isEmpty() && replyText.isNullOrEmpty() && replyName.isNullOrEmpty()) return
        val encrypted = cryptoManager.encryptForParticipants(ids, ProtectedTextPayload(text, replyText, replyName).encode())
        data["encByUid"] = encrypted
        data["encrypted"] = true
        data["encryptedPayloadVersion"] = 2
        data["text"] = ""
        data.remove("replyToText")
        data.remove("replyToSenderName")
    }

    private fun decryptedPayload(doc: DocumentSnapshot): ProtectedTextPayload? {
        if (doc.getBoolean("encrypted") != true) {
            return ProtectedTextPayload(doc.getString("text") ?: "", doc.getString("replyToText"), doc.getString("replyToSenderName"))
        }
        val uid = firebaseAuth.currentUser?.uid ?: return null
        val cipher = (doc.get("encByUid") as? Map<*, *>)?.get(uid) as? String ?: return null
        val raw = cryptoManager.decrypt(cipher) ?: return null
        return runCatching {
            val version = (doc.getLong("encryptedPayloadVersion") ?: 1L).toInt()
            val decoded = ProtectedTextPayload.decode(raw, version)
            if (version == 1) decoded.copy(replyText = doc.getString("replyToText"), replySenderName = doc.getString("replyToSenderName")) else decoded
        }.getOrNull()
    }

    /** Best effort cleanup of a legacy server preview, without deleting message history. */
    override suspend fun clearLegacyPrivatePreview(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val ref = firestore.collection("chats").document(chatId)
        val doc = ref.get(Source.SERVER).await()
        if (isProtectedPrivate(chatId, doc.getString("type")) && uid in (doc.get("participantIds") as? List<*>).orEmpty()) {
            ref.update(mapOf("lastMessagePlain" to FieldValue.delete(), "lastMessage" to "Личное сообщение")).await()
        }
    }

    override suspend fun sendChecklistMessage(
        chatId: String, title: String, items: List<String>, topicId: String?
    ): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        val checklist = runCatching { SharedChecklist.create(title, items) }.getOrNull()
            ?: return SendMessageResult.Error("Укажите название и от 1 до 20 непустых пунктов")
        return try {
            val chat = firestore.collection("chats").document(chatId).get().await()
            if (chat.getString("type") != "GROUP" || uid !in (chat.get("participantIds") as? List<*>).orEmpty()) {
                return SendMessageResult.Error("Совместные списки доступны участникам группы")
            }
            sendRawMessage(chatId, mutableMapOf<String, Any?>(
                "text" to "", "checklistTitle" to checklist.title,
                "checklistItems" to checklist.items, "checklistCheckedBy" to emptyMap<String, String>()
            ), topicId = topicId)
        } catch (e: Exception) {
            SendMessageResult.Error(e.toUserMessage("Не удалось отправить список"))
        }
    }

    override suspend fun setChecklistItemChecked(
        chatId: String, messageId: String, itemIndex: Int, checked: Boolean
    ): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val ref = chatRef.collection("messages").document(messageId)
            // Desired state, not a blind toggle: transaction retries cannot undo the user's action.
            firestore.runTransaction { txn ->
                val chat = txn.get(chatRef)
                val doc = txn.get(ref)
                check(chat.getString("type") == "GROUP" && uid in (chat.get("participantIds") as? List<*>).orEmpty())
                check(doc.exists() && doc.getBoolean("isDeleted") != true)
                check((doc.getLong("expiresAt") ?: Long.MAX_VALUE) > System.currentTimeMillis())
                val checklist = SharedChecklist.fromFields(doc.get("checklistTitle"), doc.get("checklistItems"), doc.get("checklistCheckedBy"))
                    ?: error("Список недоступен")
                require(itemIndex in checklist.items.indices)
                doc.getString("topicId")?.let { topic ->
                    check(txn.get(chatRef.collection("topics").document(topic)).getBoolean("isClosed") != true)
                }
                if ((checklist.completedBy(itemIndex) != null) != checked) {
                    // Only this item changes; concurrent changes to other items survive.
                    txn.update(ref,
                        FieldPath.of("checklistCheckedBy", itemIndex.toString()), if (checked) uid else "",
                        FieldPath.of("checklistLastItemIndex"), itemIndex,
                        FieldPath.of("checklistUpdatedBy"), uid,
                        FieldPath.of("checklistUpdatedAt"), FieldValue.serverTimestamp()
                    )
                }
            }.await()
            SendMessageResult.Success(messageId)
        } catch (e: Exception) {
            SendMessageResult.Error(e.toUserMessage("Не удалось изменить пункт. Проверьте связь и права в группе"))
        }
    }

    override suspend fun sendCanvasSnapshot(chatId: String, imageBase64: String, title: String, localId: String, topicId: String?): SendMessageResult {
        if (!app.yodo.messenger.features.canvas.CanvasLimits.validId(localId) || title.isBlank() || title.length > 100 || imageBase64.length !in 1..650000) {
            return SendMessageResult.Error("Некорректная доска или слишком большое превью")
        }
        return sendRawMessage(chatId, mutableMapOf<String, Any?>("imageBase64" to imageBase64, "text" to "🎨 ${title.trim()}", "canvasLocalId" to localId, "canvasVersion" to 1), topicId = topicId)
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
            val chat = firestore.collection("chats").document(chatId).get(Source.SERVER).await()
            protectTextData(chatId, data, chat)
            firestore.collection("chats").document(chatId)
                .collection("scheduledMessages").add(data).await()
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось запланировать сообщение")) }
    }

    override fun observeScheduledMessages(chatId: String): Flow<List<ScheduledMessage>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("scheduledMessages")
            .whereEqualTo("senderId", firebaseAuth.currentUser?.uid ?: "__signed_out__")
            .orderBy("scheduledFor", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        val payload = decryptedPayload(doc)
                        ScheduledMessage(
                            id = doc.id, chatId = chatId,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            text = payload?.text ?: "🔒 Не удалось расшифровать",
                            imageBase64 = doc.getString("imageBase64"),
                            replyToMessageId = doc.getString("replyToMessageId"),
                            replyToSenderName = payload?.replySenderName,
                            replyToText = payload?.replyText,
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
        if (newText.isBlank()) throw SecureMessagingException("Отредактированный текст не может быть пустым")
        val chatRef = firestore.collection("chats").document(chatId)
        val ref = chatRef.collection("scheduledMessages").document(scheduledMessageId)
        val original = ref.get(Source.SERVER).await()
        val uid = firebaseAuth.currentUser?.uid ?: throw SecureMessagingException("Войдите в аккаунт")
        if (original.getString("senderId") != uid) throw SecureMessagingException("Нет доступа к отложенному сообщению")
        val payload = decryptedPayload(original) ?: throw SecureMessagingException("Отложенное сообщение не расшифровано")
        val data = mutableMapOf<String, Any?>("text" to newText.trim(), "scheduledFor" to newScheduledFor)
        payload.replyText?.let { data["replyToText"] = it }
        payload.replySenderName?.let { data["replyToSenderName"] = it }
        protectTextData(chatId, data, chatRef.get(Source.SERVER).await())
        if (data["encrypted"] == true) {
            data["replyToText"] = FieldValue.delete()
            data["replyToSenderName"] = FieldValue.delete()
        }
        ref.update(data).await()
    }

    override suspend fun publishDueScheduledMessages(chatId: String) {
        try {
            val uid = firebaseAuth.currentUser?.uid ?: return
            val dueRef = firestore.collection("chats").document(chatId).collection("scheduledMessages")
            val due = dueRef.whereEqualTo("senderId", uid).whereLessThanOrEqualTo("scheduledFor", System.currentTimeMillis()).get().await()
            due.documents.forEach { doc ->
                val payload = decryptedPayload(doc) ?: return@forEach
                val data = mutableMapOf<String, Any?>("text" to payload.text)
                doc.getString("imageBase64")?.let { data["imageBase64"] = it }
                doc.getString("replyToMessageId")?.let { data["replyToMessageId"] = it }
                payload.replySenderName?.let { data["replyToSenderName"] = it }
                payload.replyText?.let { data["replyToText"] = it }
                // Fresh keys are rechecked by the central send path. Failed sends retain the schedule.
                if (sendRawMessage(chatId, data) is SendMessageResult.Success) doc.reference.delete().await()
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Keep unsent documents; never silently downgrade or delete them. */ }
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
        if (originalMessage.textDecryptionFailed) return SendMessageResult.Error("Сообщение не расшифровано — пересылка остановлена")
        if (originalMessage.isViewOnce) {
            return SendMessageResult.Error("Фото «на один просмотр» нельзя переслать")
        }
        // Forward a checklist as a read-only snapshot, never as a linked editable copy.
        val forwardedText = originalMessage.checklist?.let { checklist ->
            buildString {
                appendLine(checklist.title)
                checklist.items.forEachIndexed { index, label ->
                    appendLine("${if (checklist.completedBy(index) != null) "[x]" else "[ ]"} $label")
                }
            }.trimEnd()
        } ?: originalMessage.text
        val data = mutableMapOf<String, Any?>(
            "text" to forwardedText,
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
            val chatRef = firestore.collection("chats").document(chatId)
            val ref = chatRef.collection("messages").document(messageId)
            val original = ref.get(Source.SERVER).await()
            val uid = firebaseAuth.currentUser?.uid ?: throw SecureMessagingException("Войдите в аккаунт")
            if (original.getString("senderId") != uid || original.getBoolean("isDeleted") == true) {
                return SendMessageResult.Error("Это сообщение нельзя редактировать")
            }
            val payload = decryptedPayload(original) ?: throw SecureMessagingException("Исходное сообщение не расшифровано. Редактирование остановлено")
            val data = mutableMapOf<String, Any?>("text" to trimmed, "isEdited" to true)
            payload.replyText?.let { data["replyToText"] = it }
            payload.replySenderName?.let { data["replyToSenderName"] = it }
            val chat = chatRef.get(Source.SERVER).await()
            protectTextData(chatId, data, chat)
            if (data["encrypted"] == true) {
                data["replyToText"] = FieldValue.delete()
                data["replyToSenderName"] = FieldValue.delete()
            }
            ref.update(data).await()
            if (isProtectedPrivate(chatId, chat.getString("type"))) {
                try { clearLegacyPrivatePreview(chatId) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* The edit already succeeded; cleanup can be retried later. */ }
            }
            SendMessageResult.Success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось отредактировать")) }
    }

    override suspend fun deleteMessage(chatId: String, messageId: String, deletedByAdmin: Boolean): SendMessageResult {
        return try {
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            // НОВОЕ (история удалённых сообщений): сохраняем копию сообщения в
            // архив (deletedMessages) ДО мягкого удаления — иначе текст и медиа
            // будут уже стёрты и восстановить их из веб-панели не получится.
            val snapshot = messageRef.get().await()
            if (snapshot.exists()) {
                archiveDeletedMessage(
                    chatId, messageId, snapshot,
                    reason = if (deletedByAdmin) "RULES" else "OTHER",
                    archiveSource = "ANDROID"
                )
            }
            messageRef.update(softDeleteFields(deletedByAdmin)).await()
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
            // НОВОЕ (история удалённых сообщений): архивируем каждое сообщение
            // перед мягким удалением (best-effort — сбой архива не ломает удаление).
            // whereIn по documentId — пачками по 10 (лимит whereIn), чтобы не
            // делать по одному чтению на сообщение.
            val messagesCol = firestore.collection("chats").document(chatId).collection("messages")
            messageIds.chunked(10).forEach { chunk ->
                val snapshots = try {
                    messagesCol.whereIn(FieldPath.documentId(), chunk).get().await()
                } catch (e: Exception) {
                    null
                }
                snapshots?.documents?.filter { it.exists() }?.forEach { snap ->
                    archiveDeletedMessage(
                        chatId, snap.id, snap,
                        reason = if (deletedByAdmin) "RULES" else "OTHER",
                        archiveSource = "ANDROID"
                    )
                }
            }
            refs.chunked(500).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { ref ->
                    batch.update(ref, softDeleteFields(deletedByAdmin))
                }
                batch.commit().await()
            }
            // ИСПРАВЛЕНО (превью в списке чатов «врёт» после удаления): см. deleteMessage —
            // то же самое при массовом удалении.
            refreshLastMessagePreviewIfNeeded(chatId, messageIds)
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось удалить сообщения")) }
    }

    // НОВОЕ (причины удаления + автофильтр): поля «мягкого» удаления. Один и тот
    // же набор используют точечное, массовое и автоматическое удаление — 1:1 с
    // web/admin.js softDeletePayload. deletedByAdmin=true в чате показывается как
    // «Сообщение удалено администратором» (НОВОЕ, баг 10).
    private fun softDeleteFields(deletedByAdmin: Boolean): Map<String, Any?> = mapOf(
        "isDeleted" to true,
        "text" to "",
        "deletedByAdmin" to deletedByAdmin,
        "imageBase64" to FieldValue.delete(),
        "fileBase64" to FieldValue.delete(),
        "fileName" to FieldValue.delete(),
        "fileMimeType" to FieldValue.delete(),
        "fileSizeBytes" to FieldValue.delete(),
        "locationLat" to FieldValue.delete(),
        "locationLng" to FieldValue.delete()
    )

    /**
     * НОВОЕ (история удалённых сообщений): сохраняет копию сообщения в корневую
     * коллекцию deletedMessages ПЕРЕД мягким удалением, чтобы админ мог
     * восстановить его из веб-панели в течение 30 дней. Формат 1:1 с
     * web/admin.js (deletedEntry / pickOriginalFields): preview, originalText,
     * media{...}, reason, reasonText, source, deletedBy, deletedAt, restored.
     * Best-effort: сбой архива не должен превращать уже совершённое удаление в ошибку.
     */
    private suspend fun archiveDeletedMessage(
        chatId: String,
        messageId: String,
        snapshot: DocumentSnapshot,
        reason: String,
        archiveSource: String
    ) {
        try {
            val uid = firebaseAuth.currentUser?.uid ?: return
            val m = snapshot.data ?: return
            val preview = when {
                m["encrypted"] == true -> "🔒 Сообщение"
                (m["text"] as? String)?.isNotBlank() == true -> m["text"] as String
                m["voiceBase64"] != null -> "🎤 Голосовое сообщение"
                m["isViewOnce"] == true -> "📷 Фото (один просмотр)"
                m["imagesBase64"] != null -> "📷 Фото (${(m["imagesBase64"] as? List<*>)?.size ?: 1})"
                m["imageBase64"] != null -> "📷 Фото"
                m["locationLat"] != null -> "📍 Геопозиция"
                m["fileBase64"] != null -> "📎 ${m["fileName"] ?: "Файл"}"
                else -> ""
            }
            val media = mutableMapOf<String, Any?>()
            listOf(
                "imageBase64", "fileBase64", "fileName", "fileMimeType",
                "fileSizeBytes", "locationLat", "locationLng"
            ).forEach { key -> m[key]?.let { media[key] = it } }
            val entry = mutableMapOf<String, Any?>(
                "chatId" to chatId,
                "messageId" to messageId,
                "senderId" to (m["senderId"] ?: ""),
                "preview" to preview,
                "originalText" to ((m["text"] as? String) ?: ""),
                "reason" to reason,
                "reasonText" to "",
                "source" to archiveSource,
                "deletedBy" to uid,
                "deletedAt" to System.currentTimeMillis(),
                "restored" to false
            )
            // Медиа (base64) может не уместиться в лимит документа Firestore (~1 МиБ):
            // тогда сохраняем только текст, а факт пропуска помечаем mediaSkipped.
            if (media.isNotEmpty()) {
                val weight = media.values.sumOf { if (it is String) it.length else 16 }
                if (weight < 700_000) entry["media"] = media else entry["mediaSkipped"] = true
            }
            firestore.collection("deletedMessages").add(entry).await()
        } catch (e: Exception) {
            android.util.Log.w("MessageRepositoryImpl", "archiveDeletedMessage failed", e)
        }
    }

    // НОВОЕ (автофильтр сообщений): правила из config/autoModeration, кэш ~1 минута,
    // чтобы не читать документ при каждой отправке.
    private data class AutoFilterRule(val type: String, val pattern: String, val reason: String)
    private data class AutoFilterConfig(val enabled: Boolean, val rules: List<AutoFilterRule>)

    @Volatile private var autoFilterCache: AutoFilterConfig? = null
    @Volatile private var autoFilterCacheAt: Long = 0L

    private suspend fun loadAutoFilterConfig(): AutoFilterConfig {
        val now = System.currentTimeMillis()
        autoFilterCache?.let { if (now - autoFilterCacheAt < 60_000) return it }
        return try {
            val snap = firestore.collection("config").document("autoModeration").get().await()
            val raw = snap.get("rules") as? List<*> ?: emptyList<Any?>()
            val rules = raw.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                if (map["enabled"] == false) return@mapNotNull null
                val pattern = (map["pattern"] as? String)?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                AutoFilterRule(
                    type = (map["type"] as? String) ?: "LINK",
                    pattern = pattern,
                    reason = (map["reason"] as? String) ?: "RULES"
                )
            }
            AutoFilterConfig(snap.getBoolean("enabled") == true, rules)
                .also { autoFilterCache = it; autoFilterCacheAt = now }
        } catch (e: Exception) {
            autoFilterCache ?: AutoFilterConfig(false, emptyList())
        }
    }

    /** Семантика совпадения 1:1 с web/admin.js ruleMatches. */
    private fun ruleMatches(rule: AutoFilterRule, text: String): Boolean = try {
        when (rule.type.uppercase()) {
            "REGEX" -> Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
            "DOMAIN" -> {
                val hosts = Regex("(?:https?://)?([a-z0-9-]+(?:\\.[a-z0-9-]+)+)", RegexOption.IGNORE_CASE)
                    .findAll(text).map { it.groupValues[1].lowercase() }
                val pattern = rule.pattern.lowercase()
                hosts.any { it == pattern || it.endsWith(".$pattern") || it.contains(pattern) }
            }
            else -> text.contains(rule.pattern, ignoreCase = true)
        }
    } catch (e: Exception) { false }

    /**
     * НОВОЕ (автофильтр сообщений): если включён config/autoModeration и только что
     * отправленный текст совпал с правилом — тут же мягко удаляем это сообщение и
     * пишем запись в историю удалённых (source = AUTO). Работает на клиенте
     * отправителя, поэтому фильтруются и личные (E2EE) чаты: проверяем открытый
     * текст до шифрования. Удалять чужие сообщения нельзя — только своё.
     */
    private suspend fun applyAutoFilter(chatId: String, messageId: String?, text: String) {
        if (messageId.isNullOrBlank() || text.isBlank()) return
        try {
            val config = loadAutoFilterConfig()
            if (!config.enabled || config.rules.isEmpty()) return
            val rule = config.rules.firstOrNull { ruleMatches(it, text) } ?: return
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            val snapshot = messageRef.get().await()
            if (snapshot.exists()) {
                archiveDeletedMessage(chatId, messageId, snapshot, reason = rule.reason, archiveSource = "AUTO")
            }
            messageRef.update(softDeleteFields(deletedByAdmin = false)).await()
            refreshLastMessagePreviewIfNeeded(chatId, listOf(messageId))
        } catch (e: Exception) {
            android.util.Log.w("MessageRepositoryImpl", "applyAutoFilter failed", e)
        }
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
            val safeUpdates = updates.toMutableMap()
            if (isProtectedPrivate(chatId, chatSnapshot.getString("type")) && latestRemaining != null) {
                safeUpdates["lastMessage"] = if (latestRemaining.getBoolean("encrypted") == true) "🔒 Сообщение" else "Личное сообщение"
            }
            chatRef.update(safeUpdates).await()
        } catch (e: Exception) {
            // Best-effort: если пересчёт не удался (нет сети, отказ правил и т.п.), само
            // удаление сообщения уже применилось — не превращаем это в ошибку пользователю.
        }
    }

    private fun unreadTail(doc: DocumentSnapshot, uid: String) = UnreadTail(
        count = normalizedUnreadCount((doc.get("unreadCounts") as? Map<*, *>)?.get(uid)),
        lastMessageId = doc.getString("lastMessageId"),
        timestamp = doc.getLong("lastMessageTimestamp") ?: 0L,
        senderId = doc.getString("lastMessageSenderId"))

    override fun observeUnreadBadge(chatId: String, topicId: String?): Flow<UnreadBadgeSnapshot> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val chatRef = firestore.collection("chats").document(chatId)
        var chat: UnreadTail? = null
        var topic: UnreadTail? = null
        fun emit() {
            val current = chat ?: return
            if (firebaseAuth.currentUser?.uid == uid && (topicId == null || topic != null))
                trySend(UnreadBadgeSnapshot(current, topic))
        }
        val chatListener = chatRef.addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (doc != null && error == null) { chat = unreadTail(doc, uid); emit() }
        }
        val topicListener = topicId?.let { id ->
            chatRef.collection("topics").document(id).addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
                if (doc != null && error == null) { topic = unreadTail(doc, uid); emit() }
            }
        }
        awaitClose { chatListener.remove(); topicListener?.remove() }
    }

    override suspend fun clearUnreadForVisibleMessages(chatId: String, visibleIds: List<String>, topicId: String?): Boolean =
        clearUnreadScoped(chatId,visibleIds,topicId,null)

    private suspend fun clearUnreadScoped(chatId: String, visibleIds: List<String>, topicId: String?, expectedAccount: String?): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        check(expectedAccount == null || expectedAccount == uid) { "Аккаунт изменился" }
        if (visibleIds.isEmpty()) return false
        val chatRef = firestore.collection("chats").document(chatId)
        return firestore.runTransaction { tx ->
            check(firebaseAuth.currentUser?.uid == uid) { "Аккаунт изменился" }
            val chat = tx.get(chatRef)
            if (!chat.exists()) return@runTransaction false
            val topicRef = topicId?.let { chatRef.collection("topics").document(it) }
            val topic = topicRef?.let { tx.get(it) }
            val target = topic ?: chat
            if (!target.exists()) return@runTransaction false
            val tail = unreadTail(target, uid)
            if (tail.count == 0) return@runTransaction true
            val tailMatches = if (!tail.lastMessageId.isNullOrBlank()) tail.lastMessageId in visibleIds else {
                // Legacy fallback verifies real visible documents, not a client timestamp claim.
                visibleIds.takeLast(8).map { tx.get(chatRef.collection("messages").document(it)) }.any {
                    it.exists() && tail.timestamp > 0 && it.getLong("timestamp") == tail.timestamp &&
                        (tail.senderId == null || it.getString("senderId") == tail.senderId) &&
                        (topicId == null || it.getString("topicId") == topicId)
                }
            }
            if (!tailMatches) return@runTransaction false
            if (topicRef == null) {
                tx.update(chatRef, FieldPath.of("unreadCounts", uid), 0)
            } else {
                tx.update(topicRef, FieldPath.of("unreadCounts", uid), 0)
                val remaining = remainingUnreadAfterTopicRead(unreadTail(chat, uid).count, tail.count)
                tx.update(chatRef, FieldPath.of("unreadCounts", uid), remaining)
            }
            true
        }.await()
    }

    // Explicit notification action: unlike viewport receipts, this intentionally reads the whole chat.
    override suspend fun markChatAsRead(chatId: String, expectedAccount: String?) {
        val uid=firebaseAuth.currentUser?.uid ?: return
        check(expectedAccount==null || expectedAccount==uid) { "Аккаунт изменился" }
        val docs = firestore.collection("chats").document(chatId).collection("messages").get(Source.SERVER).await()
        check(firebaseAuth.currentUser?.uid==uid) { "Аккаунт изменился" }
        val ids = docs.documents.map { it.id }
        try { updateReceipts(chatId,ids,userSettingsPreferences.showReadReceipts.first(),null,uid) }
        finally { if(firebaseAuth.currentUser?.uid==uid)clearUnreadScoped(chatId,ids,null,uid) }
    }

    override suspend fun markMessagesAsRead(chatId: String, messageIds: List<String>, topicId: String?) {
        updateReceipts(chatId, messageIds, userSettingsPreferences.showReadReceipts.first(), topicId)
    }

    private suspend fun updateReceipts(chatId: String, ids: List<String>, read: Boolean,
        topicId: String?, expectedAccount: String? = null) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        check(expectedAccount==null || expectedAccount==uid) { "Аккаунт изменился" }
        val chatRef = firestore.collection("chats").document(chatId)
        // Small transactions stay within the rules document-access budget. All reads precede writes.
        ids.distinct().chunked(8).forEach { chunk ->
            retryReceiptConflict { firestore.runTransaction { tx ->
                check(firebaseAuth.currentUser?.uid == uid) { "Аккаунт изменился" }
                val chat = tx.get(chatRef)
                val topicRef = topicId?.let { chatRef.collection("topics").document(it) }
                val topic = topicRef?.let { tx.get(it) }
                val docs = chunk.map { tx.get(chatRef.collection("messages").document(it)) }
                for (doc in docs) {
                    if (!doc.exists() || doc.getString("senderId") == uid) continue
                    val recipients = (doc.get("recipientIds") as? List<*>)?.filterIsInstance<String>()
                    val old = doc.getString("status")
                    val updates = mutableMapOf<String, Any>()
                    val next = if (recipients != null) {
                        if (uid !in recipients) continue
                        val delivered = ((doc.get("deliveredTo") as? List<*>)?.filterIsInstance<String>().orEmpty() + uid).distinct()
                        val readers = ((doc.get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty() + if (read) listOf(uid) else emptyList()).distinct()
                        if (doc.get("deliveredTo") != delivered) updates["deliveredTo"] = delivered
                        if (doc.get("readBy") != readers) updates["readBy"] = readers
                        aggregateReceiptStatus(recipients, delivered, readers).name
                    } else {
                        // Legacy records have only one scalar receipt; never downgrade READ.
                        if (old == "READ" || read) "READ" else "DELIVERED"
                    }
                    if (next != old) updates["status"] = next
                    if (updates.isNotEmpty()) tx.update(doc.reference, updates)
                    if (chat.getString("lastMessageId") == doc.id && next != chat.getString("lastMessageStatus")) {
                        tx.update(chatRef, "lastMessageStatus", next)
                    }
                }
                null
            }.await() }
        }
    }

    private suspend fun retryReceiptConflict(block: suspend () -> Unit) {
        // Rules may reject a stale receipt before the SDK reports a transaction conflict.
        repeat(4) { attempt ->
            try { block(); return }
            catch (e: FirebaseFirestoreException) {
                if (attempt == 3 || e.code !in listOf(FirebaseFirestoreException.Code.ABORTED,
                        FirebaseFirestoreException.Code.PERMISSION_DENIED)) throw e
                delay(150L * (attempt + 1))
            }
        }
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
        val uid = firebaseAuth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val messageRef = firestore.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        // Исключение больше не глотаем: ChatViewModel.toggleReaction() делает оптимистичный
        // локальный апдейт и должен узнать о неудаче, чтобы откатить его.
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(messageRef)
            val reactionsRaw = snapshot.get("reactions") as? Map<*, *>
            val currentUids = (reactionsRaw?.get(emoji) as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val updatedUids = if (uid in currentUids) currentUids - uid else currentUids + uid
            transaction.update(messageRef, "reactions.$emoji", updatedUids)
        }.await()
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
                val text = decryptedPayload(doc)?.text ?: "🔒 Не удалось расшифровать"
                val timestamp = doc.getLong("timestamp") ?: 0L
                val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru")).format(Date(timestamp))
                val hasImage = doc.getString("imageBase64") != null
                val fileName = doc.getString("fileName")
                val hasLocation = doc.getDouble("locationLat") != null
                sb.appendLine("[$time] $senderId:")
                if (text.isNotBlank()) sb.appendLine("  $text")
                if (doc.getBoolean("isDeleted") != true) {
                    SharedChecklist.fromFields(doc.get("checklistTitle"), doc.get("checklistItems"), doc.get("checklistCheckedBy"))?.let { list ->
                        sb.appendLine("  ${list.title}")
                        list.items.forEachIndexed { index, label ->
                            sb.appendLine("  ${if (list.completedBy(index) != null) "[x]" else "[ ]"} $label")
                        }
                    }
                }
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

    /**
     * НОВОЕ (пагинация истории): одна страница истории старше beforeTimestamp.
     * Читает с сервера (Source.SERVER не задаём — допустим кэш), возвращает
     * сообщения по возрастанию timestamp, как их ждёт UI. Истёкшие исчезающие
     * сообщения отфильтрованы так же, как в observeMessages.
     *
     * Ошибку не глотаем: в отличие от «история кончилась» (пустой список), сбой сети
     * не должен навсегда выключать подгрузку — ViewModel показывает сообщение и
     * позволяет повторить попытку.
     */
    override suspend fun getMessagesPage(
        chatId: String,
        topicId: String?,
        beforeTimestamp: Long?,
        limit: Int
    ): List<Message> {
        var query: Query = firestore.collection("chats").document(chatId)
            .collection("messages")
        if (topicId != null) query = query.whereEqualTo("topicId", topicId)
        if (beforeTimestamp != null) query = query.whereLessThan("timestamp", beforeTimestamp)
        val snapshot = query
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.coerceIn(1, 200).toLong())
            .get().await()
        return snapshot.documents
            .mapNotNull { mapDocToMessage(it, chatId) }
            .filter { msg -> msg.expiresAt == null || msg.expiresAt > System.currentTimeMillis() }
            .sortedBy { it.timestamp }
    }

    /** НОВОЕ: общий маппинг документа в Message — используется в observeMessages,
     *  observeMessage и getRecentMessages, чтобы не дублировать разбор полей. */
    private fun mapDocToMessage(doc: DocumentSnapshot, chatId: String): Message? {
        return try {
            val reactionsRaw = doc.get("reactions") as? Map<*, *>
            val reactions = reactionsRaw?.mapNotNull { (key, value) ->
                val emoji = key as? String ?: return@mapNotNull null
                val uids = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                emoji to uids
            }?.toMap() ?: emptyMap()
            val payload = decryptedPayload(doc)
            Message(
                id = doc.id, chatId = chatId,
                senderId = doc.getString("senderId") ?: return null,
                topicId = doc.getString("topicId"),
                text = payload?.text ?: "🔒 Не удалось расшифровать",
                canvasLocalId = doc.getString("canvasLocalId")?.takeIf { app.yodo.messenger.features.canvas.CanvasLimits.validId(it) },
                isTextEncrypted = doc.getBoolean("encrypted") == true,
                textDecryptionFailed = doc.getBoolean("encrypted") == true && payload == null,
                timestamp = doc.getLong("timestamp") ?: 0L,
                // ИСПРАВЛЕНО (индикатор прочитано/доставлено "врёт"): раньше статус брался
                // прямо из поля "status" без учёта hasPendingWrites, поэтому оптимистично
                // созданное локальное сообщение могло на мгновение показать неверную
                // (устаревшую из кэша) галочку. Теперь решение централизовано в
                // resolveMessageStatus и явно учитывает, подтверждена ли запись сервером.
                status = resolveMessageStatus(doc.getString("status"), doc.metadata.hasPendingWrites(),
                    doc.getString("senderId") == firebaseAuth.currentUser?.uid, doc.getTimestamp("sentAt") != null),
                receiptRecipientCount = (doc.get("recipientIds") as? List<*>)?.size ?: 0,
                receiptReadCount = (doc.get("readBy") as? List<*>)?.size ?: 0,
                replyToMessageId = doc.getString("replyToMessageId"),
                replyToSenderName = payload?.replySenderName ?: if (doc.getLong("encryptedPayloadVersion") != 2L) doc.getString("replyToSenderName") else null,
                replyToText = payload?.replyText ?: if (doc.getLong("encryptedPayloadVersion") != 2L) doc.getString("replyToText") else null,
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
                checklist = SharedChecklist.fromFields(doc.get("checklistTitle"), doc.get("checklistItems"), doc.get("checklistCheckedBy")),
                poll = mapDocToPoll(doc),
                isViewOnce = doc.getBoolean("isViewOnce") ?: false,
                viewOnceOpened = doc.getBoolean("viewOnceOpened") ?: false,
                viewCount = (doc.getLong("viewCount") ?: 0L).toInt()
            )
        } catch (e: Exception) { null }
    }
}