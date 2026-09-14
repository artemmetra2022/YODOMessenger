package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.GlobalAdminLogEntry
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (глобальный аудит-лог): показывает журнал действий Админки — глобальные
 * блокировки/разблокировки пользователей и изменения настроек приложения
 * (например, обязательное подтверждение email). Отдельно от AdminLogViewModel,
 * который показывает журнал конкретного чата/группы.
 */
@HiltViewModel
class AdminAuditLogViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _entries = MutableStateFlow<List<GlobalAdminLogEntry>>(emptyList())
    val entries: StateFlow<List<GlobalAdminLogEntry>> = _entries

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val pageSize = 50

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val page = userRepository.getGlobalAuditLog(limit = pageSize)
            _entries.value = page
            _hasMore.value = page.size == pageSize
            _isLoading.value = false
        }
    }

    fun loadMore() {
        val last = _entries.value.lastOrNull() ?: return
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val page = userRepository.getGlobalAuditLog(limit = pageSize, startAfterTimestamp = last.timestamp)
            _entries.value = _entries.value + page
            _hasMore.value = page.size == pageSize
            _isLoadingMore.value = false
        }
    }

    fun refresh() = load()
}
