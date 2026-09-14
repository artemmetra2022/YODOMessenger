package app.yodo.messenger.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.GlobalBlock
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.ReportActionResult
import app.yodo.messenger.domain.repository.ReportRepository
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (AD): экран-заглушка для глобально заблокированного пользователя.
 * Наблюдает статус блокировки и позволяет отправить обжалование.
 */
@HiltViewModel
class GlobalBlockViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val reportRepository: ReportRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val globalBlock: StateFlow<GlobalBlock?> =
        userRepository.observeMyGlobalBlock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _appealSending = MutableStateFlow(false)
    val appealSending: StateFlow<Boolean> = _appealSending

    private val _appealSent = MutableStateFlow(false)
    val appealSent: StateFlow<Boolean> = _appealSent

    private val _appealError = MutableStateFlow<String?>(null)
    val appealError: StateFlow<String?> = _appealError

    fun sendAppeal(text: String, photoBase64: String?) {
        viewModelScope.launch {
            _appealSending.value = true
            _appealError.value = null
            when (val result = reportRepository.submitAppeal(text, photoBase64)) {
                is ReportActionResult.Success -> { _appealSent.value = true }
                is ReportActionResult.Error -> { _appealError.value = result.message }
            }
            _appealSending.value = false
        }
    }

    fun clearAppealError() { _appealError.value = null }

    fun logout() { authRepository.logout() }
}
