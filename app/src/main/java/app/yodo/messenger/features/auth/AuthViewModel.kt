package app.yodo.messenger.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.AuthResult
import app.yodo.messenger.domain.repository.ResetPasswordResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    /** Аккаунт создан/найден, но email ещё не подтверждён — показываем экран ожидания. */
    data class RequiresEmailVerification(val email: String) : AuthUiState()
}

/** Отдельное состояние для экрана сброса пароля — не смешивается с [AuthUiState] экрана входа. */
sealed class ResetPasswordUiState {
    data object Idle : ResetPasswordUiState()
    data object Loading : ResetPasswordUiState()
    data object Success : ResetPasswordUiState()
    data class Error(val message: String) : ResetPasswordUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSettingsPreferences: UserSettingsPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _resetPasswordState = MutableStateFlow<ResetPasswordUiState>(ResetPasswordUiState.Idle)
    val resetPasswordState: StateFlow<ResetPasswordUiState> = _resetPasswordState

    // НОВОЕ (расширенные опросы): читается на RegisterScreen для отображения переключателя
    // с уже актуальным (общим для приложения) значением флага.
    val advancedPollsEnabled: StateFlow<Boolean> = userSettingsPreferences.advancedPollsEnabled.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false
    )

    // НОВОЕ (расширенные опросы): пользователь может включить/выключить опцию прямо на
    // экране регистрации — значение сразу попадает в общий DataStore-флаг, тот же,
    // что используется в настройках после входа.
    fun setAdvancedPollsEnabled(enabled: Boolean) {
        viewModelScope.launch { userSettingsPreferences.setAdvancedPollsEnabled(enabled) }
    }

    /** [emailOrUsername] — на экране входа пользователь может ввести и email, и username. */
    fun login(emailOrUsername: String, password: String) {
        if (emailOrUsername.isBlank()) {
            _uiState.value = AuthUiState.Error("Введите email или username")
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error("Введите пароль")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.login(emailOrUsername, password)) {
                is AuthResult.Success -> _uiState.value = AuthUiState.Success
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(result.message)
                is AuthResult.RequiresEmailVerification ->
                    _uiState.value = AuthUiState.RequiresEmailVerification(result.email)
            }
        }
    }

    fun register(name: String, username: String, email: String, password: String) {
        if (name.isBlank()) {
            _uiState.value = AuthUiState.Error("Введите имя")
            return
        }
        if (!validateUsername(username)) return
        if (!validateCredentials(email, password)) return

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.register(name, username, email, password)) {
                is AuthResult.Success -> _uiState.value = AuthUiState.Success
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(result.message)
                is AuthResult.RequiresEmailVerification ->
                    _uiState.value = AuthUiState.RequiresEmailVerification(result.email)
            }
        }
    }

    /** Повторная отправка письма с подтверждением с экрана ожидания. */
    fun resendVerificationEmail() {
        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is AuthResult.RequiresEmailVerification ->
                    _uiState.value = AuthUiState.RequiresEmailVerification(result.email)
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(result.message)
                is AuthResult.Success -> Unit
            }
        }
    }

    /** Проверка по кнопке "Я подтвердил почту" — перечитывает пользователя с сервера. */
    fun checkEmailVerified() {
        val email = (uiState.value as? AuthUiState.RequiresEmailVerification)?.email.orEmpty()
        viewModelScope.launch {
            val verified = authRepository.reloadUser()
            _uiState.value = if (verified) {
                AuthUiState.Success
            } else {
                _notVerifiedYetEvent.value = true
                AuthUiState.RequiresEmailVerification(email)
            }
        }
    }

    // НОВОЕ: одноразовый сигнал для показа "Почта ещё не подтверждена" на UI без
    // подмешивания этого текста в основной AuthUiState (чтобы не терять email в состоянии).
    private val _notVerifiedYetEvent = MutableStateFlow(false)
    val notVerifiedYetEvent: StateFlow<Boolean> = _notVerifiedYetEvent

    fun consumeNotVerifiedYetEvent() {
        _notVerifiedYetEvent.value = false
    }

    private fun validateUsername(username: String): Boolean {
        // Нормализуем так же, как в репозитории: убираем «@», приводим к строчным.
        // Это гарантирует, что валидация в VM и в AuthRepositoryImpl используют
        // одно и то же правило и пользователь видит ошибку ещё до сетевого запроса.
        val normalized = username.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) {
            _uiState.value = AuthUiState.Error("Введите username")
            return false
        }
        if (!normalized.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            _uiState.value = AuthUiState.Error(
                "Username: 3-20 символов, только латиница, цифры и \"_\""
            )
            return false
        }
        return true
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    /** Разлогинивает недоподтверждённого пользователя перед возвратом на экран входа. */
    fun logoutAndReturnToLogin() {
        authRepository.logout()
        resetState()
    }

    /** [emailOrUsername] — как и при входе, можно ввести и email, и username. */
    fun resetPassword(emailOrUsername: String) {
        if (emailOrUsername.isBlank()) {
            _resetPasswordState.value = ResetPasswordUiState.Error("Введите email или username")
            return
        }

        _resetPasswordState.value = ResetPasswordUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.resetPassword(emailOrUsername)) {
                is ResetPasswordResult.Success -> _resetPasswordState.value = ResetPasswordUiState.Success
                is ResetPasswordResult.Error -> _resetPasswordState.value = ResetPasswordUiState.Error(result.message)
            }
        }
    }

    fun resetPasswordState() {
        _resetPasswordState.value = ResetPasswordUiState.Idle
    }

    fun loginWithGoogle(idToken: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.loginWithGoogle(idToken)) {
                is AuthResult.Success -> _uiState.value = AuthUiState.Success
                is AuthResult.Error -> _uiState.value = AuthUiState.Error(result.message)
            }
        }
    }

    private fun validateCredentials(email: String, password: String): Boolean {
        if (email.isBlank() || !email.contains("@")) {
            _uiState.value = AuthUiState.Error("Введите корректный email")
            return false
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Пароль должен содержать минимум 6 символов")
            return false
        }
        return true
    }
}
