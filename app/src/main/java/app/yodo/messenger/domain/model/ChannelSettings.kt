package app.yodo.messenger.domain.model

/**
 * НОВОЕ (режимы доступа каналов): режим доступа к каналу.
 * - OPEN — публичный открытый: канал виден всем в поиске, присоединиться может любой.
 * - MODERATED — публичный модерируемый: виден всем в поиске, но присоединение — по заявке
 *   (владелец/админ одобряет или отклоняет заявки).
 * - HIDDEN — скрытый: не виден в поиске, попасть можно только по прямой ссылке/приглашению/QR.
 */
enum class ChannelAccessMode {
    OPEN, MODERATED, HIDDEN;

    companion object {
        fun fromRaw(raw: String?): ChannelAccessMode = when (raw?.uppercase()) {
            "MODERATED" -> MODERATED
            "HIDDEN" -> HIDDEN
            else -> OPEN
        }
    }

    val title: String
        get() = when (this) {
            OPEN -> "Публичный открытый"
            MODERATED -> "Публичный модерируемый"
            HIDDEN -> "Скрытый"
        }

    val description: String
        get() = when (this) {
            OPEN -> "Виден всем в поиске, присоединиться может каждый"
            MODERATED -> "Виден всем, присоединение по заявке (с одобрением)"
            HIDDEN -> "Не виден в поиске — только по ссылке, приглашению или QR-коду"
        }
}

/**
 * НОВОЕ (ограничения канала): гибкий набор ограничений, задаваемых владельцем/админом
 * в настройках канала. Все поля по умолчанию разрешены (true) — так существующие каналы
 * без этих полей ведут себя как раньше.
 */
data class ChannelRestrictions(
    val allowForwarding: Boolean = true,
    val allowComments: Boolean = true,
    val allowReactions: Boolean = true,
    val allowSaving: Boolean = true,
    val allowLinkPreviews: Boolean = true
) {
    companion object {
        val DEFAULT = ChannelRestrictions()
    }
}

/**
 * НОВОЕ (модерируемые каналы): заявка на вступление в канал.
 */
data class JoinRequest(
    val userId: String,
    val displayName: String,
    val username: String?,
    val avatarBase64: String?,
    val photoUrl: String?,
    val requestedAt: Long
)
