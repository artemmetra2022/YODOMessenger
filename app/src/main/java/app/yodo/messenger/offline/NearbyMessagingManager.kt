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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над Google Nearby Connections API — прямой обмен сообщениями между двумя
 * устройствами через Bluetooth/Wi-Fi Direct, БЕЗ интернета и без сервера.
 *
 * Важное ограничение (сознательно, честно): это не mesh-сеть — сообщение передаётся
 * только напрямую между двумя устройствами в радиусе связи (обычно 10-100 метров),
 * а не "прыгает" через посторонние устройства для увеличения дальности, как в Bridgefy.
 * Настоящий multi-hop mesh — отдельный, гораздо более сложный протокол.
 *
 * Для простоты MVP входящие подключения принимаются автоматически (без диалога
 * подтверждения) — приемлемо для 1:1 сценария, но стоит иметь в виду при большом
 * количестве незнакомых устройств поблизости.
 */
@Singleton
class NearbyMessagingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Уникальный идентификатор "канала" именно нашего приложения — чтобы Nearby
        // не путал наши устройства с другими приложениями, использующими тот же API.
        private const val SERVICE_ID = "app.yodo.messenger.OFFLINE_CHAT"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _messages = MutableStateFlow<List<OfflineMessage>>(emptyList())
    val messages: StateFlow<List<OfflineMessage>> = _messages

    private var connectedEndpointId: String? = null
    private var myDisplayName: String = "Yodo User"
    private var isAdvertising = false
    private var isDiscovering = false

    /** Начать одновременно "быть видимым" для других и искать их — стандартный режим для чата. */
    fun startAdvertisingAndDiscovery(displayName: String) {
        myDisplayName = displayName.ifBlank { "Yodo User" }
        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        if (isAdvertising) return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startAdvertising(
            myDisplayName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            isAdvertising = true
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
        }
    }

    fun connectTo(endpointId: String) {
        _connectionState.value = ConnectionState.CONNECTING
        connectionsClient.requestConnection(myDisplayName, endpointId, connectionLifecycleCallback)
    }

    fun sendMessage(text: String) {
        val endpointId = connectedEndpointId ?: return
        val payload = Payload.fromBytes(text.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)

        _messages.value = _messages.value + OfflineMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            timestamp = System.currentTimeMillis(),
            isOutgoing = true
        )
    }

    fun disconnect() {
        connectedEndpointId?.let { connectionsClient.disconnectFromEndpoint(it) }
        connectedEndpointId = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
    }

    /** Полностью остановить advertising/discovery — вызывать при выходе с экрана офлайн-чата. */
    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        isAdvertising = false
        isDiscovering = false
        connectedEndpointId = null
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        _messages.value = emptyList()
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val device = NearbyDevice(endpointId = endpointId, displayName = info.endpointName)
            _discoveredDevices.value = _discoveredDevices.value + device
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredDevices.value = _discoveredDevices.value.filter { it.endpointId != endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Упрощение MVP: принимаем подключение автоматически, без диалога подтверждения
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpointId = endpointId
                _connectionState.value = ConnectionState.CONNECTED
                val device = _discoveredDevices.value.firstOrNull { it.endpointId == endpointId }
                _connectedDeviceName.value = device?.displayName ?: "Собеседник"
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            _connectionState.value = ConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val text = String(bytes, Charsets.UTF_8)
                _messages.value = _messages.value + OfflineMessage(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = false
                )
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Для простых текстовых BYTES-payload прогресс передачи отслеживать не требуется
        }
    }
}
