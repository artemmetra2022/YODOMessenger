package app.yodo.messenger.offline

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над Google Nearby Connections API с настоящей MESH-МАРШРУТИЗАЦИЕЙ.
 *
 * Устройство:
 * - автоматически подключается ко всем найденным соседям, образуя ячеистую сеть;
 * - ретранслирует чужие сообщения дальше (multi-hop);
 * - рассылает presence-беконы (HELLO) для построения топологии;
 * - подтверждает доставку личных сообщений (ACK).
 *
 * У каждого узла есть шестизначный номер (shortId) — стабильный и читаемый
 * идентификатор. При совпадении номеров в одной сети один из узлов автоматически
 * меняет свой номер, поэтому в пределах видимой mesh-сети номера не повторяются.
 */
@Singleton
class NearbyMessagingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SERVICE_ID = "app.yodo.messenger.OFFLINE_CHAT"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val PREFS = "offline_prefs"
        private const val KEY_NODE_ID = "mesh_node_id"
        private const val KEY_SHORT_SALT = "mesh_short_salt"
        private const val BEACON_INTERVAL_MS = 5000L
        // Формат имени endpoint'a: "<имя>\u0001<nodeId>\u0001<shortId>".
        private const val NAME_SEP = "\u0001"
    }

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** Стабильный внутренний идентификатор узла (не показывается пользователю). */
    private val myNodeId: String = run {
        prefs.getString(KEY_NODE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_NODE_ID, it).apply()
        }
    }

    // Соль для вычисления шестизначного номера (меняется при конфликте).
    private var shortSalt: Int = prefs.getInt(KEY_SHORT_SALT, 0)

    private val router = MeshRouter(myNodeId)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var beaconJob: Job? = null

    private val connectedEndpoints = ConcurrentHashMap<String, String>()
    private val endpointNames = ConcurrentHashMap<String, String>()
    private val handshakeNodeId = ConcurrentHashMap<String, String>()
    private val handshakeShort = ConcurrentHashMap<String, String>()
    private val pendingConnections = ConcurrentHashMap.newKeySet<String>()

    // ==== StateFlow для UI ====
    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _messages = MutableStateFlow<List<OfflineMessage>>(emptyList())
    val messages: StateFlow<List<OfflineMessage>> = _messages

    private val _meshNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val meshNodes: StateFlow<List<MeshNode>> = _meshNodes

    private val _neighborCount = MutableStateFlow(0)
    val neighborCount: StateFlow<Int> = _neighborCount

    /** Мой шестизначный номер — показывается пользователю и сообщается другу. */
    private val _myShortId = MutableStateFlow(deriveShort(shortSalt))
    val myShortId: StateFlow<String> = _myShortId

    private var myDisplayName = "Yodo User"
    private var isAdvertising = false
    private var isDiscovering = false

    /** Вычисляем стабильный 6-значный номер из nodeId и соли (100000..999999). */
    private fun deriveShort(salt: Int): String {
        val h = (myNodeId + "#" + salt).hashCode()
        val v = Math.floorMod(h, 900000) + 100000
        return v.toString()
    }

    fun startAdvertisingAndDiscovery(displayName: String) {
        myDisplayName = displayName.ifBlank { "Yodo User" }
        startAdvertising()
        startDiscovery()
        startBeacon()
    }

    private fun advertisedName(): String =
        myDisplayName + NAME_SEP + myNodeId + NAME_SEP + _myShortId.value

    /** Разбор имени: (имя, nodeId, shortId). Совместимо со старым форматом. */
    private fun parseAdvertisedName(raw: String): Triple<String, String, String> {
        val parts = raw.split(NAME_SEP)
        val name = parts.getOrNull(0) ?: raw
        val nodeId = parts.getOrNull(1) ?: ""
        val short = parts.getOrNull(2) ?: ""
        return Triple(name, nodeId, short)
    }

    private fun startAdvertising() {
        if (isAdvertising) return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            advertisedName(),
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            isAdvertising = true
        }.addOnFailureListener {
            isAdvertising = false
        }
    }

    private fun startDiscovery() {
        if (isDiscovering) return
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            isDiscovering = true
        }.addOnFailureListener {
            isDiscovering = false
        }
    }

    private fun startBeacon() {
        if (beaconJob?.isActive == true) return
        beaconJob = scope.launch {
            while (isActive) {
                delay(BEACON_INTERVAL_MS)
                sendBeacon()
            }
        }
    }

    fun connectTo(endpointId: String) {
        if (connectedEndpoints.containsKey(endpointId)) return
        if (!pendingConnections.add(endpointId)) return
        if (connectedEndpoints.isEmpty()) _connectionState.value = ConnectionState.CONNECTING
        connectionsClient.requestConnection(advertisedName(), endpointId, connectionLifecycleCallback)
            .addOnFailureListener { pendingConnections.remove(endpointId) }
    }

    /** Широковещательное сообщение всей mesh-сети. */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val packet = newPacket(MeshPacketType.MSG, MeshPacket.BROADCAST, text, MeshPacket.DEFAULT_TTL, now)
        router.markSeen(packet.packetId)
        broadcastToNeighbors(packet, exclude = null)
        _messages.value = _messages.value + OfflineMessage(
            id = packet.packetId,
            text = text,
            timestamp = now,
            isOutgoing = true,
            senderName = myDisplayName,
            hops = 0,
            delivered = false,
            isBroadcast = true
        )
    }

    /** Личное сообщение конкретному узлу — маршрутизация по таблице + ACK. */
    fun sendMessageTo(nodeId: String, text: String) {
        if (text.isBlank() || nodeId.isBlank()) return
        val now = System.currentTimeMillis()
        val packet = newPacket(MeshPacketType.MSG, nodeId, text, MeshPacket.DEFAULT_TTL, now)
        router.markSeen(packet.packetId)
        routeTowardsDestination(packet, exclude = null)
        _messages.value = _messages.value + OfflineMessage(
            id = packet.packetId,
            text = text,
            timestamp = now,
            isOutgoing = true,
            senderName = myDisplayName,
            targetShort = router.shortOf(nodeId),
            hops = 0,
            delivered = false,
            isBroadcast = false
        )
    }

    /**
     * НОВОЕ (батч 7): экстренный SOS-сигнал. Широковещательное сообщение
     * с максимальным TTL и пометкой «🆘 SOS» — расходится по всей mesh-сети.
     */
    fun sendSos(note: String) {
        val extra = note.trim()
        val body = if (extra.isBlank()) "🆘 SOS! Нужна помощь" else "🆘 SOS! $extra"
        val now = System.currentTimeMillis()
        val packet = newPacket(MeshPacketType.MSG, MeshPacket.BROADCAST, body, MeshPacket.DEFAULT_TTL + 4, now)
        router.markSeen(packet.packetId)
        broadcastToNeighbors(packet, exclude = null)
        _messages.value = _messages.value + OfflineMessage(
            id = packet.packetId,
            text = body,
            timestamp = now,
            isOutgoing = true,
            senderName = myDisplayName,
            hops = 0,
            delivered = false,
            isBroadcast = true
        )
    }

    /** НОВОЕ (батч 7): очистить локальную историю сообщений (только у себя). */
    fun clearMessages() {
        _messages.value = emptyList()
    }

    /** НОВОЕ (батч 7): сменить имя «на лету» — перезапуск advertising, чтобы соседи увидели новое имя. */
    fun updateDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed == myDisplayName) return
        myDisplayName = trimmed
        if (isAdvertising) {
            connectionsClient.stopAdvertising()
            isAdvertising = false
            startAdvertising()
        }
    }

    private fun newPacket(
        type: MeshPacketType,
        dstId: String,
        text: String,
        ttl: Int,
        now: Long,
        refId: String = ""
    ): MeshPacket = MeshPacket(
        type = type,
        packetId = UUID.randomUUID().toString(),
        srcId = myNodeId,
        srcName = myDisplayName,
        srcShort = _myShortId.value,
        dstId = dstId,
        ttl = ttl,
        hops = 0,
        text = text,
        path = listOf(myNodeId),
        refId = refId,
        originTimestamp = now
    )

    private fun sendBeacon() {
        if (connectedEndpoints.isEmpty()) return
        val packet = newPacket(MeshPacketType.HELLO, MeshPacket.BROADCAST, "", MeshPacket.BEACON_TTL, System.currentTimeMillis())
        router.markSeen(packet.packetId)
        broadcastToNeighbors(packet, exclude = null)
    }

    private fun broadcastToNeighbors(packet: MeshPacket, exclude: String?) {
        val bytes = packet.toBytes()
        for (endpointId in connectedEndpoints.keys) {
            if (endpointId != exclude) sendBytes(endpointId, bytes)
        }
    }

    private fun routeTowardsDestination(packet: MeshPacket, exclude: String?) {
        val nextHop = router.nextHop(packet.dstId)
        if (nextHop != null && nextHop != exclude && connectedEndpoints.containsKey(nextHop)) {
            sendBytes(nextHop, packet.toBytes())
        } else {
            broadcastToNeighbors(packet, exclude)
        }
    }

    private fun sendBytes(endpointId: String, bytes: ByteArray) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    fun disconnect() {
        for (endpointId in connectedEndpoints.keys) {
            connectionsClient.disconnectFromEndpoint(endpointId)
        }
        connectedEndpoints.clear()
        endpointNames.clear()
        handshakeNodeId.clear()
        handshakeShort.clear()
        pendingConnections.clear()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _neighborCount.value = 0
        _meshNodes.value = emptyList()
        _discoveredDevices.value = emptyList()
        isAdvertising = false
        isDiscovering = false
        startAdvertising()
        startDiscovery()
        startBeacon()
    }

    fun stopAll() {
        beaconJob?.cancel()
        beaconJob = null
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        isAdvertising = false
        isDiscovering = false
        connectedEndpoints.clear()
        endpointNames.clear()
        handshakeNodeId.clear()
        handshakeShort.clear()
        pendingConnections.clear()
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _neighborCount.value = 0
        _meshNodes.value = emptyList()
        _messages.value = emptyList()
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (peerName, peerNodeId, peerShort) = parseAdvertisedName(info.endpointName)
            endpointNames[endpointId] = peerName
            if (peerNodeId.isNotBlank()) handshakeNodeId[endpointId] = peerNodeId
            if (peerShort.isNotBlank()) handshakeShort[endpointId] = peerShort
            _discoveredDevices.value =
                _discoveredDevices.value.filter { it.endpointId != endpointId } +
                    NearbyDevice(endpointId = endpointId, displayName = peerName)

            // Авто-подключение с tie-break по nodeId: инициирует только одна сторона.
            if (peerNodeId.isBlank() || peerNodeId == myNodeId) return
            if (myNodeId < peerNodeId) connectTo(endpointId)
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredDevices.value = _discoveredDevices.value.filter { it.endpointId != endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val (peerName, peerNodeId, peerShort) = parseAdvertisedName(info.endpointName)
            if (peerName.isNotBlank()) endpointNames[endpointId] = peerName
            if (peerNodeId.isNotBlank()) handshakeNodeId[endpointId] = peerNodeId
            if (peerShort.isNotBlank()) handshakeShort[endpointId] = peerShort
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pendingConnections.remove(endpointId)
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val nodeId = handshakeNodeId[endpointId] ?: ""
                connectedEndpoints[endpointId] = nodeId
                if (nodeId.isNotBlank()) {
                    router.learnRoute(
                        nodeId,
                        endpointNames[endpointId] ?: "",
                        handshakeShort[endpointId] ?: "",
                        endpointId,
                        1
                    )
                }
                _connectionState.value = ConnectionState.CONNECTED
                updateMeshNodes()
                sendBeacon()
            } else {
                if (connectedEndpoints.isEmpty()) _connectionState.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            endpointNames.remove(endpointId)
            handshakeNodeId.remove(endpointId)
            handshakeShort.remove(endpointId)
            pendingConnections.remove(endpointId)
            router.removeEndpoint(endpointId)
            if (connectedEndpoints.isEmpty()) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            updateMeshNodes()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val raw = String(bytes, Charsets.UTF_8)
            val packet = MeshPacket.fromJson(raw)
            if (packet == null) {
                addIncoming(raw, endpointNames[endpointId] ?: "Собеседник", null, 0, true)
                return
            }
            handlePacket(endpointId, packet)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Для коротких BYTES-payload отслеживание прогресса не требуется.
        }
    }

    private fun handlePacket(fromEndpointId: String, packet: MeshPacket) {
        if (router.isDuplicate(packet.packetId)) return

        router.learnRoute(packet.srcId, packet.srcName, packet.srcShort, fromEndpointId, packet.hops + 1)
        resolveShortCollision(packet.srcId, packet.srcShort)
        updateMeshNodes()

        when (packet.type) {
            MeshPacketType.HELLO -> {
                forwardFlood(fromEndpointId, packet)
            }

            MeshPacketType.MSG -> {
                val isBroadcast = packet.dstId == MeshPacket.BROADCAST
                val forMe = isBroadcast || packet.dstId == myNodeId
                if (forMe) {
                    addIncoming(packet.text, packet.srcName, packet.srcShort, packet.hops, isBroadcast)
                    if (packet.dstId == myNodeId) sendAck(packet)
                }
                if (isBroadcast) {
                    forwardFlood(fromEndpointId, packet)
                } else if (packet.dstId != myNodeId) {
                    forwardUnicast(fromEndpointId, packet)
                }
            }

            MeshPacketType.ACK -> {
                if (packet.dstId == myNodeId) {
                    markDelivered(packet.refId)
                } else {
                    forwardUnicast(fromEndpointId, packet)
                }
            }
        }
    }

    private fun forwardFlood(fromEndpointId: String, packet: MeshPacket) {
        if (myNodeId in packet.path) return
        val nextTtl = packet.ttl - 1
        if (nextTtl <= 0) return
        val forwarded = packet.copy(ttl = nextTtl, hops = packet.hops + 1, path = packet.path + myNodeId)
        broadcastToNeighbors(forwarded, exclude = fromEndpointId)
    }

    private fun forwardUnicast(fromEndpointId: String, packet: MeshPacket) {
        if (myNodeId in packet.path) return
        val nextTtl = packet.ttl - 1
        if (nextTtl <= 0) return
        val forwarded = packet.copy(ttl = nextTtl, hops = packet.hops + 1, path = packet.path + myNodeId)
        routeTowardsDestination(forwarded, exclude = fromEndpointId)
    }

    private fun sendAck(original: MeshPacket) {
        val ack = newPacket(
            type = MeshPacketType.ACK,
            dstId = original.srcId,
            text = "",
            ttl = MeshPacket.DEFAULT_TTL,
            now = System.currentTimeMillis(),
            refId = original.packetId
        )
        router.markSeen(ack.packetId)
        routeTowardsDestination(ack, exclude = null)
    }

    private fun addIncoming(text: String, senderName: String, senderShort: String?, hops: Int, isBroadcast: Boolean) {
        _messages.value = _messages.value + OfflineMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            timestamp = System.currentTimeMillis(),
            isOutgoing = false,
            senderName = senderName,
            senderShort = senderShort,
            hops = hops,
            delivered = false,
            isBroadcast = isBroadcast
        )
    }

    private fun markDelivered(refId: String) {
        if (refId.isBlank()) return
        _messages.value = _messages.value.map {
            if (it.id == refId) it.copy(delivered = true) else it
        }
    }

    /**
     * Разрешение конфликта номеров: если чужой узел использует тот же номер,
     * что и мы, то сторона с большим nodeId меняет свой номер — так в сети
     * не останется двух одинаковых номеров.
     */
    private fun resolveShortCollision(otherNodeId: String, otherShort: String) {
        if (otherShort.isBlank() || otherNodeId == myNodeId) return
        if (otherShort == _myShortId.value && myNodeId > otherNodeId) {
            shortSalt += 1
            prefs.edit().putInt(KEY_SHORT_SALT, shortSalt).apply()
            _myShortId.value = deriveShort(shortSalt)
            // Перезапускаем advertising, чтобы соседи увидели новый номер.
            connectionsClient.stopAdvertising()
            isAdvertising = false
            startAdvertising()
        }
    }

    private fun updateMeshNodes() {
        val now = System.currentTimeMillis()
        val nodes = LinkedHashMap<String, MeshNode>()
        for ((endpointId, nodeId) in connectedEndpoints) {
            if (nodeId.isBlank()) continue
            val name = endpointNames[endpointId] ?: router.nameOf(nodeId) ?: "Узел"
            val short = handshakeShort[endpointId] ?: router.shortOf(nodeId) ?: ""
            nodes[nodeId] = MeshNode(nodeId, name, short, 1, true, now)
        }
        for ((nodeId, route) in router.routesSnapshot()) {
            if (nodeId == myNodeId || nodes.containsKey(nodeId)) continue
            val name = router.nameOf(nodeId) ?: "Узел"
            val short = router.shortOf(nodeId) ?: ""
            nodes[nodeId] = MeshNode(nodeId, name, short, route.hopCount, false, route.updatedAt)
        }
        val list = nodes.values.sortedWith(compareBy({ it.hopCount }, { it.name }))
        _meshNodes.value = list
        val neighbors = connectedEndpoints.count { it.value.isNotBlank() }
        _neighborCount.value = neighbors
        _connectedDeviceName.value = if (connectedEndpoints.isEmpty()) {
            null
        } else {
            "Mesh · соседей: $neighbors, всего узлов: ${list.size}"
        }
    }
}
