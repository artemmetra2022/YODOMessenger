package app.yodo.messenger.domain.model

/**
 * НОВОЕ (п.15): общий тип для настроек приватности вида «кто может …».
 *
 * EVERYONE — все пользователи.
 * CONTACTS — только знакомые: те, кто есть в вашем списке контактов YODO
 *            (добавлены по QR), либо с кем у вас уже есть личный чат.
 * NOBODY   — никто.
 */
enum class PrivacyWho {
    EVERYONE,
    CONTACTS,
    NOBODY;

    companion object {
        /** Безопасный разбор значения из Firestore (по умолчанию — EVERYONE). */
        fun fromString(value: String?): PrivacyWho =
            entries.firstOrNull { it.name == value } ?: EVERYONE
    }
}
