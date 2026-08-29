package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelProfileUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val profile: ChannelProfile? = null,
    val postsCount: Int = 0,
    val recentPosts: List<Message> = emptyList(),
    val owner: YodoUser? = null,
    val admins: List<YodoUser> = emptyList(),
    val isOwner: Boolean = false,
    val canManage: Boolean = false
)

@HiltViewModel
class ChannelProfileViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(ChannelProfileUiState())
    val uiState: StateFlow<ChannelProfileUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    // НОВОЕ: сигнал о том, что канал был удалён владельцем — экран должен закрыться.
    private val _channelDeleted = MutableStateFlow(false)
    val channelDeleted: StateFlow<Boolean> = _channelDeleted

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init { refresh() }

    /** Перечитывает всё с сервера — вызывается и при возврате с экрана редактирования. */
    fun refresh() {
        viewModelScope.launch {
            val profile = chatRepository.getChannelProfile(chatId)
            if (profile == null) {
                _uiState.value = ChannelProfileUiState(isLoading = false, notFound = true)
                return@launch
            }
            val myUid = firebaseAuth.currentUser?.uid
            val postsCount = messageRepository.countMessages(chatId)
            val recentPosts = messageRepository.getRecentMessages(chatId, 3)
            val owner = profile.ownerId?.let { userRepository.getUserById(it) }
            val admins = profile.adminIds.mapNotNull { userRepository.getUserById(it) }
            _uiState.value = ChannelProfileUiState(
                isLoading = false,
                profile = profile,
                postsCount = postsCount,
                recentPosts = recentPosts,
                owner = owner,
                admins = admins,
                isOwner = myUid != null && myUid == profile.ownerId,
                canManage = myUid != null && (myUid == profile.ownerId || myUid in profile.adminIds)
            )
        }
    }

    /** Подписка/отписка с мгновенным (оптимистичным) обновлением счётчиков в UI.
     *  Владелец канала не может отписаться от собственного канала.
     *  НОВОЕ (модерируемые каналы): если канал MODERATED и пользователь ещё не
     *  подписан — вместо подписки отправляем/отменяем заявку на вступление. */
    fun toggleSubscription() {
        if (_uiState.value.isOwner) return
        val profile = _uiState.value.profile ?: return

        // Модерируемый канал: неподписанный пользователь работает с заявкой.
        if (profile.accessMode == app.yodo.messenger.domain.model.ChannelAccessMode.MODERATED && !profile.isSubscribed) {
            val wasPending = profile.hasPendingJoinRequest
            _uiState.value = _uiState.value.copy(profile = profile.copy(hasPendingJoinRequest = !wasPending))
            viewModelScope.launch {
                if (wasPending) chatRepository.cancelJoinRequest(chatId)
                else chatRepository.requestToJoinChannel(chatId)
            }
            return
        }

        val wasSubscribed = profile.isSubscribed
        _uiState.value = _uiState.value.copy(
            profile = profile.copy(
                isSubscribed = !wasSubscribed,
                subscriberCount = (profile.subscriberCount + if (wasSubscribed) -1 else 1).coerceAtLeast(0)
            )
        )
        viewModelScope.launch {
            if (wasSubscribed) chatRepository.unsubscribeFromChannel(chatId)
            else chatRepository.subscribeToChannel(chatId)
        }
    }

    fun openChat() { _openChatId.value = chatId }

    fun consumeOpenChatId() { _openChatId.value = null }

    // НОВОЕ: владелец удаляет канал целиком прямо из профиля канала.
    fun deleteChannel() {
        if (!_uiState.value.isOwner) return
        viewModelScope.launch {
            when (val result = chatRepository.deleteChannel(chatId)) {
                is app.yodo.messenger.domain.repository.ChannelUpdateResult.Success -> _channelDeleted.value = true
                is app.yodo.messenger.domain.repository.ChannelUpdateResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    fun consumeErrorMessage() { _errorMessage.value = null }
}