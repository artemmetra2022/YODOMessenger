package app.yodo.messenger.domain.model

/** Состояние облачного пароля (двухэтапной аутентификации) пользователя, как в Telegram. */
data class TwoFactorState(
    val enabled: Boolean = false,
    /** Необязательная подсказка, которую пользователь задаёт при установке пароля. */
    val hint: String? = null
)
