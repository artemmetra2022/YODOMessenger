package app.yodo.messenger.offline

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние экрана офлайн-чата с точки зрения идентификации пользователя.
 */
sealed class OfflineIdentityState {
    /** Пользователь авторизован — имя подтянуто из Firebase автоматически. */
    data class Online(val displayName: String) : OfflineIdentityState()
    /** Пользователь не авторизован — нужно показать поле ввода имени. */
    data class NeedsName(val savedName: String) : OfflineIdentityState()
    /** Имя введено, поиск запущен (гость уже нажал "Начать поиск"). */
    data class Searching(val displayName: String) : OfflineIdentityState()
}

private const val PREFS_NAME = "offline_prefs"
private const val KEY_OFFLINE_NAME = "offline_display_name"

@HiltViewModel
class OfflineChatViewModel @Inject constructor(
    private val nearbyManager: NearbyMessagingManager,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val discoveredDevices: StateFlow<List<NearbyDevice>> = nearbyManager.discoveredDevices
    val connectionState: StateFlow<ConnectionState> = nearbyManager.connectionState
    val connectedDeviceName: StateFlow<String?> = nearbyManager.connectedDeviceName
    val messages: StateFlow<List<OfflineMessage>> = nearbyManager.messages

    // НОВОЕ (mesh): узлы ячеистой сети и число прямых соседей.
    val meshNodes: StateFlow<List<MeshNode>> = nearbyManager.meshNodes
    val neighborCount: StateFlow<Int> = nearbyManager.neighborCount

    /** Мой шестизначный номер — его можно сообщить другу. */
    val myShortId: StateFlow<String> = nearbyManager.myShortId

    /** Выбранный адресат личных сообщений (null → пишем всей сети). */
    private val _selectedTargetNodeId = MutableStateFlow<String?>(null)
    val selectedTargetNodeId: StateFlow<String?> = _selectedTargetNodeId.asStateFlow()

    /** Состояние идентификации — определяет, показывать ли поле ввода имени. */
    private val _identityState = MutableStateFlow<OfflineIdentityState>(resolveIdentity())
    val identityState: StateFlow<OfflineIdentityState> = _identityState.asStateFlow()

    /**
     * Определяем начальное состояние:
     * - если пользователь залогинен → берём displayName + username из Firebase
     * - иначе → предлагаем ввести имя (или показываем сохранённое с прошлого раза)
     */
    private fun resolveIdentity(): OfflineIdentityState {
        val user = firebaseAuth.currentUser
        return if (user != null) {
            // Залогинен: строим отображаемое имя как "Имя (@username)" или просто "Имя"
            val name = buildOnlineName(
                displayName = user.displayName,
                // FirebaseAuth не хранит username напрямую — он в Firestore.
                // Здесь используем phoneNumber или email как запасной вариант,
                // полный username придёт через UserRepository если понадобится.
                fallback = user.phoneNumber ?: user.email
            )
            OfflineIdentityState.Online(name)
        } else {
            val saved = loadSavedOfflineName()
            OfflineIdentityState.NeedsName(savedName = saved)
        }
    }

    private fun buildOnlineName(displayName: String?, fallback: String?): String {
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: "Пользователь Yodo"
        return name
    }

    private fun loadSavedOfflineName(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_OFFLINE_NAME, "") ?: ""
    }

    private fun saveOfflineName(name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_OFFLINE_NAME, name).apply()
    }

    /**
     * Вызывается из UI, когда пользователь не авторизован и ввёл имя вручную.
     * Сохраняем имя и запускаем поиск.
     */
    fun startSearchingWithCustomName(name: String) {
        val trimmed = name.trim().ifBlank { "Гость-${(1000..9999).random()}" }
        saveOfflineName(trimmed)
        _identityState.value = OfflineIdentityState.Searching(displayName = trimmed)
        nearbyManager.startAdvertisingAndDiscovery(trimmed)
    }

    /**
     * Вызывается из UI после получения разрешений, когда пользователь уже авторизован
     * (его имя было определено автоматически).
     */
    fun startSearching() {
        val state = _identityState.value
        val name = when (state) {
            is OfflineIdentityState.Online -> state.displayName
            is OfflineIdentityState.NeedsName -> state.savedName.ifBlank { "Гость-${(1000..9999).random()}" }
            is OfflineIdentityState.Searching -> state.displayName
        }
        nearbyManager.startAdvertisingAndDiscovery(name)
    }

    fun connectTo(device: NearbyDevice) {
        nearbyManager.connectTo(device.endpointId)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val target = _selectedTargetNodeId.value
        if (target == null) {
            nearbyManager.sendMessage(text)
        } else {
            nearbyManager.sendMessageTo(target, text)
        }
    }

    /** Личное сообщение конкретному узлу mesh-сети (маршрутизация + ACK). */
    fun sendMessageTo(nodeId: String, text: String) {
        if (text.isBlank()) return
        nearbyManager.sendMessageTo(nodeId, text)
    }

    /** Выбрать адресата тапом по узлу в списке (null → снова всем). */
    fun selectTarget(nodeId: String?) {
        _selectedTargetNodeId.value = nodeId
    }

    /** Выбрать адресата по шестизначному номеру. Возвращает найденный узел или null. */
    fun selectTargetByShort(shortId: String): MeshNode? {
        val digits = shortId.trim()
        val node = meshNodes.value.firstOrNull { it.shortId == digits }
        if (node != null) _selectedTargetNodeId.value = node.nodeId
        return node
    }

    /** Найти узел по nodeId (для отображения выбранного адресата). */
    fun findNode(nodeId: String?): MeshNode? =
        if (nodeId == null) null else meshNodes.value.firstOrNull { it.nodeId == nodeId }

    fun disconnect() {
        nearbyManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        nearbyManager.stopAll()
    }
}
