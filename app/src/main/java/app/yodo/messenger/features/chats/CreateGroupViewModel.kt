package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupUiState(
    val searchResults: List<YodoUser> = emptyList(),
    val selectedUsers: List<YodoUser> = emptyList(),
    val isSearching: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState

    private val _createdChatId = MutableStateFlow<String?>(null)
    val createdChatId: StateFlow<String?> = _createdChatId

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

    fun createGroup(title: String) {
        val members = _uiState.value.selectedUsers.map { it.uid }
        if (members.size < 2) {
            _uiState.value = _uiState.value.copy(errorMessage = "Выберите минимум 2 участников")
            return
        }
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Введите название группы")
            return
        }

        _uiState.value = _uiState.value.copy(isCreating = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.createGroupChat(title, members)) {
                is CreateChatResult.Success -> {
                    _uiState.value = _uiState.value.copy(isCreating = false)
                    _createdChatId.value = result.chatId
                }
                is CreateChatResult.Error -> {
                    _uiState.value = _uiState.value.copy(isCreating = false, errorMessage = result.message)
                }
            }
        }
    }

    fun consumeCreatedChatId() {
        _createdChatId.value = null
    }
}
