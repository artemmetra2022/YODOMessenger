package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.AdminActionType
import app.yodo.messenger.domain.model.AdminLogEntry
import app.yodo.messenger.domain.model.AdminLogFilter
import app.yodo.messenger.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminLogUiState(
    val isLoading: Boolean = true,
    val entries: List<AdminLogEntry> = emptyList(),
    val filter: AdminLogFilter = AdminLogFilter(),
    val canLoadMore: Boolean = true,
    val isLoadingMore: Boolean = false
)

private const val PAGE_SIZE = 30

@HiltViewModel
class AdminLogViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(AdminLogUiState())
    val uiState: StateFlow<AdminLogUiState> = _uiState

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val entries = chatRepository.getAdminLog(chatId, _uiState.value.filter, PAGE_SIZE)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                entries = entries,
                canLoadMore = entries.size >= PAGE_SIZE
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.canLoadMore || state.entries.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            val lastTimestamp = state.entries.last().timestamp
            val more = chatRepository.getAdminLog(chatId, state.filter, PAGE_SIZE, lastTimestamp)
            _uiState.value = _uiState.value.copy(
                entries = state.entries + more,
                isLoadingMore = false,
                canLoadMore = more.size >= PAGE_SIZE
            )
        }
    }

    fun setActionTypeFilter(actionType: AdminActionType?) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(actionType = actionType))
        load()
    }

    fun setActorFilter(actorId: String?) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(actorId = actorId))
        load()
    }

    fun setDateRangeFilter(fromMillis: Long?, toMillis: Long?) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(fromMillis = fromMillis, toMillis = toMillis))
        load()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(filter = AdminLogFilter())
        load()
    }
}
