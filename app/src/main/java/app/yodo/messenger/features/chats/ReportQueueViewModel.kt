package app.yodo.messenger.features.chats

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.Report
import app.yodo.messenger.domain.model.ReportResolution
import app.yodo.messenger.domain.model.ReportReason
import app.yodo.messenger.domain.model.ReportStatus
import app.yodo.messenger.domain.repository.ReportActionResult
import app.yodo.messenger.domain.repository.ReportRepository
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import app.yodo.messenger.data.worker.ModerationDeletionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportQueueUiState(
    val isLoading: Boolean = true,
    val statusFilter: ReportStatus? = ReportStatus.PENDING,
    val reasonFilter: ReportReason? = null,
    val reports: List<Report> = emptyList(),
    val reasonCounts: Map<ReportReason, Int> = emptyMap()
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
            reportRepository.observeReports(chatId, null).collect { allReports ->
                val status = _uiState.value.statusFilter
                val reason = _uiState.value.reasonFilter
                val statusFiltered = if (status == null) allReports else allReports.filter { it.status == status }
                val filtered = if (reason == null) statusFiltered else statusFiltered.filter {
                    if (reason == ReportReason.OTHER) {
                        it.reason !in setOf(
                            ReportReason.SPAM, ReportReason.HARASSMENT,
                            ReportReason.NSFW, ReportReason.ADVERTISEMENT,
                            ReportReason.APPEAL
                        )
                    } else it.reason == reason
                }
                val counts = statusFiltered
                    .filter { it.reason != ReportReason.APPEAL }
                    .groupingBy {
                        when (it.reason) {
                            ReportReason.SPAM -> ReportReason.SPAM
                            ReportReason.HARASSMENT -> ReportReason.HARASSMENT
                            ReportReason.NSFW -> ReportReason.NSFW
                            ReportReason.ADVERTISEMENT -> ReportReason.ADVERTISEMENT
                            else -> ReportReason.OTHER
                        }
                    }.eachCount()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    reports = filtered,
                    reasonCounts = counts
                )
            }
        }
    }

    fun setStatusFilter(status: ReportStatus?) {
        _uiState.value = _uiState.value.copy(statusFilter = status, isLoading = true)
        observe()
    }

    fun setReasonFilter(reason: ReportReason?) {
        _uiState.value = _uiState.value.copy(reasonFilter = reason, isLoading = false)
        observe()
    }

    fun consumeErrorMessage() { _errorMessage.value = null }
}

data class ReportDetailUiState(
    val isLoading: Boolean = true,
    val report: Report? = null,
    val comments: List<app.yodo.messenger.domain.model.ReportComment> = emptyList(),
    val contextMessages: List<app.yodo.messenger.domain.model.Message> = emptyList(),
    val isSubmittingAction: Boolean = false,
    val bulkDeletionScheduledAt: Long? = null
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val messageRepository: app.yodo.messenger.domain.repository.MessageRepository,
    @ApplicationContext private val appContext: Context,
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
            val contextMessages = if (report?.targetType == app.yodo.messenger.domain.model.ReportTargetType.MESSAGE &&
                report.targetMessageId != null) {
                messageRepository.getMessageContext(chatId, report.targetMessageId, 3)
            } else emptyList()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                report = report,
                contextMessages = contextMessages
            )
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

    fun resolve(resolution: ReportResolution, comment: String, deleteMessage: Boolean, banUser: Boolean, silentDelete: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingAction = true)
            when (val result = reportRepository.resolveReport(chatId, reportId, resolution, comment, deleteMessage, banUser, silentDelete)) {
                is ReportActionResult.Success -> { _actionCompleted.value = true }
                is ReportActionResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSubmittingAction = false)
                    _errorMessage.value = result.message
                }
            }
        }
    }

    fun cancelScheduledDeletion() {
        viewModelScope.launch {
            when (val result = reportRepository.cancelScheduledDeletion(chatId, reportId)) {
                is ReportActionResult.Error -> _errorMessage.value = result.message
                is ReportActionResult.Success -> refresh()
            }
        }
    }

    fun scheduleBulkDeletion(startAt: Long, endAt: Long): Long? {
        val report = _uiState.value.report ?: return null
        val executeAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
        val data = Data.Builder()
            .putString(ModerationDeletionWorker.KEY_CHAT_ID, chatId)
            .putString(ModerationDeletionWorker.KEY_USER_ID, report.targetUserId)
            .putLong(ModerationDeletionWorker.KEY_START_AT, startAt)
            .putLong(ModerationDeletionWorker.KEY_END_AT, endAt)
            .putLong(ModerationDeletionWorker.KEY_EXECUTE_AT, executeAt)
            .build()
        val request = OneTimeWorkRequestBuilder<ModerationDeletionWorker>()
            .setInitialDelay(24L, java.util.concurrent.TimeUnit.HOURS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "moderation_bulk_${chatId}_${report.targetUserId}_${startAt}_${endAt}",
            ExistingWorkPolicy.REPLACE,
            request
        )
        _uiState.value = _uiState.value.copy(bulkDeletionScheduledAt = executeAt)
        return executeAt
    }
}
