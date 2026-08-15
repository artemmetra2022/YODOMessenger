package app.yodo.messenger.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.TwoFactorEmailSendResult
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
    /** Включена — показываем поле ввода пароля (первый шаг). */
    data class AwaitingPassword(val hint: String?, val error: String? = null, val isVerifying: Boolean = false) :
        TwoFactorGateUiState()
    /**
     * Пароль верен, код на почту отправляется/отправлен — показываем поле
     * ввода 6-значного кода (второй шаг).
     */
    data class AwaitingEmailCode(
        val maskedEmail: String,
        val error: String? = null,
        val isVerifying: Boolean = false,
        val isResending: Boolean = false,
        val infoMessage: String? = null
    ) : TwoFactorGateUiState()
    /** Оба шага пройдены — можно пропускать пользователя дальше. */
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

    /** Первый шаг: проверка облачного пароля. При успехе сразу запрашивает email-код. */
    fun verify(password: String) {
        val current = _uiState.value
        if (current !is TwoFactorGateUiState.AwaitingPassword) return
        _uiState.value = current.copy(isVerifying = true, error = null)
        viewModelScope.launch {
            val ok = twoFactorRepository.verifyPassword(password)
            if (!ok) {
                _uiState.value = current.copy(isVerifying = false, error = "Неверный пароль")
                return@launch
            }
            requestEmailCode(isResend = false)
        }
    }

    /** Запрашивает (или повторно запрашивает) отправку 6-значного кода на почту. */
    fun resendEmailCode() {
        val current = _uiState.value
        if (current !is TwoFactorGateUiState.AwaitingEmailCode) return
        _uiState.value = current.copy(isResending = true, error = null, infoMessage = null)
        viewModelScope.launch { requestEmailCode(isResend = true) }
    }

    private suspend fun requestEmailCode(isResend: Boolean) {
        when (val result = twoFactorRepository.sendEmailCode()) {
            is TwoFactorEmailSendResult.Success -> {
                _uiState.value = TwoFactorGateUiState.AwaitingEmailCode(
                    maskedEmail = result.maskedEmail,
                    infoMessage = if (isResend) "Мы отправили новый код" else null
                )
            }
            is TwoFactorEmailSendResult.Error -> {
                val current = _uiState.value
                _uiState.value = if (current is TwoFactorGateUiState.AwaitingEmailCode) {
                    current.copy(isResending = false, error = result.message)
                } else {
                    // Ошибка при первой отправке (сразу после пароля) — остаёмся
                    // на экране пароля с сообщением об ошибке, чтобы не застрять
                    // на пустом экране кода без адреса почты.
                    TwoFactorGateUiState.AwaitingPassword(hint = null, error = result.message)
                }
            }
        }
    }

    /** Второй шаг: проверка 6-значного кода из письма. */
    fun verifyEmailCode(code: String) {
        val current = _uiState.value
        if (current !is TwoFactorGateUiState.AwaitingEmailCode) return
        _uiState.value = current.copy(isVerifying = true, error = null, infoMessage = null)
        viewModelScope.launch {
            val ok = twoFactorRepository.verifyEmailCode(code)
            _uiState.value = if (ok) {
                TwoFactorGateUiState.Verified
            } else {
                current.copy(isVerifying = false, error = "Неверный или устаревший код")
            }
        }
    }

    /** Пользователь отменяет вход — выходим из аккаунта, чтобы не оставлять сессию открытой. */
    fun cancelAndLogout() {
        authRepository.logout()
    }
}
