package app.yodo.messenger.offline

/** Устройство, обнаруженное поблизости через Nearby Connections (ещё не подключены). */
data class NearbyDevice(
    val endpointId: String,
    val displayName: String
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class OfflineMessage(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    /** true, если сообщение ещё не доставлено (соединение разорвалось до подтверждения). */
    val isPending: Boolean = false
)
