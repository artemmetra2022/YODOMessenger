package app.yodo.messenger.offline

/** Устройство, обнаруженное поблизости через Nearby Connections (ещё не подключены). */
data class NearbyDevice(
    val endpointId: String,
    val displayName: String
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * НОВОЕ (mesh). Узел mesh-сети: либо прямой сосед (1 прыжок),
 * либо достижимый через ретрансляцию (hopCount прыжков).
 *
 * @param shortId шестизначный номер узла — по нему можно найти конкретного человека.
 */
data class MeshNode(
    val nodeId: String,
    val name: String,
    val shortId: String,
    val hopCount: Int,
    val isNeighbor: Boolean,
    val lastSeen: Long
)

data class OfflineMessage(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    /** true, если сообщение ещё не доставлено. */
    val isPending: Boolean = false,
    // НОВОЕ (mesh-метаданные):
    /** Имя отправителя (для входящих). */
    val senderName: String? = null,
    /** Номер отправителя (для входящих личных). */
    val senderShort: String? = null,
    /** Номер адресата (для исходящих личных). */
    val targetShort: String? = null,
    /** Сколько прыжков прошло сообщение до нас. */
    val hops: Int = 0,
    /** true, если получен ACK (для исходящих личных). */
    val delivered: Boolean = false,
    /** true — широковещательное, false — личное. */
    val isBroadcast: Boolean = true
)
