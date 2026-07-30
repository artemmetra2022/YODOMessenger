package app.yodo.messenger.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.TwoFactorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TwoFactorGateUiState {
    /** Проверяем, включена ли двухэтапная аутентификация у этого пользователя. */
    data object Checking : TwoFactorGateUiState()
    /** Не включена — можно сразу пропускать пользователя дальше. */
    data object NotRequired : TwoFactorGateUiState()
    /** Включена — показываем поле ввода пароля. */
    data class AwaitingPassword(val hint: String?, val error: String? = null, val isVerifying: Boolean = false) :
        TwoFactorGateUiState()
    /** Пароль верен — можно пропускать пользователя дальше. */
    data object Verified : TwoFactorGateUiState()
}

@HiltViewModel
class TwoFactorGateViewModel @Inject constructor(
    private val twoFactorRepository: TwoFactorRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TwoFactorGateUiState>(TwoFactorGateUiState.Checking)
    val uiState: StateFlow<TwoFactorGateUiState> = _uiState

    init {
        viewModelScope.launch {
            val state = twoFactorRepository.observeState().first()
            _uiState.value = if (state.enabled) {
                TwoFactorGateUiState.AwaitingPassword(hint = state.hint)
            } else {
                TwoFactorGateUiState.NotRequired
            }
        }
    }

    fun verify(password: String) {
        val current = _uiState.value
        if (current !is TwoFactorGateUiState.AwaitingPassword) return
        _uiState.value = current.copy(isVerifying = true, error = null)
        viewModelScope.launch {
            val ok = twoFactorRepository.verifyPassword(password)
            _uiState.value = if (ok) {
                TwoFactorGateUiState.Verified
            } else {
                current.copy(isVerifying = false, error = "Неверный пароль")
            }
        }
    }

    /** Пользователь отменяет ввод пароля — выходим из аккаунта, чтобы не оставлять сессию открытой. */
    fun cancelAndLogout() {
        authRepository.logout()
    }
}
