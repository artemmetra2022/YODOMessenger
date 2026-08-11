package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.TwoFactorState
import app.yodo.messenger.domain.repository.TwoFactorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Экран настроек двухэтапной аутентификации (облачный пароль, аналог Telegram). */
data class TwoFactorUiState(
    val isLoading: Boolean = true,
    val state: TwoFactorState = TwoFactorState(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class TwoFactorViewModel @Inject constructor(
    private val twoFactorRepository: TwoFactorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TwoFactorUiState())
    val uiState: StateFlow<TwoFactorUiState> = _uiState

    init {
        viewModelScope.launch {
            twoFactorRepository.observeState().collect { state ->
                _uiState.value = _uiState.value.copy(isLoading = false, state = state)
            }
        }
    }

    /** Включает облачный пароль впервые. */
    fun enablePassword(newPassword: String, confirmPassword: String, hint: String?) {
        if (newPassword.isBlank()) {
            showError("Введите пароль")
            return
        }
        if (newPassword != confirmPassword) {
            showError("Пароли не совпадают")
            return
        }
        viewModelScope.launch {
            val ok = twoFactorRepository.setPassword(newPassword, hint)
            if (ok) {
                showSuccess("Пароль включён")
            } else {
                showError("Не удалось включить пароль, попробуйте ещё раз")
            }
        }
    }

    /** Меняет уже установленный пароль на новый (требует текущий пароль). */
    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String, hint: String?) {
        if (newPassword.isBlank()) {
            showError("Введите новый пароль")
            return
        }
        if (newPassword != confirmPassword) {
            showError("Пароли не совпадают")
            return
        }
        viewModelScope.launch {
            val verified = twoFactorRepository.verifyPassword(currentPassword)
            if (!verified) {
                showError("Неверный текущий пароль")
                return@launch
            }
            val ok = twoFactorRepository.setPassword(newPassword, hint)
            if (ok) {
                showSuccess("Пароль изменён")
            } else {
                showError("Не удалось изменить пароль, попробуйте ещё раз")
            }
        }
    }

    /** Отключает облачный пароль (требует текущий пароль для подтверждения). */
    fun disable(currentPassword: String) {
        viewModelScope.launch {
            val ok = twoFactorRepository.disable(currentPassword)
            if (ok) {
                showSuccess("Пароль отключён")
            } else {
                showError("Неверный пароль")
            }
        }
    }

    /** Снэкбар показан — очищаем сообщение, чтобы не показать его повторно. */
    fun consumeMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message, successMessage = null)
    }

    private fun showSuccess(message: String) {
        _uiState.value = _uiState.value.copy(successMessage = message, errorMessage = null)
    }
}
