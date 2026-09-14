package app.yodo.messenger.domain.model

/**
 * НОВОЕ (п.19 ТЗ): ограничение возможности писать в поддержку — отдельная,
 * более мягкая мера, чем полный [GlobalBlock]. Пользователь остаётся внутри
 * приложения и может пользоваться всем остальным, но не может отправлять
 * новые сообщения в чат поддержки, пока ограничение действует.
 *
 * Хранится в коллекции supportRestrictions/{uid}. expiresAt == null означает
 * ограничение навсегда, иначе — метка времени, после которой оно перестаёт
 * действовать (проверяется и на клиенте, и должна проверяться в firestore.rules).
 */
data class SupportRestriction(
    val userId: String = "",
    val reason: String = "",
    val restrictedBy: String = "",
    val restrictedByName: String = "",
    val restrictedAt: Long = 0L,
    // null => ограничение бессрочное ("навсегда")
    val expiresAt: Long? = null
) {
    /** true, если ограничение сейчас действует (бессрочное либо срок ещё не истёк). */
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt == null || expiresAt > now
}
