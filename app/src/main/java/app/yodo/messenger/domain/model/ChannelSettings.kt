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
 * НОВОЕ (настройки канала: кто может писать комментарии): определяет, кому разрешено
 * оставлять комментарии к постам канала. Отдельно от allowComments — allowComments
 * включает/выключает комментарии целиком, а этот режим уточняет круг лиц, когда они
 * включены.
 * - EVERYONE — может писать любой участник канала (как раньше, поведение по умолчанию).
 * - SUBSCRIBERS_ONLY — только те, кто состоит в канале (подписан), не гости по ссылке
 *   на пост без подписки.
 * - ADMINS_ONLY — только владелец и администраторы канала.
 */
enum class CommentPermission {
    EVERYONE, SUBSCRIBERS_ONLY, ADMINS_ONLY;

    companion object {
        fun fromRaw(raw: String?): CommentPermission = when (raw?.uppercase()) {
            "SUBSCRIBERS_ONLY" -> SUBSCRIBERS_ONLY
            "ADMINS_ONLY" -> ADMINS_ONLY
            else -> EVERYONE
        }
    }

    val title: String
        get() = when (this) {
            EVERYONE -> "Все"
            SUBSCRIBERS_ONLY -> "Только подписчики"
            ADMINS_ONLY -> "Только администраторы"
        }

    val description: String
        get() = when (this) {
            EVERYONE -> "Комментировать может любой участник канала"
            SUBSCRIBERS_ONLY -> "Только те, кто подписан на канал"
            ADMINS_ONLY -> "Только владелец и администраторы канала"
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
    val allowLinkPreviews: Boolean = true,
    // НОВОЕ: кто именно может писать комментарии, когда allowComments == true.
    val commentPermission: CommentPermission = CommentPermission.EVERYONE
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
