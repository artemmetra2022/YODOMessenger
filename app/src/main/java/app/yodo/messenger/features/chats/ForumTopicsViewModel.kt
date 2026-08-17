package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ForumTopic
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// НОВОЕ (форумные группы): состояние экрана списка разделов форума.
data class ForumTopicsUiState(
    val topics: List<ForumTopic> = emptyList(),
    val isLoading: Boolean = true,
    // Может ли текущий пользователь создавать новые разделы (владелец/админ группы).
    val canCreateTopics: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ForumTopicsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(ForumTopicsUiState())
    val uiState: StateFlow<ForumTopicsUiState> = _uiState

    init {
        loadPermissions()
        observeTopics()
    }

    private fun loadPermissions() {
        viewModelScope.launch {
            val uid = firebaseAuth.currentUser?.uid
            val info = chatRepository.getGroupInfo(chatId)
            val canCreate = uid != null && info != null && uid == info.createdBy
            _uiState.value = _uiState.value.copy(canCreateTopics = canCreate)
        }
    }

    private fun observeTopics() {
        viewModelScope.launch {
            chatRepository.observeForumTopics(chatId).collect { topics ->
                _uiState.value = _uiState.value.copy(topics = topics, isLoading = false)
            }
        }
    }

    fun createTopic(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, errorMessage = null)
            when (val result = chatRepository.createForumTopic(chatId, title)) {
                is ChannelUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(isCreating = false)
                }
                is ChannelUpdateResult.Error -> {
                    _uiState.value = _uiState.value.copy(isCreating = false, errorMessage = result.message)
                }
            }
        }
    }

    // НОВОЕ: закрыть/открыть тему — доступно владельцу/админу (canCreateTopics
    // сейчас уже отражает ровно это право, переиспользуем его в UI для показа кнопок).
    fun toggleTopicClosed(topicId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.toggleTopicClosed(chatId, topicId)) {
                is ChannelUpdateResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                is ChannelUpdateResult.Success -> Unit
            }
        }
    }

    // НОВОЕ: удаление темы.
    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.deleteForumTopic(chatId, topicId)) {
                is ChannelUpdateResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                is ChannelUpdateResult.Success -> Unit
            }
        }
    }

    // НОВОЕ: закрепление важной темы сверху списка (персонально для пользователя).
    fun togglePinTopic(topicId: String) {
        viewModelScope.launch {
            when (val result = chatRepository.togglePinTopic(chatId, topicId)) {
                is ChannelUpdateResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                is ChannelUpdateResult.Success -> Unit
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
