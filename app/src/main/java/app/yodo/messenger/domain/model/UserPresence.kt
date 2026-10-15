package app.yodo.messenger.domain.model

data class UserPresence(
    val isOnline: Boolean,
    val lastSeenMillis: Long
)
