package app.yodo.messenger.domain.model

enum class AdminRole(val label: String) {
    ADMIN("Администратор"),
    MODERATOR("Модератор"),
    SUPPORT("Поддержка"),
    SUPER_ADMIN("Главный администратор")
}

data class AdminActivityPoint(
    val dayStart: Long = 0L,
    val logins: Int = 0,
    val messages: Int = 0,
    val total: Int = 0
)

data class AdminAssignment(
    val uid: String = "",
    val role: AdminRole = AdminRole.ADMIN,
    val assignedAt: Long = 0L,
    val assignedBy: String = "",
    val enabled: Boolean = true
)
