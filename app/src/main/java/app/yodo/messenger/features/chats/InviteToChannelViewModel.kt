package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ: приглашение контактов в канал — открывается из меню чата-канала (три точки).
 * Повторяет паттерн CreateGroupViewModel (мультивыбор пользователей с поиском),
 * но вместо создания чата — подписывает выбранных пользователей на существующий канал.
 */
data class InviteToChannelUiState(
    val searchResults: List<YodoUser> = emptyList(),
    val selectedUsers: List<YodoUser> = emptyList(),
    val isSearching: Boolean = false,
    val isInviting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class InviteToChannelViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(InviteToChannelUiState())
    val uiState: StateFlow<InviteToChannelUiState> = _uiState

    private val _invited = MutableStateFlow(false)
    val invited: StateFlow<Boolean> = _invited

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }

        _uiState.value = _uiState.value.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            delay(350)
            val results = userRepository.searchUsers(query)
            val selectedIds = _uiState.value.selectedUsers.map { it.uid }.toSet()
            _uiState.value = _uiState.value.copy(
                searchResults = results.filter { it.uid !in selectedIds },
                isSearching = false
            )
        }
    }

    fun toggleUser(user: YodoUser) {
        val current = _uiState.value.selectedUsers
        val updated = if (current.any { it.uid == user.uid }) {
            current.filter { it.uid != user.uid }
        } else {
            current + user
        }
        _uiState.value = _uiState.value.copy(
            selectedUsers = updated,
            searchResults = _uiState.value.searchResults.filter { it.uid != user.uid }
        )
    }

    fun removeSelected(user: YodoUser) {
        _uiState.value = _uiState.value.copy(
            selectedUsers = _uiState.value.selectedUsers.filter { it.uid != user.uid }
        )
    }

    fun sendInvites() {
        val members = _uiState.value.selectedUsers.map { it.uid }
        if (members.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Выберите хотя бы одного человека")
            return
        }
        _uiState.value = _uiState.value.copy(isInviting = true, errorMessage = null)
        viewModelScope.launch {
            // НОВОЕ (п.15): репозиторий возвращает имена тех, кого не пригласили —
            // они ограничили настройку приватности «Кто может приглашать в группы».
            val skipped = chatRepository.inviteUsersToChannel(chatId, members)
            _uiState.value = _uiState.value.copy(
                isInviting = false,
                errorMessage = if (skipped.isEmpty()) null
                else "Не приглашены (настройка «Кто может приглашать в группы»): " + skipped.joinToString(", ")
            )
            if (skipped.size < members.size) _invited.value = true
        }
    }

    fun consumeInvited() { _invited.value = false }
    fun consumeErrorMessage() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
