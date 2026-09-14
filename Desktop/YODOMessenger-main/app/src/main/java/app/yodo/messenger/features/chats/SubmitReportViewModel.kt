package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ReportReason
import app.yodo.messenger.domain.repository.ReportActionResult
import app.yodo.messenger.domain.repository.ReportRepository
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubmitReportUiState(
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Лёгкий ViewModel для диалога "Пожаловаться" — используется как из меню сообщения,
 * так и из профиля пользователя. Не завязан на конкретный chatId через SavedStateHandle,
 * т.к. вызывается модально с разных экранов.
 */
@HiltViewModel
class SubmitReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitReportUiState())
    val uiState: StateFlow<SubmitReportUiState> = _uiState

    private val _resolvedTargetName = MutableStateFlow<String?>(null)
    val resolvedTargetName: StateFlow<String?> = _resolvedTargetName

    fun resolveTargetName(userId: String) {
        viewModelScope.launch {
            _resolvedTargetName.value = userRepository.getUserById(userId)?.displayName
        }
    }

    fun submitMessageReport(
        chatId: String,
        messageId: String,
        messagePreview: String,
        targetUserId: String,
        targetUserName: String,
        reason: ReportReason,
        customReasonText: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = SubmitReportUiState(isSubmitting = true)
            when (val result = reportRepository.reportMessage(
                chatId, messageId, messagePreview, targetUserId, targetUserName, reason, customReasonText
            )) {
                is ReportActionResult.Success -> _uiState.value = SubmitReportUiState(submitted = true)
                is ReportActionResult.Error -> _uiState.value = SubmitReportUiState(errorMessage = result.message)
            }
        }
    }

    fun submitUserReport(
        chatId: String,
        targetUserId: String,
        targetUserName: String,
        reason: ReportReason,
        customReasonText: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = SubmitReportUiState(isSubmitting = true)
            when (val result = reportRepository.reportUser(chatId, targetUserId, targetUserName, reason, customReasonText)) {
                is ReportActionResult.Success -> _uiState.value = SubmitReportUiState(submitted = true)
                is ReportActionResult.Error -> _uiState.value = SubmitReportUiState(errorMessage = result.message)
            }
        }
    }

    fun reset() { _uiState.value = SubmitReportUiState() }
}
