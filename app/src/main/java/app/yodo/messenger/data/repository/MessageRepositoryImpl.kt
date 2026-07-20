package app.yodo.messenger.data.repository

import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Структура документа в подколлекции "chats/{chatId}/messages":
 * {
 *   senderId, text, timestamp, status, notified,
 *   replyToMessageId?, replyToSenderName?, replyToText?,
 *   reactions: { emoji: [uid,...] },
 *   imageBase64?, isEdited?, isDeleted?, forwardedFromSenderName?
 * }
 */
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
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    try {
                        val reactionsRaw = doc.get("reactions") as? Map<*, *>
                        val reactions = reactionsRaw
                            ?.mapNotNull reactionEntry@{ (key, value) ->
                                val emoji = key as? String ?: return@reactionEntry null
                                val uids = (value as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                emoji to uids
                            }
                            ?.toMap()
                            ?: emptyMap()

                        Message(
                            id = doc.id,
                            chatId = chatId,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
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
                            forwardedFromSenderName = doc.getString("forwardedFromSenderName")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    private suspend fun sendRawMessage(chatId: String, data: MutableMap<String, Any>): SendMessageResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return SendMessageResult.Error("Вы не авторизованы")

        return try {
            val chatRef = firestore.collection("chats").document(chatId)
            val chatSnapshot = chatRef.get().await()
            val participantIds = chatSnapshot.get("participantIds") as? List<*> ?: emptyList<String>()
            val now = System.currentTimeMillis()

            data["senderId"] = uid
            data["timestamp"] = now
            data["status"] = "SENT"
            data["notified"] = false

            chatRef.collection("messages").add(data).await()

            val previewText = (data["text"] as? String)?.takeIf { it.isNotBlank() }
                ?: if (data.containsKey("imageBase64")) "📷 Фото" else ""

            val unreadUpdates = mutableMapOf<String, Any>(
                "lastMessage" to previewText,
                "lastMessageTimestamp" to now
            )
            participantIds.filterIsInstance<String>()
                .filter { it != uid }
                .forEach { otherUid ->
                    unreadUpdates["unreadCounts.$otherUid"] = FieldValue.increment(1)
                }

            chatRef.update(unreadUpdates).await()

            SendMessageResult.Success
        } catch (e: Exception) {
            SendMessageResult.Error(e.message ?: "Не удалось отправить сообщение")
        }
    }

    override suspend fun sendMessage(chatId: String, text: String, replyTo: ReplyContext?): SendMessageResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Сообщение не может быть пустым")

        val data = mutableMapOf<String, Any>("text" to trimmed)
        if (replyTo != null) {
            data["replyToMessageId"] = replyTo.messageId
            data["replyToSenderName"] = replyTo.senderName
            data["replyToText"] = replyTo.text
        }
        return sendRawMessage(chatId, data)
    }

    override suspend fun sendImageMessage(chatId: String, imageBase64: String, caption: String): SendMessageResult {
        val data = mutableMapOf<String, Any>(
            "imageBase64" to imageBase64,
            "text" to caption.trim()
        )
        return sendRawMessage(chatId, data)
    }

    override suspend fun forwardMessage(
        targetChatId: String,
        originalMessage: Message,
        fromSenderName: String
    ): SendMessageResult {
        val data = mutableMapOf<String, Any>(
            "text" to originalMessage.text,
            "forwardedFromSenderName" to fromSenderName
        )
        originalMessage.imageBase64?.let { data["imageBase64"] = it }
        return sendRawMessage(targetChatId, data)
    }

    override suspend fun editMessage(chatId: String, messageId: String, newText: String): SendMessageResult {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return SendMessageResult.Error("Сообщение не может быть пустым")

        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(mapOf("text" to trimmed, "isEdited" to true))
                .await()
            SendMessageResult.Success
        } catch (e: Exception) {
            SendMessageResult.Error(e.message ?: "Не удалось отредактировать сообщение")
        }
    }

    override suspend fun deleteMessage(chatId: String, messageId: String): SendMessageResult {
        return try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "isDeleted" to true,
                        "text" to "",
                        "imageBase64" to FieldValue.delete()
                    )
                )
                .await()
            SendMessageResult.Success
        } catch (e: Exception) {
            SendMessageResult.Error(e.message ?: "Не удалось удалить сообщение")
        }
    }

    override suspend fun markChatAsRead(chatId: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            // Приватность: если пользователь выключил "показывать статус прочтения" — мы всё равно
            // сбрасываем счётчик непрочитанных у себя, но НЕ помечаем чужие сообщения как READ,
            // чтобы у отправителя не появлялась вторая (синяя) галочка.
            val showReadReceipts = userSettingsPreferences.showReadReceipts.first()

            if (showReadReceipts) {
                val messagesRef = firestore.collection("chats").document(chatId).collection("messages")

                val fromOthers = messagesRef
                    .whereNotEqualTo("senderId", uid)
                    .get().await()

                val unreadDocs = fromOthers.documents.filter { it.getString("status") != "READ" }

                if (unreadDocs.isNotEmpty()) {
                    val batch = firestore.batch()
                    unreadDocs.forEach { doc ->
                        batch.update(doc.reference, "status", "READ")
                    }
                    batch.commit().await()
                }
            }

            firestore.collection("chats").document(chatId)
                .update("unreadCounts.$uid", 0)
                .await()
        } catch (e: Exception) {
            // Не критично — просто не сброшенный счётчик/статус до следующего успешного вызова
        }
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

                val updatedUids = if (uid in currentUids) {
                    currentUids - uid
                } else {
                    currentUids + uid
                }

                transaction.update(messageRef, "reactions.$emoji", updatedUids)
            }.await()
        } catch (e: Exception) {
            // Не критично — реакция просто не обновится в этот раз
        }
    }
}
