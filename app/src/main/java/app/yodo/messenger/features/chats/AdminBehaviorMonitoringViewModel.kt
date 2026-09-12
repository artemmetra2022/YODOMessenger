package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.BehaviorMonitoringSnapshot
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminBehaviorMonitoringViewModel @Inject constructor(
    private val userRepository: UserRepository,
    firebaseAuth: FirebaseAuth
) : ViewModel() {
    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in
            app.yodo.messenger.domain.repository.ChatRepository.ADMIN_EMAILS.map { it.lowercase() }

    private val _state = MutableStateFlow<BehaviorMonitoringSnapshot?>(null)
    val state: StateFlow<BehaviorMonitoringSnapshot?> = _state
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _state.value = userRepository.getBehaviorMonitoringSnapshot()
            _loading.value = false
        }
    }

    fun setNewcomerPause(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = userRepository.setNewcomerMessagingPauseEnabled(enabled)) {
                is ProfileUpdateResult.Error -> _error.value = result.message
                is ProfileUpdateResult.Success -> _state.value =
                    (_state.value ?: BehaviorMonitoringSnapshot()).copy(newcomerPauseEnabled = enabled)
            }
        }
    }

    fun consumeError() { _error.value = null }
}
