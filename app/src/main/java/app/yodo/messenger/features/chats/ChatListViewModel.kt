package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Фильтр списка чатов (горизонтальные табы, как в Telegram) */
enum class ChatFilter {
    ALL,        // Все чаты
    PRIVATE,    // Личные (type == PRIVATE)
    GROUPS,     // Группы (type == GROUP)
    UNREAD      // Непрочитанные (unreadCount > 0)
}

sealed class ChatListUiState {
    data object Loading : ChatListUiState()
    data object Empty : ChatListUiState()
    data class Content(
        val chats: List<ChatPreview>,
        val archivedChats: List<ChatPreview> = emptyList(),
        // Полный список (до применения фильтра) — нужен для подсчёта бейджей в табах
        val allActiveChats: List<ChatPreview> = emptyList()
    ) : ChatListUiState()
    data class Error(val message: String) : ChatListUiState()
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val draftsPreferences: DraftsPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState

    /** Текущий выбранный фильтр */
    private val _activeFilter = MutableStateFlow(ChatFilter.ALL)
    val activeFilter: StateFlow<ChatFilter> = _activeFilter

    init {
        observeChats()
        syncFcmToken()
    }

    fun setFilter(filter: ChatFilter) {
        _activeFilter.value = filter
    }

    private fun observeChats() {
        viewModelScope.launch {
            combine(
                chatRepository.observeChatList(),
                draftsPreferences.observeAllDrafts(),
                _activeFilter
            ) { result, drafts, filter ->
                when (result) {
                    is ChatListResult.Success -> {
                        val chatsWithDrafts = if (drafts.isEmpty()) {
                            result.chats
                        } else {
                            result.chats.map { chat ->
                                val draft = drafts[chat.chatId]
                                if (draft != null) chat.copy(draftText = draft) else chat
                            }
                        }
                        val (archived, active) = chatsWithDrafts.partition { it.isArchived }

                        // Применяем фильтр
                        val filtered = when (filter) {
                            ChatFilter.ALL     -> active
                            ChatFilter.PRIVATE -> active.filter { it.type == ChatType.PRIVATE }
                            ChatFilter.GROUPS  -> active.filter { it.type == ChatType.GROUP || it.type == ChatType.CHANNEL }
                            ChatFilter.UNREAD  -> active.filter { it.unreadCount > 0 }
                        }

                        if (active.isEmpty() && archived.isEmpty()) ChatListUiState.Empty
                        else ChatListUiState.Content(
                            chats = filtered,
                            archivedChats = archived,
                            allActiveChats = active
                        )
                    }
                    is ChatListResult.Error -> ChatListUiState.Error(result.message)
                }
            }.collect { state -> _uiState.value = state }
        }
    }

    fun togglePinChat(chatId: String) {
        viewModelScope.launch { chatRepository.togglePinChat(chatId) }
    }

    fun toggleMuteChat(chatId: String) {
        viewModelScope.launch { chatRepository.toggleMuteChat(chatId) }
    }

    fun toggleArchiveChat(chatId: String) {
        viewModelScope.launch { chatRepository.toggleArchiveChat(chatId) }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try { chatRepository.deleteChat(chatId) } catch (e: Exception) { }
        }
    }

    fun clearChatHistory(chatId: String) {
        viewModelScope.launch {
            try { chatRepository.clearChatHistory(chatId) } catch (e: Exception) { }
        }
    }

    private fun syncFcmToken() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                firestore.collection("users").document(uid).update("fcmToken", token).await()
            }
        }
    }
}
