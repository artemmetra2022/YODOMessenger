package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AdminUsersUiState {
    data object Idle : AdminUsersUiState()
    data object Loading : AdminUsersUiState()
    data object NoResults : AdminUsersUiState()
    data class Results(val users: List<YodoUser>) : AdminUsersUiState()
}

/**
 * НОВОЕ (единая вкладка «Админка»): поиск пользователя по имени/username и его
 * глобальная блокировка прямо из списка результатов — без захода в профиль.
 * Использует тот же UserRepository.searchUsers/setGlobalBlock/removeGlobalBlock,
 * что и обычный поиск и экран профиля пользователя (UserProfileViewModel) —
 * контекстная блокировка "с профиля конкретного человека" остаётся как есть,
 * этот экран лишь даёт более быстрый путь для админа, который ищет по имени.
 */
@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }

    private val _uiState = MutableStateFlow<AdminUsersUiState>(AdminUsersUiState.Idle)
    val uiState: StateFlow<AdminUsersUiState> = _uiState

    // uid -> заблокирован ли глобально (подгружается лениво под каждый результат поиска).
    private val _blockedStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val blockedStatus: StateFlow<Map<String, Boolean>> = _blockedStatus

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = AdminUsersUiState.Idle
            return
        }
        _uiState.value = AdminUsersUiState.Loading
        searchJob = viewModelScope.launch {
            delay(350)
            val users = userRepository.searchUsers(query)
            _uiState.value = if (users.isEmpty()) AdminUsersUiState.NoResults else AdminUsersUiState.Results(users)
            // Подтягиваем статус блокировки для новых результатов.
            val statuses = users.associate { it.uid to (userRepository.getGlobalBlock(it.uid) != null) }
            _blockedStatus.value = _blockedStatus.value + statuses
        }
    }

    fun blockUser(userId: String, reason: String) {
        viewModelScope.launch {
            when (val result = userRepository.setGlobalBlock(userId, reason)) {
                is ProfileUpdateResult.Error -> _errorMessage.value = result.message
                ProfileUpdateResult.Success -> {
                    _blockedStatus.value = _blockedStatus.value + (userId to true)
                }
            }
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            when (val result = userRepository.removeGlobalBlock(userId)) {
                is ProfileUpdateResult.Error -> _errorMessage.value = result.message
                ProfileUpdateResult.Success -> {
                    _blockedStatus.value = _blockedStatus.value + (userId to false)
                }
            }
        }
    }

    fun consumeErrorMessage() {
        _errorMessage.value = null
    }
}
