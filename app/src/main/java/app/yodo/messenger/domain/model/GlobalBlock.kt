package app.yodo.messenger.domain.model

/**
 * НОВОЕ (AD): глобальная блокировка аккаунта администратором приложения (2 почты).
 * Хранится в коллекции globalBlocks/{uid}. Пока документ существует — пользователь
 * не может пользоваться приложением, ему показывается причина и кнопка обжалования.
 */
data class GlobalBlock(
    val userId: String = "",
    val reason: String = "",
    val blockedBy: String = "",
    val blockedByName: String = "",
    val blockedAt: Long = 0L
)
