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
    val blockedAt: Long = 0L,
    // НОВОЕ (санкции на срок): 0 — бессрочная блокировка, иначе — время
    // автоснятия (epoch ms). Клиент игнорирует блокировку с истёкшим сроком,
    // а веб-панель дочищает такие документы (без Cloud Functions).
    val expiresAt: Long = 0L,
    val durationMs: Long = 0L
) {
    val isExpired: Boolean
        get() = expiresAt > 0L && expiresAt <= System.currentTimeMillis()
}
