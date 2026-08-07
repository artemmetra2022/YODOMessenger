package app.yodo.messenger.domain.model

/**
 * НОВОЕ (система ролей): гранулярные права, которые можно выдать роли в группе/канале.
 * Хранится как Map<String, Boolean> в Firestore (ключ — имя константы Permission).
 */
enum class Permission(val label: String) {
    DELETE_MESSAGES("Удалять сообщения других участников"),
    BAN_USERS("Банить и снимать бан с пользователей"),
    INVITE_USERS("Приглашать новых участников"),
    PIN_MESSAGES("Закреплять сообщения"),
    EDIT_CHAT_INFO("Менять название, описание и аватар"),
    ADD_ADMINS("Назначать и снимать роли другим участникам"),
    POST_MESSAGES("Публиковать сообщения от имени канала"),
    VIEW_ADMIN_LOG("Просматривать журнал действий администраторов");

    companion object {
        fun defaultSetFor(builtIn: BuiltInRole): Set<Permission> = when (builtIn) {
            BuiltInRole.OWNER -> entries.toSet()
            BuiltInRole.MODERATOR -> setOf(
                DELETE_MESSAGES, BAN_USERS, INVITE_USERS, PIN_MESSAGES, VIEW_ADMIN_LOG
            )
            BuiltInRole.ASSISTANT -> setOf(
                DELETE_MESSAGES, INVITE_USERS, PIN_MESSAGES
            )
            BuiltInRole.CONTENT_EDITOR -> setOf(
                EDIT_CHAT_INFO, POST_MESSAGES, PIN_MESSAGES
            )
        }
    }
}

/**
 * Встроенные роли-шаблоны (п.1 ТЗ): Владелец, Модератор, Помощник, Редактор контента.
 * Пользователь может создавать и свои роли (CustomRole) поверх этого набора.
 */
enum class BuiltInRole(val displayName: String) {
    OWNER("Владелец"),
    MODERATOR("Модератор"),
    ASSISTANT("Помощник"),
    CONTENT_EDITOR("Редактор контента")
}

/**
 * Роль, назначенная конкретному участнику чата.
 * Если [customRoleId] не null — используется кастомная роль из ChatInfo.customRoles,
 * иначе применяется набор прав встроенной роли [builtIn].
 */
data class AssignedRole(
    val userId: String,
    val builtIn: BuiltInRole?,
    val customRoleId: String? = null
)

/** Кастомная роль, созданная владельцем/модератором для конкретного чата. */
data class CustomRole(
    val id: String,
    val name: String,
    val permissions: Set<Permission>,
    // Цвет бейджа роли в UI (hex ARGB), для визуального отличия ролей в списке участников.
    val colorHex: String = "#FF7C4DFF"
)

/**
 * Итоговый набор прав участника чата — используется во ViewModel для управления
 * видимостью действий в UI (например, кнопка "Удалить сообщение" показывается только
 * тем, у кого есть Permission.DELETE_MESSAGES).
 */
data class MemberPermissions(
    val userId: String,
    val roleName: String,
    val isOwner: Boolean,
    val permissions: Set<Permission>
) {
    fun has(permission: Permission): Boolean = isOwner || permission in permissions
}
