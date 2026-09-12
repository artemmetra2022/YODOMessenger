package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.Report
import app.yodo.messenger.domain.model.ReportReason
import app.yodo.messenger.domain.model.ReportStatus
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.ReportRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (AC): глобальный раздел «Жалобы» для главных админов (2 почты).
 * Собирает все жалобы и обжалования по всем чатам в одном месте.
 */
@HiltViewModel
class ReportInboxViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS

    private val _statusFilter = MutableStateFlow<ReportStatus?>(ReportStatus.PENDING)
    val statusFilter: StateFlow<ReportStatus?> = _statusFilter

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports

    private val _reasonFilter = MutableStateFlow<ReportReason?>(null)
    val reasonFilter: StateFlow<ReportReason?> = _reasonFilter

    private val _reasonCounts = MutableStateFlow<Map<ReportReason, Int>>(emptyMap())
    val reasonCounts: StateFlow<Map<ReportReason, Int>> = _reasonCounts

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init { if (isAdmin) observe() }

    private fun observe() {
        viewModelScope.launch {
            reportRepository.observeAllReports(null).collect { list ->
                val status = _statusFilter.value
                val statusFiltered = if (status == null) list else list.filter { it.status == status }
                _reasonCounts.value = statusFiltered
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
                val reason = _reasonFilter.value
                _reports.value = if (reason == null) statusFiltered else statusFiltered.filter {
                    if (reason == ReportReason.OTHER) {
                        it.reason !in setOf(
                            ReportReason.SPAM, ReportReason.HARASSMENT,
                            ReportReason.NSFW, ReportReason.ADVERTISEMENT,
                            ReportReason.APPEAL
                        )
                    } else it.reason == reason
                }
                _isLoading.value = false
            }
        }
    }

    fun setStatusFilter(status: ReportStatus?) {
        _statusFilter.value = status
        _isLoading.value = true
        observe()
    }

    fun setReasonFilter(reason: ReportReason?) {
        _reasonFilter.value = reason
        observe()
    }
}
