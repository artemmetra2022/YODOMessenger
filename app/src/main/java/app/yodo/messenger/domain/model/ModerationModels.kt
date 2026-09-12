package app.yodo.messenger.domain.model

/** Тип сопоставления автоматического правила модерации. */
enum class ModerationMatchType(val label: String) {
    CONTAINS("Содержит текст"),
    REGEX("Регулярное выражение"),
    URL_DOMAIN("Домен ссылки")
}

/** Причина автоматического удаления. */
enum class ModerationDeleteReason(val label: String) {
    SPAM("Спам"),
    HARASSMENT("Оскорбление"),
    NSFW("NSFW"),
    RULES("Нарушение правил"),
    OTHER("Другое")
}

data class ModerationRule(
    val id: String = "",
    val name: String = "",
    val pattern: String = "",
    val matchType: ModerationMatchType = ModerationMatchType.CONTAINS,
    val reason: ModerationDeleteReason = ModerationDeleteReason.OTHER,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String = ""
)

data class DeletedMessageRecord(
    val id: String = "",
    val chatId: String,
    val messageId: String,
    val senderId: String,
    val senderName: String = "",
    val preview: String = "",
    val reason: ModerationDeleteReason = ModerationDeleteReason.OTHER,
    val deletedAt: Long = System.currentTimeMillis(),
    val restoreUntil: Long = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
    val deletedBy: String = "",
    val automatic: Boolean = false,
    val originalData: Map<String, Any?> = emptyMap()
) {
    fun canRestore(now: Long = System.currentTimeMillis()): Boolean = now < restoreUntil
}
