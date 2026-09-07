package app.yodo.messenger.data.repository

import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.domain.model.AdminActionType
import app.yodo.messenger.domain.model.Report
import app.yodo.messenger.domain.model.ReportComment
import app.yodo.messenger.domain.model.ReportReason
import app.yodo.messenger.domain.model.ReportResolution
import app.yodo.messenger.domain.model.ReportStatus
import app.yodo.messenger.domain.model.ReportTargetType
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.ReportActionResult
import app.yodo.messenger.domain.repository.ReportRepository
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository
) : ReportRepository {

    private fun reportsRef(chatId: String) =
        firestore.collection("chats").document(chatId).collection("reports")

    private suspend fun currentUserName(): String {
        val uid = firebaseAuth.currentUser?.uid ?: return "Пользователь"
        return firestore.collection("users").document(uid).get().await()
            .getString("displayName") ?: "Пользователь"
    }

    override suspend fun reportMessage(
        chatId: String,
        messageId: String,
        messagePreview: String,
        targetUserId: String,
        targetUserName: String,
        reason: ReportReason,
        customReasonText: String
    ): ReportActionResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return ReportActionResult.Error("Нужно войти в аккаунт")
        return try {
            val reporterName = currentUserName()
            val data = mapOf(
                "targetType" to ReportTargetType.MESSAGE.name,
                "targetMessageId" to messageId,
                "targetMessagePreview" to messagePreview.take(500),
                "targetUserId" to targetUserId,
                "targetUserName" to targetUserName,
                "reporterId" to uid,
                "reporterName" to reporterName,
                "reason" to reason.name,
                "customReasonText" to customReasonText.take(500),
                "status" to ReportStatus.PENDING.name,
                "createdAt" to System.currentTimeMillis()
            )
            reportsRef(chatId).add(data).await()
            ReportActionResult.Success
        } catch (e: Exception) {
            ReportActionResult.Error(e.toUserMessage("Не удалось отправить жалобу"))
        }
    }

    override suspend fun reportUser(
        chatId: String,
        targetUserId: String,
        targetUserName: String,
        reason: ReportReason,
        customReasonText: String
    ): ReportActionResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return ReportActionResult.Error("Нужно войти в аккаунт")
        return try {
            val reporterName = currentUserName()
            val data = mapOf(
                "targetType" to ReportTargetType.USER.name,
                "targetUserId" to targetUserId,
                "targetUserName" to targetUserName,
                "reporterId" to uid,
                "reporterName" to reporterName,
                "reason" to reason.name,
                "customReasonText" to customReasonText.take(500),
                "status" to ReportStatus.PENDING.name,
                "createdAt" to System.currentTimeMillis()
            )
            reportsRef(chatId).add(data).await()
            ReportActionResult.Success
        } catch (e: Exception) {
            ReportActionResult.Error(e.toUserMessage("Не удалось отправить жалобу"))
        }
    }

    private fun parseReport(chatId: String, id: String, data: Map<String, Any?>): Report? {
        val targetTypeName = data["targetType"] as? String ?: return null
        val targetType = runCatching { ReportTargetType.valueOf(targetTypeName) }.getOrNull() ?: return null
        val reasonName = data["reason"] as? String ?: return null
        val reason = runCatching { ReportReason.valueOf(reasonName) }.getOrNull() ?: return null
        val statusName = data["status"] as? String ?: ReportStatus.PENDING.name
        val status = runCatching { ReportStatus.valueOf(statusName) }.getOrNull() ?: ReportStatus.PENDING
        val resolutionName = data["resolution"] as? String
        val resolution = resolutionName?.let { runCatching { ReportResolution.valueOf(it) }.getOrNull() }
        return Report(
            id = id,
            chatId = chatId,
            targetType = targetType,
            targetMessageId = data["targetMessageId"] as? String,
            targetMessagePreview = data["targetMessagePreview"] as? String,
            targetUserId = data["targetUserId"] as? String ?: "",
            targetUserName = data["targetUserName"] as? String ?: "",
            reporterId = data["reporterId"] as? String ?: "",
            reporterName = data["reporterName"] as? String ?: "",
            reason = reason,
            customReasonText = data["customReasonText"] as? String ?: "",
            status = status,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            reviewedBy = data["reviewedBy"] as? String,
            reviewedByName = data["reviewedByName"] as? String,
            reviewedAt = (data["reviewedAt"] as? Number)?.toLong(),
            reviewerComment = data["reviewerComment"] as? String ?: "",
            resolution = resolution,
            // НОВОЕ (AD): отметка обжалования и фото.
            isAppeal = (data["isAppeal"] as? Boolean) ?: (reason == ReportReason.APPEAL),
            appealPhotoBase64 = data["appealPhotoBase64"] as? String
        )
    }

    override fun observeReports(chatId: String, status: ReportStatus?): Flow<List<Report>> = callbackFlow {
        var query: Query = reportsRef(chatId).orderBy("createdAt", Query.Direction.DESCENDING)
        if (status != null) query = query.whereEqualTo("status", status.name)
        val registration = query.addSnapshotListener { snapshot, _ ->
            val reports = snapshot?.documents?.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                parseReport(chatId, doc.id, data)
            } ?: emptyList()
            trySend(reports)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getReport(chatId: String, reportId: String): Report? {
        return try {
            val doc = reportsRef(chatId).document(reportId).get().await()
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return null
            parseReport(chatId, doc.id, data)
        } catch (e: Exception) { null }
    }

    override fun observeReportComments(chatId: String, reportId: String): Flow<List<ReportComment>> = callbackFlow {
        val registration = reportsRef(chatId).document(reportId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val comments = snapshot?.documents?.mapNotNull { doc ->
                    ReportComment(
                        id = doc.id,
                        authorId = doc.getString("authorId") ?: "",
                        authorName = doc.getString("authorName") ?: "Пользователь",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
                trySend(comments)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addReportComment(chatId: String, reportId: String, text: String): ReportActionResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return ReportActionResult.Error("Нужно войти в аккаунт")
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ReportActionResult.Error("Комментарий не может быть пустым")
        return try {
            val authorName = currentUserName()
            reportsRef(chatId).document(reportId).collection("comments").add(
                mapOf(
                    "authorId" to uid,
                    "authorName" to authorName,
                    "text" to trimmed,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            ReportActionResult.Success
        } catch (e: Exception) {
            ReportActionResult.Error(e.toUserMessage("Не удалось добавить комментарий"))
        }
    }

    override suspend fun dismissReport(chatId: String, reportId: String, comment: String): ReportActionResult {
        return finalizeReport(chatId, reportId, ReportStatus.DISMISSED, ReportResolution.DISMISSED, comment)
    }

    override suspend fun resolveReport(
        chatId: String,
        reportId: String,
        resolution: ReportResolution,
        comment: String,
        deleteMessage: Boolean,
        banUser: Boolean,
        silentDelete: Boolean
    ): ReportActionResult {
        val report = getReport(chatId, reportId)
            ?: return ReportActionResult.Error("Жалоба не найдена")

        if (deleteMessage && report.targetMessageId != null) {
            // НОВОЕ (баг 10): два режима удаления по жалобе —
            //  1) обычное административное удаление: isDeleted=true + deletedByAdmin=true,
            //     в чате видно «Сообщение удалено администратором»;
            //  2) «тихое удаление» (silentDelete): документ сообщения удаляется целиком,
            //     сообщение бесследно исчезает у всех участников.
            val result = if (silentDelete) {
                messageRepository.hardDeleteMessage(chatId, report.targetMessageId)
            } else {
                messageRepository.deleteMessage(chatId, report.targetMessageId, deletedByAdmin = true)
            }
            if (result is SendMessageResult.Error) {
                return ReportActionResult.Error(result.message)
            }
            chatRepository.logAdminAction(
                chatId, AdminActionType.MESSAGE_DELETED,
                details = if (silentDelete) "По жалобе #$reportId (тихое удаление)" else "По жалобе #$reportId",
                targetUserId = report.targetUserId, targetUserName = report.targetUserName
            )
        }
        if (banUser) {
            when (val result = chatRepository.banMember(chatId, report.targetUserId)) {
                is ChannelUpdateResult.Error -> return ReportActionResult.Error(result.message)
                else -> {}
            }
        }
        return finalizeReport(chatId, reportId, ReportStatus.RESOLVED, resolution, comment)
    }

    private suspend fun finalizeReport(
        chatId: String,
        reportId: String,
        status: ReportStatus,
        resolution: ReportResolution,
        comment: String
    ): ReportActionResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return ReportActionResult.Error("Нужно войти в аккаунт")
        return try {
            val reviewerName = currentUserName()
            reportsRef(chatId).document(reportId).update(
                mapOf(
                    "status" to status.name,
                    "resolution" to resolution.name,
                    "reviewedBy" to uid,
                    "reviewedByName" to reviewerName,
                    "reviewedAt" to System.currentTimeMillis(),
                    "reviewerComment" to comment.take(1000)
                )
            ).await()
            ReportActionResult.Success
        } catch (e: Exception) {
            ReportActionResult.Error(e.toUserMessage("Не удалось сохранить решение"))
        }
    }

    override suspend fun countPendingReports(chatId: String): Int {
        return try {
            reportsRef(chatId).whereEqualTo("status", ReportStatus.PENDING.name)
                .get().await().size()
        } catch (e: Exception) { 0 }
    }

    // НОВОЕ (AD): обжалование глобальной блокировки — падает в раздел жалоб
    // официального канала с пометкой isAppeal=true.
    override suspend fun submitAppeal(text: String, photoBase64: String?): ReportActionResult {
        val uid = firebaseAuth.currentUser?.uid
            ?: return ReportActionResult.Error("Нужно войти в аккаунт")
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ReportActionResult.Error("Напишите текст обжалования")
        return try {
            val reporterName = currentUserName()
            val data = mapOf(
                "targetType" to ReportTargetType.USER.name,
                "targetUserId" to uid,
                "targetUserName" to reporterName,
                "reporterId" to uid,
                "reporterName" to reporterName,
                "reason" to ReportReason.APPEAL.name,
                "customReasonText" to trimmed.take(1000),
                "status" to ReportStatus.PENDING.name,
                "createdAt" to System.currentTimeMillis(),
                "isAppeal" to true,
                "appealPhotoBase64" to photoBase64
            )
            reportsRef(ChatRepository.OFFICIAL_CHANNEL_ID).add(data).await()
            ReportActionResult.Success
        } catch (e: Exception) {
            ReportActionResult.Error(e.toUserMessage("Не удалось отправить обжалование"))
        }
    }

    // НОВОЕ (AC): глобальная лента всех жалоб через collectionGroup.
    override fun observeAllReports(status: ReportStatus?): Flow<List<Report>> = callbackFlow {
        var query: Query = firestore.collectionGroup("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)
        if (status != null) query = query.whereEqualTo("status", status.name)
        val registration = query.addSnapshotListener { snapshot, _ ->
            val reports = snapshot?.documents?.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                // chatId восстанавливаем из пути: chats/{chatId}/reports/{id}
                val chatId = doc.reference.parent.parent?.id ?: ""
                parseReport(chatId, doc.id, data)
            } ?: emptyList()
            trySend(reports)
        }
        awaitClose { registration.remove() }
    }
}
