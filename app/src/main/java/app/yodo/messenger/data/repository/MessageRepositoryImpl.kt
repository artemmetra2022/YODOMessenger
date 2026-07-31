package app.yodo.messenger.data.repository

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
    private val userSettingsPreferences: UserSettingsPreferences
) : MessageRepository {

    override fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList<Message>()); return@addSnapshotListener }
                val messages = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> mapDocToMessage(doc, chatId) }
                    // п.38: истёкшие исчезающие сообщения не показываем в UI
                    .filter { msg -> msg.expiresAt == null || msg.expiresAt > System.currentTimeMillis() }
                trySend(messages)
            }
        awaitClose { listener.remove() }
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
        ttlOverrideSeconds: Long? = null
    ): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val chatSnapshot = chatRef.get().await()
            val participantIds = (chatSnapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()
            val now = System.currentTimeMillis()
            data["senderId"] = uid
            data["timestamp"] = now
            data["status"] = "SENT"
            data["notified"] = false
            // п.38 + переработка per-message: если пользователь явно выбрал таймер для этого
            // сообщения — используем его (в т.ч. явное "выключено"); иначе — TTL чата по умолчанию.
            val ttlSeconds = if (hasTtlOverride) ttlOverrideSeconds else chatSnapshot.getLong("disappearingTtlSeconds")
            if (ttlSeconds != null && ttlSeconds > 0) {
                data["expiresAt"] = now + ttlSeconds * 1000L
            }
            val newDocRef = chatRef.collection("messages").add(data).await()
            val previewText = (data["text"] as? String)?.takeIf { it.isNotBlank() }
                ?: if (data.containsKey("voiceBase64")) "🎤 Голосовое сообщение"
                else if (data.containsKey("imageBase64")) "📷 Фото"
                else if (data.containsKey("locationLat")) "📍 Геопозиция"
                else if (data.containsKey("fileBase64")) "📎 ${data["fileName"] as? String ?: "Файл"}"
                else ""
            val unreadUpdates = mutableMapOf<String, Any?>(
                "lastMessage" to previewText, "lastMessageTimestamp" to now,
                "lastMessageSenderId" to uid, "lastMessageStatus" to "SENT"
            )
            participantIds.filterIsInstance<String>().filter { it != uid }.forEach { otherUid ->
                unreadUpdates["unreadCounts.$otherUid"] = FieldValue.increment(1)
            }
            chatRef.update(unreadUpdates).await()
            SendMessageResult.Success(messageId = newDocRef.id)
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось отправить сообщение")) }
    }

    override suspend fun sendMessage(
        chatId: String, text: String, replyTo: ReplyContext?,
        hasTtlOverride: Boolean, ttlOverrideSeconds: Long?
    ): SendMessageResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Сообщение не может быть пустым")
        val data = mutableMapOf<String, Any?>("text" to trimmed)
        if (replyTo != null) {
            data["replyToMessageId"] = replyTo.messageId
            data["replyToSenderName"] = replyTo.senderName
            data["replyToText"] = replyTo.text
        }
        return sendRawMessage(chatId, data, hasTtlOverride, ttlOverrideSeconds)
    }

    override suspend fun sendImageMessage(chatId: String, imageBase64: String, caption: String): SendMessageResult {
        val data = mutableMapOf<String, Any?>("imageBase64" to imageBase64, "text" to caption.trim())
        return sendRawMessage(chatId, data)
    }

    override suspend fun sendVoiceMessage(chatId: String, voiceBase64: String, durationMs: Long): SendMessageResult {
        val data = mutableMapOf<String, Any?>(
            "text" to "",
            "voiceBase64" to voiceBase64,
            "voiceDurationMs" to durationMs
        )
        return sendRawMessage(chatId, data)
    }

    override suspend fun sendFileMessage(
        chatId: String, fileBase64: String, fileName: String, mimeType: String, sizeBytes: Long
    ): SendMessageResult {
        val data = mutableMapOf<String, Any?>(
            "text" to "",
            "fileBase64" to fileBase64,
            "fileName" to fileName,
            "fileMimeType" to mimeType,
            "fileSizeBytes" to sizeBytes
        )
        return sendRawMessage(chatId, data)
    }

    override suspend fun sendLocationMessage(chatId: String, lat: Double, lng: Double): SendMessageResult {
        val data = mutableMapOf<String, Any?>(
            "text" to "",
            "locationLat" to lat,
            "locationLng" to lng
        )
        return sendRawMessage(chatId, data)
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

    override suspend fun forwardMessage(targetChatId: String, originalMessage: Message, fromSenderName: String, fromSenderId: String): SendMessageResult {
        val data = mutableMapOf<String, Any?>(
            "text" to originalMessage.text,
            "forwardedFromSenderName" to fromSenderName,
            "forwardedFromSenderId" to fromSenderId
        )
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

    override suspend fun deleteMessage(chatId: String, messageId: String): SendMessageResult {
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(mapOf(
                    "isDeleted" to true, "text" to "",
                    "imageBase64" to FieldValue.delete(),
                    "fileBase64" to FieldValue.delete(),
                    "fileName" to FieldValue.delete(),
                    "fileMimeType" to FieldValue.delete(),
                    "fileSizeBytes" to FieldValue.delete(),
                    "locationLat" to FieldValue.delete(),
                    "locationLng" to FieldValue.delete()
                )).await()
            SendMessageResult.Success()
        } catch (e: Exception) { SendMessageResult.Error(e.toUserMessage("Не удалось удалить")) }
    }

    override suspend fun markChatAsRead(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            val showReadReceipts = userSettingsPreferences.showReadReceipts.first()
            val chatRef = firestore.collection("chats").document(chatId)
            if (showReadReceipts) {
                val messagesRef = chatRef.collection("messages")
                val fromOthers = messagesRef.whereNotEqualTo("senderId", uid).get().await()
                val unreadDocs = fromOthers.documents.filter { it.getString("status") != "READ" }
                if (unreadDocs.isNotEmpty()) {
                    val batch = firestore.batch()
                    unreadDocs.forEach { doc -> batch.update(doc.reference, "status", "READ") }
                    batch.commit().await()
                }
                val chatSnapshot = chatRef.get().await()
                if (chatSnapshot.getString("lastMessageSenderId") != uid) {
                    chatRef.update("lastMessageStatus", "READ").await()
                }
            }
            chatRef.update("unreadCounts.$uid", 0).await()
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
                bookmarkRef.set(mapOf(
                    "messageId" to messageId, "chatId" to chatId,
                    "senderId" to (msgDoc.getString("senderId") ?: ""),
                    "text" to (msgDoc.getString("text") ?: ""),
                    "timestamp" to (msgDoc.getLong("timestamp") ?: 0L),
                    "imageBase64" to msgDoc.getString("imageBase64"),
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
            val batch = firestore.batch()
            expired.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        } catch (e: Exception) { }
    }

    // НОВОЕ (опросы): создание сообщения-опроса
    override suspend fun sendPollMessage(
        chatId: String,
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowMultipleAnswers: Boolean,
        replyTo: ReplyContext?,
        hasTtlOverride: Boolean,
        ttlOverrideSeconds: Long?
    ): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        if (question.trim().isEmpty()) return SendMessageResult.Error("Введите вопрос опроса")
        if (options.size < 2) return SendMessageResult.Error("Добавьте минимум 2 варианта ответа")
        if (options.any { it.trim().isEmpty() }) return SendMessageResult.Error("Варианты ответа не могут быть пустыми")
        
        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val chatSnapshot = chatRef.get().await()
            val participantIds = (chatSnapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()
            val now = System.currentTimeMillis()
            
            // Build poll data structure
            val pollData = mapOf(
                "question" to question.trim(),
                "options" to options.map { it.trim() },
                "votesByOption" to emptyMap<Int, List<String>>(),
                "isAnonymous" to isAnonymous,
                "allowMultipleAnswers" to allowMultipleAnswers,
                "isClosed" to false
            )
            
            val data = mutableMapOf<String, Any?>(
                "text" to "",
                "poll" to pollData,
                "senderId" to uid,
                "timestamp" to now,
                "status" to "SENT",
                "notified" to false
            )
            
            // Handle reply context
            if (replyTo != null) {
                data["replyToMessageId"] = replyTo.messageId
                data["replyToSenderName"] = replyTo.senderName
                data["replyToText"] = replyTo.text
            }
            
            // Handle TTL (disappearing messages)
            val ttlSeconds = if (hasTtlOverride) ttlOverrideSeconds else chatSnapshot.getLong("disappearingTtlSeconds")
            if (ttlSeconds != null && ttlSeconds > 0) {
                data["expiresAt"] = now + ttlSeconds * 1000L
            }
            
            val newDocRef = chatRef.collection("messages").add(data).await()
            
            // Update chat preview
            val previewText = "📊 ${question.trim()}"
            val unreadUpdates = mutableMapOf<String, Any?>(
                "lastMessage" to previewText, 
                "lastMessageTimestamp" to now,
                "lastMessageSenderId" to uid, 
                "lastMessageStatus" to "SENT"
            )
            participantIds.filterIsInstance<String>().filter { it != uid }.forEach { otherUid ->
                unreadUpdates["unreadCounts.$otherUid"] = FieldValue.increment(1)
            }
            chatRef.update(unreadUpdates).await()
            
            SendMessageResult.Success(messageId = newDocRef.id)
        } catch (e: Exception) { 
            SendMessageResult.Error(e.toUserMessage("Не удалось создать опрос")) 
        }
    }

    // НОВОЕ (опросы): голосование в опросе
    override suspend fun voteOnPoll(chatId: String, messageId: String, optionIndices: Set<Int>): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        if (optionIndices.isEmpty()) return SendMessageResult.Error("Выберите вариант ответа")
        
        return try {
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            
            val messageSnapshot = messageRef.get().await()
            val pollRaw = messageSnapshot.get("poll") as? Map<*, *> 
                ?: return SendMessageResult.Error("Опрос не найден")
            
            val isClosed = pollRaw["isClosed"] as? Boolean ?: false
            if (isClosed) return SendMessageResult.Error("Опрос закрыт")
            
            val optionsRaw = pollRaw["options"] as? List<*> 
                ?: return SendMessageResult.Error("Неверная структура опроса")
            val allowMultiple = pollRaw["allowMultipleAnswers"] as? Boolean ?: false
            
            // Validate option indices
            val validIndices = optionIndices.filter { it >= 0 && it < optionsRaw.size }
            if (validIndices.isEmpty()) return SendMessageResult.Error("Неверные варианты ответа")
            
            if (!allowMultiple && validIndices.size > 1) {
                return SendMessageResult.Error("Можно выбрать только один вариант")
            }
            
            val votesByOptionRaw = pollRaw["votesByOption"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val votesByOption = mutableMapOf<Int, MutableList<String>>()
            
            // Parse existing votes
            votesByOptionRaw.forEach { (key, value) ->
                val index = (key as? Number)?.toInt() ?: return@forEach
                val voters = (value as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
                votesByOption[index] = voters
            }
            
            // Remove user's previous votes from all options
            votesByOption.forEach { (_, voters) ->
                voters.remove(uid)
            }
            
            // Add user's new votes
            validIndices.forEach { index ->
                votesByOption.getOrPut(index) { mutableListOf() }.add(uid)
            }
            
            // Convert to Firestore-compatible format
            val votesByOptionMap = votesByOption.mapKeys { it.key.toString() }
            
            messageRef.update("poll.votesByOption", votesByOptionMap).await()
            SendMessageResult.Success()
        } catch (e: Exception) { 
            SendMessageResult.Error(e.toUserMessage("Не удалось проголосовать")) 
        }
    }

    // НОВОЕ (опросы): закрыть опрос
    override suspend fun closePoll(chatId: String, messageId: String): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid ?: return SendMessageResult.Error("Вы не авторизованы")
        
        return try {
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            
            val messageSnapshot = messageRef.get().await()
            val senderId = messageSnapshot.getString("senderId")
            
            // Only poll creator can close it
            if (senderId != uid) {
                return SendMessageResult.Error("Только создатель опроса может его закрыть")
            }
            
            messageRef.update("poll.isClosed", true).await()
            SendMessageResult.Success()
        } catch (e: Exception) { 
            SendMessageResult.Error(e.toUserMessage("Не удалось закрыть опрос")) 
        }
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
            val myDoc = firestore.collection("users").document(uid).get().await()
            val senderName = myDoc.getString("displayName")?.takeIf { it.isNotBlank() } ?: "Пользователь"
            val messageRef = firestore.collection("chats").document(chatId)
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
    private fun mapDocToMessage(doc: DocumentSnapshot, chatId: String): Message? {
        return try {
            val reactionsRaw = doc.get("reactions") as? Map<*, *>
            val reactions = reactionsRaw?.mapNotNull { (key, value) ->
                val emoji = key as? String ?: return@mapNotNull null
                val uids = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                emoji to uids
            }?.toMap() ?: emptyMap()
            
            // Parse poll data if present
            val poll = doc.get("poll") as? Map<*, *?>?.let { pollMap ->
                val question = pollMap["question"] as? String ?: return@let null
                val optionsRaw = pollMap["options"] as? List<*> ?: return@let null
                val options = optionsRaw.filterIsInstance<String>()
                if (options.isEmpty()) return@let null
                
                val isAnonymous = pollMap["isAnonymous"] as? Boolean ?: true
                val allowMultipleAnswers = pollMap["allowMultipleAnswers"] as? Boolean ?: false
                val isClosed = pollMap["isClosed"] as? Boolean ?: false
                
                // Parse votesByOption: Map<Int, List<String>>
                val votesByOptionRaw = pollMap["votesByOption"] as? Map<*, *> ?: emptyMap<Int, List<String>>()
                val votesByOption = votesByOptionRaw.mapNotNull { (key, value) ->
                    val optionIndex = (key as? Number)?.toInt() ?: return@mapNotNull null
                    val voterUids = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    optionIndex to voterUids
                }.toMap()
                
                Poll(
                    question = question,
                    options = options,
                    votesByOption = votesByOption,
                    isAnonymous = isAnonymous,
                    allowMultipleAnswers = allowMultipleAnswers,
                    isClosed = isClosed
                )
            }
            
            Message(
                id = doc.id, chatId = chatId,
                senderId = doc.getString("senderId") ?: return null,
                text = doc.getString("text") ?: "",
                timestamp = doc.getLong("timestamp") ?: 0L,
                status = doc.getString("status")?.let { raw ->
                    runCatching { MessageStatus.valueOf(raw) }.getOrDefault(MessageStatus.SENT)
                } ?: MessageStatus.SENT,
                replyToMessageId = doc.getString("replyToMessageId"),
                replyToSenderName = doc.getString("replyToSenderName"),
                replyToText = doc.getString("replyToText"),
                reactions = reactions,
                imageBase64 = doc.getString("imageBase64"),
                isEdited = doc.getBoolean("isEdited") ?: false,
                isDeleted = doc.getBoolean("isDeleted") ?: false,
                forwardedFromSenderName = doc.getString("forwardedFromSenderName"),
                forwardedFromSenderId = doc.getString("forwardedFromSenderId"),
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
                poll = poll
            )
        } catch (e: Exception) { null }
    }
}