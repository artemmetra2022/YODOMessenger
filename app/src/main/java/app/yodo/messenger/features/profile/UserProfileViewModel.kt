package app.yodo.messenger.features.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.UserPresence
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UserProfileUiState {
    data object Loading : UserProfileUiState()
    data class Content(val user: YodoUser, val presence: UserPresence?) : UserProfileUiState()
    data object NotFound : UserProfileUiState()
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val presenceRepository: PresenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String = checkNotNull(savedStateHandle["userId"])

    private val _uiState = MutableStateFlow<UserProfileUiState>(UserProfileUiState.Loading)
    val uiState: StateFlow<UserProfileUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    // НОВОЕ (блокировка): заблокирован ли этот пользователь.
    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked

    init {
        viewModelScope.launch {
            val user = userRepository.getUserById(userId)
            if (user != null) {
                _uiState.value = UserProfileUiState.Content(user, presence = null)
                _isBlocked.value = userRepository.isUserBlocked(userId)
                observePresence()
            } else {
                _uiState.value = UserProfileUiState.NotFound
            }
        }
    }

    // НОВОЕ (блокировка): заблокировать / разблокировать пользователя.
    fun toggleBlock() {
        viewModelScope.launch {
            val result = if (_isBlocked.value) userRepository.unblockUser(userId)
            else userRepository.blockUser(userId)
            if (result is ProfileUpdateResult.Success) {
                _isBlocked.value = !_isBlocked.value
            }
        }
    }

    private fun observePresence() {
        viewModelScope.launch {
            presenceRepository.observePresence(userId).collect { presence ->
                val current = _uiState.value
                if (current is UserProfileUiState.Content) {
                    _uiState.value = current.copy(presence = presence)
                }
            }
        }
    }

    fun openChat() {
        viewModelScope.launch {
            when (val result = chatRepository.createOrGetPrivateChat(userId)) {
                is CreateChatResult.Success -> _openChatId.value = result.chatId
                is CreateChatResult.Error -> { /* TODO: показать ошибку, если потребуется */ }
            }
        }
    }

    fun consumeOpenChatId() {
        _openChatId.value = null
    }
}
