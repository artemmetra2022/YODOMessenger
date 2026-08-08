package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.core.crypto.CryptoManager
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.HiddenPinResult
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    private val userSettingsPreferences: UserSettingsPreferences,
    private val cryptoManager: CryptoManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState

    /** Текущий выбранный фильтр */
    private val _activeFilter = MutableStateFlow<ChatFilter>(ChatFilter.ALL)
    val activeFilter: StateFlow<ChatFilter> = _activeFilter

    // НОВОЕ (п.4): папки чатов — пользовательские группировки чатов
    val chatFolders: StateFlow<List<ChatFolder>> = userSettingsPreferences.chatFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // НОВОЕ (скрытые чаты): множество ID скрытых чатов (для пунктов меню).
    val hiddenChatIds: StateFlow<Set<String>> = userSettingsPreferences.hiddenChatIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // НОВОЕ (скрытые чаты): задан ли основной PIN (нужно для шторки со скрытыми чатами).
    val isPinSet: StateFlow<Boolean> = userSettingsPreferences.isPinSet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // НОВОЕ (скрытые чаты): полный список скрытых чатов (для отдельного окна).
    val hiddenChats: StateFlow<List<ChatPreview>> = combine(
        chatRepository.observeChatList(),
        userSettingsPreferences.hiddenChatIds.onStart { emit(emptySet()) }
    ) { result, hiddenIds ->
        when (result) {
            is ChatListResult.Success -> result.chats.filter { it.chatId in hiddenIds }
            is ChatListResult.Error -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // НОВОЕ (скрытые чаты): проверка пин-кода для шторки (без побочных эффектов).
    suspend fun checkHiddenPin(pin: String): HiddenPinResult = userSettingsPreferences.checkHiddenPin(pin)

    // НОВОЕ (чат поддержки): является ли текущий пользователь админом поддержки —
    // тогда в меню показывается пункт "Админ-панель поддержки".
    val isSupportAdmin: Boolean get() = chatRepository.isSupportAdmin()

    init {
        observeChats()
        syncFcmToken()
        // НОВОЕ (сквозное шифрование): гарантируем ключи и публикуем публичный ключ после входа
        // (список чатов открывается всегда после авторизации, в т.ч. сразу после регистрации).
        viewModelScope.launch { runCatching { cryptoManager.ensureInitialized() } }
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
            // combine() не эмитит НИЧЕГО, пока каждый источник не выдаст хотя бы одно значение.
            // draftsPreferences.observeAllDrafts() и chatFolders читаются из DataStore (диск),
            // и на "холодном" старте сразу после входа могут отвечать не сразу — из-за этого
            // список чатов висел на Loading, даже когда Firestore уже всё вернул. Особенно
            // заметно именно на ПЕРВОЙ загрузке после логина — отсюда и жалоба "иногда вообще
            // не появляется, помогает только перезаход". .onStart{} даёт им значение по
            // умолчанию сразу же, не дожидаясь диска — черновики/папки просто "доедут"
            // следующим обновлением, когда будут готовы.
            // НОВОЕ (скрытые чаты): пятый источник — ID скрытых чатов и признак decoy-режима.
            val hiddenInfo = combine(
                userSettingsPreferences.hiddenChatIds.onStart { emit(emptySet()) },
                userSettingsPreferences.decoyMode.onStart { emit(false) }
            ) { ids, decoy -> ids to decoy }
            combine(
                chatRepository.observeChatList(),
                draftsPreferences.observeAllDrafts().onStart { emit(emptyMap()) },
                _activeFilter,
                chatFolders.onStart { emit(emptyList()) },
                hiddenInfo
            ) { result, drafts, filter, folders, hidden ->
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
                        // НОВОЕ (скрытые чаты): в decoy-режиме полностью убираем скрытые чаты из списка.
                        val (hiddenIds, decoyMode) = hidden
                        val visibleChats = if (decoyMode) {
                            chatsWithDrafts.filter { it.chatId !in hiddenIds }
                        } else {
                            chatsWithDrafts
                        }
                        val (archived, active) = visibleChats.partition { it.isArchived }
                        
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

    // НОВОЕ (скрытые чаты): переключить скрытие чата.
    fun toggleChatHidden(chatId: String) {
        viewModelScope.launch {
            val hidden = hiddenChatIds.value.contains(chatId)
            userSettingsPreferences.setChatHidden(chatId, !hidden)
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