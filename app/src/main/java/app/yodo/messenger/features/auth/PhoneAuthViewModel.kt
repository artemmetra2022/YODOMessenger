package app.yodo.messenger.features.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.AuthResult
import app.yodo.messenger.domain.repository.PhoneAuthRepository
import app.yodo.messenger.domain.repository.PhoneVerificationCallbacks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PhoneAuthUiState {
    data object EnterPhone : PhoneAuthUiState()
    data object SendingCode : PhoneAuthUiState()
    data class CodeSent(val verificationId: String) : PhoneAuthUiState()
    data object VerifyingCode : PhoneAuthUiState()
    data object Success : PhoneAuthUiState()
    data class Error(val message: String) : PhoneAuthUiState()
}

@HiltViewModel
class PhoneAuthViewModel @Inject constructor(
    private val phoneAuthRepository: PhoneAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PhoneAuthUiState>(PhoneAuthUiState.EnterPhone)
    val uiState: StateFlow<PhoneAuthUiState> = _uiState

    // Храним отдельно от uiState, чтобы кнопка "Подтвердить" оставалась рабочей
    // даже если после неудачной попытки ввода кода состояние перешло в Error.
    private val _verificationId = MutableStateFlow<String?>(null)
    val verificationId: StateFlow<String?> = _verificationId

    private var currentPhoneNumber: String = ""

    fun sendCode(phoneNumber: String, activity: Activity) {
        if (!isValidPhone(phoneNumber)) {
            _uiState.value = PhoneAuthUiState.Error("Введите номер в формате +7XXXXXXXXXX")
            return
        }
        currentPhoneNumber = phoneNumber
        _uiState.value = PhoneAuthUiState.SendingCode

        phoneAuthRepository.startPhoneVerification(
            phoneNumber = phoneNumber,
            activity = activity,
            callbacks = object : PhoneVerificationCallbacks {
                override fun onCodeSent(verificationId: String) {
                    _verificationId.value = verificationId
                    _uiState.value = PhoneAuthUiState.CodeSent(verificationId)
                }

                override fun onAutoVerified() {
                    viewModelScope.launch {
                        when (val result = phoneAuthRepository.completeAutoVerification()) {
                            is AuthResult.Success -> _uiState.value = PhoneAuthUiState.Success
                            is AuthResult.Error -> _uiState.value = PhoneAuthUiState.Error(result.message)
                        }
                    }
                }

                override fun onFailed(message: String) {
                    _uiState.value = PhoneAuthUiState.Error(message)
                }
            }
        )
    }

    fun verifyCode(code: String) {
        val id = _verificationId.value
        if (id == null) {
            _uiState.value = PhoneAuthUiState.Error("Сначала запросите код")
            return
        }
        verifyCode(id, code)
    }

    fun verifyCode(verificationId: String, code: String) {
        if (code.length < 6) {
            _uiState.value = PhoneAuthUiState.Error("Код должен содержать 6 цифр")
            return
        }
        _uiState.value = PhoneAuthUiState.VerifyingCode
        viewModelScope.launch {
            when (val result = phoneAuthRepository.verifyCode(verificationId, code)) {
                is AuthResult.Success -> _uiState.value = PhoneAuthUiState.Success
                is AuthResult.Error -> _uiState.value = PhoneAuthUiState.Error(result.message)
            }
        }
    }

    fun resendCode(activity: Activity) {
        if (currentPhoneNumber.isNotBlank()) sendCode(currentPhoneNumber, activity)
    }

    private fun isValidPhone(phone: String): Boolean =
        phone.startsWith("+") && phone.drop(1).all { it.isDigit() } && phone.length >= 10
}
