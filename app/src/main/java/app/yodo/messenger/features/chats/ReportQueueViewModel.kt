package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.Report
import app.yodo.messenger.domain.model.ReportResolution
import app.yodo.messenger.domain.model.ReportStatus
import app.yodo.messenger.domain.repository.ReportActionResult
import app.yodo.messenger.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportQueueUiState(
    val isLoading: Boolean = true,
    val statusFilter: ReportStatus? = ReportStatus.PENDING,
    val reports: List<Report> = emptyList()
)

@HiltViewModel
class ReportQueueViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(ReportQueueUiState())
    val uiState: StateFlow<ReportQueueUiState> = _uiState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init { observe() }

    private fun observe() {
        viewModelScope.launch {
            reportRepository.observeReports(chatId, _uiState.value.statusFilter).collect { reports ->
                _uiState.value = _uiState.value.copy(isLoading = false, reports = reports)
            }
        }
    }

    fun setStatusFilter(status: ReportStatus?) {
        _uiState.value = _uiState.value.copy(statusFilter = status, isLoading = true)
        observe()
    }

    fun consumeErrorMessage() { _errorMessage.value = null }
}

data class ReportDetailUiState(
    val isLoading: Boolean = true,
    val report: Report? = null,
    val comments: List<app.yodo.messenger.domain.model.ReportComment> = emptyList(),
    val isSubmittingAction: Boolean = false
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val reportId: String = checkNotNull(savedStateHandle["reportId"])

    private val _uiState = MutableStateFlow(ReportDetailUiState())
    val uiState: StateFlow<ReportDetailUiState> = _uiState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _actionCompleted = MutableStateFlow(false)
    val actionCompleted: StateFlow<Boolean> = _actionCompleted

    init {
        refresh()
        viewModelScope.launch {
            reportRepository.observeReportComments(chatId, reportId).collect { comments ->
                _uiState.value = _uiState.value.copy(comments = comments)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val report = reportRepository.getReport(chatId, reportId)
            _uiState.value = _uiState.value.copy(isLoading = false, report = report)
        }
    }

    fun addComment(text: String) {
        viewModelScope.launch {
            when (val result = reportRepository.addReportComment(chatId, reportId, text)) {
                is ReportActionResult.Error -> _errorMessage.value = result.message
                else -> {}
            }
        }
    }

    fun dismiss(comment: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingAction = true)
            when (val result = reportRepository.dismissReport(chatId, reportId, comment)) {
                is ReportActionResult.Success -> { _actionCompleted.value = true }
                is ReportActionResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmittingAction = false)
                    _errorMessage.value = result.message
                }
            }
        }
    }

    fun resolve(resolution: ReportResolution, comment: String, deleteMessage: Boolean, banUser: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingAction = true)
            when (val result = reportRepository.resolveReport(chatId, reportId, resolution, comment, deleteMessage, banUser)) {
                is ReportActionResult.Success -> { _actionCompleted.value = true }
                is ReportActionResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmittingAction = false)
                    _errorMessage.value = result.message
                }
            }
        }
    }

    fun consumeErrorMessage() { _errorMessage.value = null }
}
