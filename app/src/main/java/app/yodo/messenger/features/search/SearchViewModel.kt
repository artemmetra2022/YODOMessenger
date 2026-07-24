package app.yodo.messenger.features.search

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

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data object NoResults : SearchUiState()
    data class Results(val users: List<YodoUser>) : SearchUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }

        _uiState.value = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            delay(350) // debounce — не долбим Firestore на каждое нажатие клавиши
            val results = userRepository.searchUsers(query)
            _uiState.value = if (results.isEmpty()) SearchUiState.NoResults else SearchUiState.Results(results)
        }
    }

    fun openChatWith(user: YodoUser) {
        viewModelScope.launch {
            when (val result = chatRepository.createOrGetPrivateChat(user.uid)) {
                is CreateChatResult.Success -> _openChatId.value = result.chatId
                is CreateChatResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun consumeErrorMessage() {
        _errorMessage.value = null
    }

    fun consumeOpenChatId() {
        _openChatId.value = null
    }
}
