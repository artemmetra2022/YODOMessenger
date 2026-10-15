package app.yodo.messenger.domain.model

/**
 * Сессия пользователя на конкретном устройстве.
 * Хранится в Firestore: users/{uid}/sessions/{sessionId}
 */
data class DeviceSession(
    val sessionId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val lastActiveAt: Long,
    val createdAt: Long,
    val isCurrent: Boolean = false
)
