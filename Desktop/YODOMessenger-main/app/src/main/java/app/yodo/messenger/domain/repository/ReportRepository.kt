package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.Report
import app.yodo.messenger.domain.model.ReportComment
import app.yodo.messenger.domain.model.ReportReason
import app.yodo.messenger.domain.model.ReportResolution
import app.yodo.messenger.domain.model.ReportStatus
import kotlinx.coroutines.flow.Flow

sealed class ReportActionResult {
    data object Success : ReportActionResult()
    data class Error(val message: String) : ReportActionResult()
}

/**
 * П.5 ТЗ: система жалоб с очередью. Жалобы привязаны к конкретному чату/каналу —
 * их видят и рассматривают админы этого чата (см. Permission.VIEW_ADMIN_LOG-подобное
 * право ниже, но для жалоб используется отдельная проверка на стороне ViewModel/rules).
 */
interface ReportRepository {

    /** Подать жалобу на сообщение. */
    suspend fun reportMessage(
        chatId: String,
        messageId: String,
        messagePreview: String,
        targetUserId: String,
        targetUserName: String,
        reason: ReportReason,
        customReasonText: String = ""
    ): ReportActionResult

    /** Подать жалобу на пользователя (без привязки к конкретному сообщению). */
    suspend fun reportUser(
        chatId: String,
        targetUserId: String,
        targetUserName: String,
        reason: ReportReason,
        customReasonText: String = ""
    ): ReportActionResult

    /** Живой поток очереди жалоб чата, отсортированный по дате (новые первыми). */
    fun observeReports(chatId: String, status: ReportStatus? = null): Flow<List<Report>>

    suspend fun getReport(chatId: String, reportId: String): Report?

    /** Комментарии админов в ходе рассмотрения конкретной жалобы. */
    fun observeReportComments(chatId: String, reportId: String): Flow<List<ReportComment>>
    suspend fun addReportComment(chatId: String, reportId: String, text: String): ReportActionResult

    /** Закрыть жалобу без действий над контентом/пользователем. */
    suspend fun dismissReport(chatId: String, reportId: String, comment: String = ""): ReportActionResult

    /**
     * Принять решение по жалобе: удалить сообщение и/или забанить пользователя,
     * с одновременной пометкой жалобы как решённой. resolution описывает итог для истории.
     * НОВОЕ (баг 10): silentDelete=true — «тихое удаление» сообщения (документ удаляется
     * из Firestore целиком, у всех участников оно просто исчезает без заглушки «удалено»).
     */
    suspend fun resolveReport(
        chatId: String,
        reportId: String,
        resolution: ReportResolution,
        comment: String = "",
        deleteMessage: Boolean = false,
        banUser: Boolean = false,
        silentDelete: Boolean = false
    ): ReportActionResult

    /** Число жалоб в очереди (для бейджа на кнопке входа в раздел). */
    suspend fun countPendingReports(chatId: String): Int

    /**
     * НОВОЕ (AD): пользователь подаёт обжалование на глобальную блокировку аккаунта.
     * Падает в раздел жалоб админов с пометкой «Обжалование».
     */
    suspend fun submitAppeal(text: String, photoBase64: String? = null): ReportActionResult

    /**
     * НОВОЕ (AC): глобальная лента всех жалоб по всем чатам — только для главных админов (2 почты).
     * Собирается через collectionGroup("reports").
     */
    fun observeAllReports(status: ReportStatus? = null): Flow<List<Report>>
}
