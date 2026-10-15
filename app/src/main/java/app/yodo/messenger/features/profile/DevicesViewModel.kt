package app.yodo.messenger.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.DeviceSession
import app.yodo.messenger.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val sessions: List<DeviceSession> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val terminatingId: String? = null  // sessionId в процессе удаления
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState

    val currentSessionId: String = sessionRepository.getCurrentSessionId()

    init {
        // Обновляем запись о текущем сеансе при открытии экрана
        viewModelScope.launch {
            sessionRepository.updateCurrentSession()
        }

        // Подписываемся на поток сессий в реальном времени
        viewModelScope.launch {
            sessionRepository.observeSessions()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Не удалось загрузить список устройств"
                    )
                }
                .collect { sessions ->
                    _uiState.value = _uiState.value.copy(
                        sessions = sessions,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    fun terminateSession(sessionId: String) {
        if (sessionId == currentSessionId) return
        _uiState.value = _uiState.value.copy(terminatingId = sessionId)
        viewModelScope.launch {
            sessionRepository.terminateSession(sessionId)
            _uiState.value = _uiState.value.copy(terminatingId = null)
        }
    }

    fun terminateAllOtherSessions() {
        val others = _uiState.value.sessions.filter { !it.isCurrent }
        if (others.isEmpty()) return
        viewModelScope.launch {
            others.forEach { session ->
                sessionRepository.terminateSession(session.sessionId)
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
