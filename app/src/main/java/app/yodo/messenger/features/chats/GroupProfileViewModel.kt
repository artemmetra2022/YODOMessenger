package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ChannelAccessMode
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupProfileUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val profile: ChannelProfile? = null,
    // НОВОЕ (админ-функции групп): пользователь уже состоит в группе.
    val isMember: Boolean = false,
    val isOwner: Boolean = false,
    val canManage: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

/**
 * НОВОЕ (админ-функции групп): профиль-превью группы из выдачи поиска.
 * Позволяет неучастнику посмотреть группу и вступить: открытая группа —
 * сразу «Вступить», модерируемая — «Подать заявку»/«Отменить заявку».
 */
@HiltViewModel
class GroupProfileViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(GroupProfileUiState())
    val uiState: StateFlow<GroupProfileUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val profile = chatRepository.getChannelProfile(chatId)
            if (profile == null) {
                _uiState.value = GroupProfileUiState(isLoading = false, notFound = true)
                return@launch
            }
            val myUid = firebaseAuth.currentUser?.uid
            _uiState.value = GroupProfileUiState(
                isLoading = false,
                profile = profile,
                isMember = profile.isSubscribed,
                isOwner = myUid != null && myUid == profile.ownerId,
                canManage = myUid != null && (myUid == profile.ownerId || myUid in profile.adminIds)
            )
        }
    }

    /** Вступить в открытую группу / подать или отменить заявку в модерируемую. */
    fun joinOrRequest() {
        val profile = _uiState.value.profile ?: return
        if (_uiState.value.isMember || _uiState.value.isOwner) return
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val result = when {
                profile.accessMode == ChannelAccessMode.MODERATED && !profile.hasPendingJoinRequest ->
                    chatRepository.requestToJoinChannel(chatId)
                profile.accessMode == ChannelAccessMode.MODERATED ->
                    chatRepository.cancelJoinRequest(chatId)
                else -> {
                    chatRepository.subscribeToChannel(chatId)
                    // subscribeToChannel глотает исключения внутри — проверяем фактический
                    // результат перечитыванием профиля (отказ правил, например из-за бана,
                    // иначе выглядел бы как беззвучно «нажатая и забытая» кнопка).
                    val updated = chatRepository.getChannelProfile(chatId)
                    if (updated?.isSubscribed == true) ChannelUpdateResult.Success
                    else ChannelUpdateResult.Error("Не удалось вступить в группу")
                }
            }
            _uiState.value = _uiState.value.copy(isSaving = false)
            when (result) {
                is ChannelUpdateResult.Success -> refresh()
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
            }
        }
    }

    fun openChat() { _openChatId.value = chatId }
    fun consumeOpenChatId() { _openChatId.value = null }
    fun consumeErrorMessage() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
