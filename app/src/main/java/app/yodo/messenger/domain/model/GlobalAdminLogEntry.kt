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
    REQUIRE_EMAIL_VERIFICATION_CHANGED("Изменение обязательного подтверждения email")
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
