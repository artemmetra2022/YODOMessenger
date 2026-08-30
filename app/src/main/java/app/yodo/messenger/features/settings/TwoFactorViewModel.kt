package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.TwoFactorState
import app.yodo.messenger.domain.repository.TwoFactorEmailSendResult
import app.yodo.messenger.domain.repository.TwoFactorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Экран настроек 2FA по email-коду. */
data class TwoFactorUiState(
    val isLoading: Boolean = true,
    val state: TwoFactorState = TwoFactorState(),
    /** Идёт подтверждение отключения: код уже отправлен, ждём ввод. */
    val awaitingDisableCode: Boolean = false,
    val maskedEmail: String? = null,
    val isSendingCode: Boolean = false,
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

    /** Включает 2FA — при следующем входе на новом устройстве придёт код на почту. */
    fun enable() {
        viewModelScope.launch {
            val ok = twoFactorRepository.enable()
            if (ok) {
                showSuccess("Двухфакторная аутентификация включена")
            } else {
                showError("Не удалось включить, попробуйте ещё раз")
            }
        }
    }

    /** Первый шаг отключения: запрашиваем код на почту для подтверждения. */
    fun startDisable() {
        _uiState.value = _uiState.value.copy(isSendingCode = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = twoFactorRepository.sendEmailCode()) {
                is TwoFactorEmailSendResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSendingCode = false,
                        awaitingDisableCode = true,
                        maskedEmail = result.maskedEmail
                    )
                }
                is TwoFactorEmailSendResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSendingCode = false)
                    showError(result.message)
                }
            }
        }
    }

    /** Второй шаг отключения: проверка кода из письма. */
    fun confirmDisable(emailCode: String) {
        viewModelScope.launch {
            val ok = twoFactorRepository.disable(emailCode)
            if (ok) {
                _uiState.value = _uiState.value.copy(awaitingDisableCode = false, maskedEmail = null)
                showSuccess("Двухфакторная аутентификация отключена")
            } else {
                showError("Неверный или устаревший код")
            }
        }
    }

    fun cancelDisable() {
        _uiState.value = _uiState.value.copy(awaitingDisableCode = false, maskedEmail = null)
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
