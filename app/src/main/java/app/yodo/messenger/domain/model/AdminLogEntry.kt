package app.yodo.messenger.domain.model

/**
 * НОВОЕ (журнал действий администраторов, п.2 ТЗ): тип зафиксированного действия.
 */
enum class AdminActionType(val label: String) {
    MESSAGE_DELETED("Удаление сообщения"),
    USER_BANNED("Бан пользователя"),
    USER_UNBANNED("Разбан пользователя"),
    USER_KICKED("Исключение участника"),
    ADMIN_ADDED("Назначение администратора"),
    ADMIN_REMOVED("Снятие администратора"),
    ROLE_ASSIGNED("Назначение роли"),
    ROLE_REMOVED("Снятие роли"),
    CUSTOM_ROLE_CREATED("Создание роли"),
    CUSTOM_ROLE_EDITED("Изменение прав роли"),
    CUSTOM_ROLE_DELETED("Удаление роли"),
    CHAT_INFO_CHANGED("Изменение параметров чата"),
    MESSAGE_PINNED("Закрепление сообщения"),
    MESSAGE_UNPINNED("Открепление сообщения"),
    USER_INVITED("Приглашение участника")
}

/**
 * Одна запись журнала действий администраторов чата/канала.
 * Хранится в подколлекции chats/{chatId}/adminLog, сортировка по timestamp desc.
 */
data class AdminLogEntry(
    val id: String = "",
    val chatId: String,
    val actorId: String,
    val actorName: String,
    val actionType: AdminActionType,
    // Человекочитаемое уточнение (например, имя удалённого сообщения/пользователя),
    // формируется на стороне клиента при записи действия.
    val details: String = "",
    // ID пользователя, над которым совершено действие (для фильтрации "показать все
    // действия над Х"), может быть null для действий уровня чата (смена описания и т.п.).
    val targetUserId: String? = null,
    val targetUserName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** Параметры фильтрации журнала на экране AdminLogScreen. */
data class AdminLogFilter(
    val actionType: AdminActionType? = null,
    val actorId: String? = null,
    val fromMillis: Long? = null,
    val toMillis: Long? = null
)
