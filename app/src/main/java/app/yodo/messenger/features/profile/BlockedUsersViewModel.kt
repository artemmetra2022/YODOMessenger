package app.yodo.messenger.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// НОВОЕ (блокировка): экран со списком заблокированных пользователей.
sealed class BlockedUsersUiState {
    data object Loading : BlockedUsersUiState()
    data class Content(val users: List<YodoUser>) : BlockedUsersUiState()
    data object Empty : BlockedUsersUiState()
}

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BlockedUsersUiState>(BlockedUsersUiState.Loading)
    val uiState: StateFlow<BlockedUsersUiState> = _uiState

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = BlockedUsersUiState.Loading
            val users = userRepository.getBlockedUsers()
            _uiState.value = if (users.isEmpty()) BlockedUsersUiState.Empty
            else BlockedUsersUiState.Content(users)
        }
    }

    // Разблокировать пользователя и обновить список.
    fun unblock(uid: String) {
        viewModelScope.launch {
            val result = userRepository.unblockUser(uid)
            if (result is ProfileUpdateResult.Success) {
                val current = _uiState.value
                if (current is BlockedUsersUiState.Content) {
                    val remaining = current.users.filterNot { it.uid == uid }
                    _uiState.value = if (remaining.isEmpty()) BlockedUsersUiState.Empty
                    else BlockedUsersUiState.Content(remaining)
                }
            }
        }
    }
}
