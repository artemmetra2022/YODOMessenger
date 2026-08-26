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
import com.google.firebase.auth.FirebaseAuth
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
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val userId: String = checkNotNull(savedStateHandle["userId"])

    // НОВОЕ (AD): являюсь ли я админом приложения (2 почты) — могу глобально блокировать.
    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS

    private val _uiState = MutableStateFlow<UserProfileUiState>(UserProfileUiState.Loading)
    val uiState: StateFlow<UserProfileUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    // НОВОЕ (блокировка): заблокирован ли этот пользователь.
    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked

    // НОВОЕ (AD): глобальная блокировка аккаунта админом.
    private val _isGloballyBlocked = MutableStateFlow(false)
    val isGloballyBlocked: StateFlow<Boolean> = _isGloballyBlocked

    // ИСПРАВЛЕНО (AT): раньше ошибка блокировки просто проглатывалась, и выглядело
    // так, будто кнопка не работает. Теперь причина неудачи показывается админу.
    private val _globalBlockError = MutableStateFlow<String?>(null)
    val globalBlockError: StateFlow<String?> = _globalBlockError

    private val _globalBlockInfo = MutableStateFlow<String?>(null)
    val globalBlockInfo: StateFlow<String?> = _globalBlockInfo

    fun clearGlobalBlockMessages() {
        _globalBlockError.value = null
        _globalBlockInfo.value = null
    }

    init {
        viewModelScope.launch {
            val user = userRepository.getUserById(userId)
            if (user != null) {
                _uiState.value = UserProfileUiState.Content(user, presence = null)
                _isBlocked.value = userRepository.isUserBlocked(userId)
                if (isAppAdmin) _isGloballyBlocked.value = userRepository.getGlobalBlock(userId) != null
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

    // НОВОЕ (AD): глобально заблокировать / разблокировать аккаунт (только 2 почты-админа).
    fun setGlobalBlock(reason: String) {
        viewModelScope.launch {
            when (val result = userRepository.setGlobalBlock(userId, reason)) {
                is ProfileUpdateResult.Success -> {
                    // ИСПРАВЛЕНО (AT): после записи перепроверяем на сервере, что блокировка
                    // действительно создана (а не осела только в локальном кэше).
                    val confirmed = userRepository.getGlobalBlock(userId) != null
                    _isGloballyBlocked.value = confirmed
                    _globalBlockInfo.value = if (confirmed) {
                        "Пользователь заблокирован"
                    } else {
                        null
                    }
                    if (!confirmed) {
                        _globalBlockError.value =
                            "Блокировка не сохранилась на сервере. Проверьте интернет и что правила Firestore опубликованы."
                    }
                }
                is ProfileUpdateResult.Error -> {
                    _globalBlockError.value = result.message
                }
            }
        }
    }

    fun removeGlobalBlock() {
        viewModelScope.launch {
            when (val result = userRepository.removeGlobalBlock(userId)) {
                is ProfileUpdateResult.Success -> {
                    _isGloballyBlocked.value = userRepository.getGlobalBlock(userId) != null
                    _globalBlockInfo.value = "Блокировка снята"
                }
                is ProfileUpdateResult.Error -> {
                    _globalBlockError.value = result.message
                }
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
