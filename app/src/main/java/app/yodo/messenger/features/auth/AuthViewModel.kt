package app.yodo.messenger.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

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
            }
        }
    }

    private fun validateUsername(username: String): Boolean {
        val normalized = username.trim().removePrefix("@")
        if (normalized.isBlank()) {
            _uiState.value = AuthUiState.Error("Введите username")
            return false
        }
        if (!normalized.matches(Regex("^[a-zA-Z0-9_]{3,20}$"))) {
            _uiState.value = AuthUiState.Error(
                "Username: 3-20 символов, только латинские буквы, цифры и \"_\""
            )
            return false
        }
        return true
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
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
