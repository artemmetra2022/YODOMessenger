package app.yodo.messenger.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineChatViewModel @Inject constructor(
    private val nearbyManager: NearbyMessagingManager,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val discoveredDevices: StateFlow<List<NearbyDevice>> = nearbyManager.discoveredDevices
    val connectionState: StateFlow<ConnectionState> = nearbyManager.connectionState
    val connectedDeviceName: StateFlow<String?> = nearbyManager.connectedDeviceName
    val messages: StateFlow<List<OfflineMessage>> = nearbyManager.messages

    fun startSearching() {
        val myName = firebaseAuth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Yodo User"
        nearbyManager.startAdvertisingAndDiscovery(myName)
    }

    fun connectTo(device: NearbyDevice) {
        nearbyManager.connectTo(device.endpointId)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        nearbyManager.sendMessage(text)
    }

    fun disconnect() {
        nearbyManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        nearbyManager.stopAll()
    }
}
