package app.yodo.messenger.domain.model

/**
 * НОВОЕ (глобальный аудит-лог Админки): тип зафиксированного действия.
 * Отличие от AdminActionType (chats/{chatId}/adminLog) — это лог действий,
 * которые не привязаны к конкретному чату/группе, а относятся к приложению
 * в целом и доступны только двум главным админам (ChatRepository.ADMIN_EMAILS).
 */
enum class GlobalAdminActionType(val label: String) {
    USER_GLOBALLY_BLOCKED("Глобальная блокировка пользователя"),
    USER_GLOBALLY_UNBLOCKED("Снятие глобальной блокировки"),
    REQUIRE_EMAIL_VERIFICATION_CHANGED("Изменение обязательного подтверждения email"),
    // НОВОЕ (веб-админка: раздел «Жалобы») — решения по жалобам из веб-панели.
    REPORT_RESOLVED_MESSAGE_DELETED("Жалоба решена: сообщение удалено"),
    REPORT_RESOLVED_USER_BANNED("Жалоба решена: автор заблокирован"),
    REPORT_DISMISSED("Жалоба отклонена"),
    // НОВОЕ (веб-админка: новости и опросы) — отложенная публикация,
    // черновики, закрытие опросов и ручной push.
    SCHOOL_NEWS_PUBLISHED("Публикация черновика новости"),
    SCHOOL_NEWS_DRAFT_SAVED("Сохранён черновик новости"),
    SCHOOL_POLL_CLOSED("Закрытие/открытие опроса"),
    SCHOOL_PUSH_RESENT("Ручная отправка push"),
    // НОВОЕ (расширенная модерация веб-админки): массовое удаление сообщений
    // нарушителя по периоду из раздела «Жалобы».
    REPORT_BULK_MESSAGES_DELETED("Массовое удаление сообщений")
}

/**
 * Одна запись глобального журнала действий Админки.
 * Хранится в корневой коллекции adminAuditLog, сортировка по timestamp desc.
 */
data class GlobalAdminLogEntry(
    val id: String = "",
    val actorId: String,
    val actorName: String,
    val actionType: GlobalAdminActionType,
    val details: String = "",
    val targetUserId: String? = null,
    val targetUserName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
