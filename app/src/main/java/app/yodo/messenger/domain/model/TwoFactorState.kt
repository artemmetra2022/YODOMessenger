package app.yodo.messenger.domain.model

/** Состояние двухфакторной аутентификации по email-коду. */
data class TwoFactorState(
    val enabled: Boolean = false
)
