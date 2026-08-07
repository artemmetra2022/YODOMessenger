package app.yodo.messenger.domain.model

/** На что подана жалоба. */
enum class ReportTargetType { MESSAGE, USER }

/** Причина жалобы — фиксированный список + "Другое" со свободным текстом (см. [Report.customReasonText]). */
enum class ReportReason(val label: String) {
    SPAM("Спам"),
    HARASSMENT("Оскорбления или травля"),
    VIOLENCE("Насилие или угрозы"),
    ILLEGAL_CONTENT("Запрещённый контент"),
    FRAUD("Мошенничество"),
    OTHER("Другое")
}

/** Статус рассмотрения жалобы админом. */
enum class ReportStatus(val label: String) {
    PENDING("На рассмотрении"),
    RESOLVED("Решена"),
    DISMISSED("Отклонена")
}

/** Решение, принятое админом по жалобе (для истории/лога). */
enum class ReportResolution(val label: String) {
    MESSAGE_DELETED("Сообщение удалено"),
    USER_BANNED("Пользователь забанен"),
    DISMISSED("Жалоба отклонена"),
    NO_ACTION("Без действий")
}

/**
 * Жалоба на сообщение или пользователя. Хранится в подколлекции chats/{chatId}/reports
 * для жалоб внутри чата/канала — так очередь жалоб естественно привязана к месту, где
 * у жалующегося есть право её подать, и её видят админы этого конкретного чата.
 */
data class Report(
    val id: String = "",
    val chatId: String,
    val targetType: ReportTargetType,
    val targetMessageId: String? = null,
    // Текст/превью сообщения на момент подачи жалобы — сохраняем, т.к. сообщение
    // может быть удалено к моменту рассмотрения.
    val targetMessagePreview: String? = null,
    val targetUserId: String,
    val targetUserName: String,
    val reporterId: String,
    val reporterName: String,
    val reason: ReportReason,
    val customReasonText: String = "",
    val status: ReportStatus = ReportStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    // Заполняется при рассмотрении:
    val reviewedBy: String? = null,
    val reviewedByName: String? = null,
    val reviewedAt: Long? = null,
    val reviewerComment: String = "",
    val resolution: ReportResolution? = null
)

/** Комментарий админа в ходе рассмотрения жалобы (обсуждение может идти в несколько реплик). */
data class ReportComment(
    val id: String = "",
    val authorId: String,
    val authorName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
