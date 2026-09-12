package app.yodo.messenger.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import app.yodo.messenger.domain.model.ModerationDeleteReason
import kotlinx.coroutines.tasks.await

/**
 * Выполняет административное удаление после обязательного 24-часового окна пересмотра.
 *
 * Поддерживает:
 *  1) удаление одного сообщения по жалобе;
 *  2) массовое удаление сообщений пользователя за период.
 *
 * Все условия повторно проверяются на сервере перед действием.
 */
class ModerationDeletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun doWork(): Result {
        return try {
            val executeAt = inputData.getLong(KEY_EXECUTE_AT, 0L)
            if (executeAt <= 0L || System.currentTimeMillis() < executeAt) {
                return Result.retry()
            }

            val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
            val reportId = inputData.getString(KEY_REPORT_ID)
            val messageId = inputData.getString(KEY_MESSAGE_ID)
            val silentDelete = inputData.getBoolean(KEY_SILENT_DELETE, false)

            if (!reportId.isNullOrBlank()) {
                executeReportDeletion(chatId, reportId, messageId, silentDelete)
            } else {
                val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
                val startAt = inputData.getLong(KEY_START_AT, 0L)
                val endAt = inputData.getLong(KEY_END_AT, 0L)
                executeBulkDeletion(chatId, userId, startAt, endAt)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun executeReportDeletion(
        chatId: String,
        reportId: String,
        messageId: String?,
        silentDelete: Boolean
    ) {
        val reportRef = firestore.collection("chats").document(chatId)
            .collection("reports").document(reportId)
        val report = reportRef.get().await()
        if (!report.exists() || report.getBoolean("deletionCancelled") == true) return

        val scheduledAt = report.getLong("deletionScheduledAt") ?: return
        if (System.currentTimeMillis() < scheduledAt) return

        if (!messageId.isNullOrBlank()) {
            val messageRef = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            archiveMessage(chatId, messageRef, parseReason(report.getString("reason")))
            if (silentDelete) {
                messageRef.delete().await()
            } else {
                messageRef.update(
                    mapOf(
                        "isDeleted" to true,
                        "deletedByAdmin" to true,
                        "text" to "",
                        "imageBase64" to FieldValue.delete(),
                        "fileBase64" to FieldValue.delete(),
                        "fileName" to FieldValue.delete(),
                        "fileMimeType" to FieldValue.delete(),
                        "fileSizeBytes" to FieldValue.delete(),
                        "locationLat" to FieldValue.delete(),
                        "locationLng" to FieldValue.delete()
                    )
                ).await()
            }
        }

        val now = System.currentTimeMillis()
        reportRef.update(
            mapOf(
                "status" to "RESOLVED",
                "resolution" to "MESSAGE_DELETED",
                "reviewedAt" to now,
                "deletionCancelled" to false,
                "deletionExecutedAt" to now
            )
        ).await()
    }

    private suspend fun executeBulkDeletion(
        chatId: String,
        userId: String,
        startAt: Long,
        endAt: Long
    ) {
        val ref = firestore.collection("chats").document(chatId).collection("messages")
        val docs = ref
            .whereEqualTo("senderId", userId)
            .whereGreaterThanOrEqualTo("timestamp", startAt)
            .whereLessThanOrEqualTo("timestamp", endAt)
            .get().await().documents

        docs.chunked(450).forEach { chunk ->
            chunk.forEach { doc -> archiveMessage(chatId, doc.reference, ModerationDeleteReason.OTHER) }
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "isDeleted" to true,
                        "deletedByAdmin" to true,
                        "text" to "",
                        "imageBase64" to FieldValue.delete(),
                        "fileBase64" to FieldValue.delete(),
                        "fileName" to FieldValue.delete(),
                        "fileMimeType" to FieldValue.delete(),
                        "fileSizeBytes" to FieldValue.delete(),
                        "locationLat" to FieldValue.delete(),
                        "locationLng" to FieldValue.delete()
                    )
                )
            }
            batch.commit().await()
        }
    }

    private suspend fun archiveMessage(
        chatId: String, messageRef: com.google.firebase.firestore.DocumentReference,
        reason: ModerationDeleteReason
    ) {
        val snapshot = messageRef.get().await()
        if (!snapshot.exists()) return
        val original = snapshot.data ?: return
        val now = System.currentTimeMillis()
        val senderId = snapshot.getString("senderId").orEmpty()
        val senderName = firestore.collection("users").document(senderId).get().await()
            .getString("displayName").orEmpty()
        firestore.collection("moderationDeletedMessages").document().set(
            mapOf(
                "chatId" to chatId, "messageId" to snapshot.id, "senderId" to senderId,
                "senderName" to senderName, "preview" to (snapshot.getString("text") ?: "").take(500),
                "reason" to reason.name, "deletedAt" to now,
                "restoreUntil" to now + 30L * 24 * 60 * 60 * 1000,
                "deletedBy" to "MODERATION_WORKER", "automatic" to false,
                "originalData" to original
            )
        ).await()
    }

    private fun parseReason(value: String?): ModerationDeleteReason = when (value) {
        "SPAM" -> ModerationDeleteReason.SPAM
        "HARASSMENT" -> ModerationDeleteReason.HARASSMENT
        "NSFW" -> ModerationDeleteReason.NSFW
        "OTHER" -> ModerationDeleteReason.OTHER
        else -> ModerationDeleteReason.RULES
    }

    companion object {
        const val KEY_CHAT_ID = "chatId"
        const val KEY_REPORT_ID = "reportId"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_EXECUTE_AT = "executeAt"
        const val KEY_SILENT_DELETE = "silentDelete"
        const val KEY_USER_ID = "userId"
        const val KEY_START_AT = "startAt"
        const val KEY_END_AT = "endAt"
    }
}
