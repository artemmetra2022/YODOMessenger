package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.ChatFolder
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Фильтр списка чатов (горизонтальные табы, как в Telegram) */
sealed class ChatFilter {
    data object ALL : ChatFilter()
    data object PRIVATE : ChatFilter()
    data object GROUPS : ChatFilter()
    data object UNREAD : ChatFilter()
    // НОВОЕ (п.4): пользовательская папка чатов
    data class Folder(val folderId: String) : ChatFilter()
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
    private val draftsPreferences: DraftsPreferences,
    private val userSettingsPreferences: UserSettingsPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState

    /** Текущий выбранный фильтр */
    private val _activeFilter = MutableStateFlow<ChatFilter>(ChatFilter.ALL)
    val activeFilter: StateFlow<ChatFilter> = _activeFilter

    // НОВОЕ (п.4): папки чатов — пользовательские группировки чатов
    val chatFolders: StateFlow<List<ChatFolder>> = userSettingsPreferences.chatFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observeChats()
        syncFcmToken()
    }

    fun setFilter(filter: ChatFilter) {
        _activeFilter.value = filter
    }

    // НОВОЕ (п.4): управление папками чатов
    fun addChatFolder(name: String) {
        viewModelScope.launch {
            val folder = ChatFolder(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                order = chatFolders.value.size
            )
            userSettingsPreferences.addChatFolder(folder)
        }
    }

    fun updateChatFolder(folder: ChatFolder) {
        viewModelScope.launch { userSettingsPreferences.updateChatFolder(folder) }
    }

    fun deleteChatFolder(folderId: String) {
        viewModelScope.launch { userSettingsPreferences.deleteChatFolder(folderId) }
    }

    fun addChatToFolder(folderId: String, chatId: String) {
        viewModelScope.launch { userSettingsPreferences.addChatToFolder(folderId, chatId) }
    }

    fun removeChatFromFolder(folderId: String, chatId: String) {
        viewModelScope.launch { userSettingsPreferences.removeChatFromFolder(folderId, chatId) }
    }

    private fun observeChats() {
        viewModelScope.launch {
            combine(
                chatRepository.observeChatList(),
                draftsPreferences.observeAllDrafts(),
                _activeFilter,
                chatFolders
            ) { result, drafts, filter, folders ->
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
                            is ChatFilter.Folder -> {
                                // НОВОЕ (п.4): фильтрация по пользовательской папке
                                val folder = folders.find { it.id == filter.folderId }
                                if (folder != null) {
                                    active.filter { it.chatId in folder.chatIds }
                                } else {
                                    active
                                }
                            }
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