package app.yodo.messenger.features.chats

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.UserPresence
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ChatUiState(
    val chatTitle: String = "Чат",
    val chatType: String = "PRIVATE",
    val otherUserId: String? = null,
    val messages: List<Message> = emptyList(),
    val pinnedMessages: List<Message> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val otherUserPresence: UserPresence? = null,
    val isOtherUserTyping: Boolean = false,
    val replyingTo: Message? = null,
    val editingMessage: Message? = null,
    val initialDraft: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val otherUserPhotoUrl: String? = null,
    val otherUserAvatarBase64: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val presenceRepository: PresenceRepository,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val draftsPreferences: DraftsPreferences,
    private val pendingForwardHolder: PendingForwardHolder,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    fun prepareForward(message: Message) { pendingForwardHolder.set(message) }

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    val sendOnEnter: StateFlow<Boolean> = userSettingsPreferences.sendOnEnter.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val autoDownloadImages: StateFlow<Boolean> = userSettingsPreferences.autoDownloadImages.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val hideKeyboardOnSend: StateFlow<Boolean> = userSettingsPreferences.hideKeyboardOnSend.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )

    private var typingResetJob: Job? = null
    private var isCurrentlyMarkedTyping = false

    init {
        loadChatInfo()
        observeMessages()
        observePinnedMessages()
        observeTyping()
        markAsRead()
        loadDraft()
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = draftsPreferences.getDraft(chatId)
            if (draft.isNotBlank()) _uiState.value = _uiState.value.copy(initialDraft = draft)
        }
    }

    fun saveDraft(text: String) {
        viewModelScope.launch { draftsPreferences.saveDraft(chatId, text) }
    }

    private fun loadChatInfo() {
        viewModelScope.launch {
            chatRepository.getChatInfo(chatId)?.let { info ->
                _uiState.value = _uiState.value.copy(
                    chatTitle = info.title, chatType = info.type,
                    otherUserId = info.otherUserId,
                    otherUserPhotoUrl = info.otherUserPhotoUrl,
                    otherUserAvatarBase64 = info.otherUserAvatarBase64
                )
                info.otherUserId?.let { observePresence(it) }
            }
        }
    }

    private fun observePresence(otherUserId: String) {
        viewModelScope.launch {
            presenceRepository.observePresence(otherUserId).collect { presence ->
                _uiState.value = _uiState.value.copy(otherUserPresence = presence)
            }
        }
    }

    private fun observeTyping() {
        viewModelScope.launch {
            presenceRepository.observeTypingUsers(chatId).collect { typingUids ->
                val otherUserId = _uiState.value.otherUserId
                val isTyping = otherUserId != null && otherUserId in typingUids
                _uiState.value = _uiState.value.copy(isOtherUserTyping = isTyping)
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages(chatId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    private fun observePinnedMessages() {
        viewModelScope.launch {
            messageRepository.observePinnedMessages(chatId).collect { pinned ->
                _uiState.value = _uiState.value.copy(pinnedMessages = pinned)
            }
        }
    }

    private fun markAsRead() {
        viewModelScope.launch { messageRepository.markChatAsRead(chatId) }
    }

    fun onInputTextChanged(text: String) {
        saveDraft(text)
        typingResetJob?.cancel()
        if (text.isNotBlank()) {
            if (!isCurrentlyMarkedTyping) {
                isCurrentlyMarkedTyping = true
                viewModelScope.launch { presenceRepository.setTyping(chatId, true) }
            }
            typingResetJob = viewModelScope.launch {
                delay(3000)
                isCurrentlyMarkedTyping = false
                presenceRepository.setTyping(chatId, false)
            }
        } else {
            clearTypingStatus()
        }
    }

    private fun clearTypingStatus() {
        typingResetJob?.cancel()
        if (isCurrentlyMarkedTyping) {
            isCurrentlyMarkedTyping = false
            viewModelScope.launch { presenceRepository.setTyping(chatId, false) }
        }
    }

    fun setReplyingTo(message: Message?) {
        _uiState.value = _uiState.value.copy(replyingTo = message, editingMessage = null)
    }

    fun setEditingMessage(message: Message?) {
        _uiState.value = _uiState.value.copy(editingMessage = message, replyingTo = null)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        clearTypingStatus()
        viewModelScope.launch { draftsPreferences.clearDraft(chatId) }
        val editing = _uiState.value.editingMessage
        if (editing != null) {
            _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null, editingMessage = null)
            viewModelScope.launch {
                when (val result = messageRepository.editMessage(chatId, editing.id, text)) {
                    is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                    is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
                }
            }
            return
        }
        val replying = _uiState.value.replyingTo
        val replyContext = replying?.let {
            ReplyContext(
                messageId = it.id,
                senderName = if (it.senderId == currentUserId) "Вы" else _uiState.value.chatTitle,
                text = it.previewText()
            )
        }
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null, replyingTo = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendMessage(chatId, text, replyContext)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    fun sendImage(base64: String) {
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = messageRepository.sendImageMessage(chatId, base64)) {
                is SendMessageResult.Success -> _uiState.value = _uiState.value.copy(isSending = false)
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(isSending = false, errorMessage = result.message)
            }
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            when (val result = messageRepository.deleteMessage(chatId, message.id)) {
                is SendMessageResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
                else -> {}
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch { messageRepository.toggleReaction(chatId, messageId, emoji) }
    }

    fun togglePinMessage(messageId: String) {
        viewModelScope.launch { messageRepository.togglePinMessage(chatId, messageId) }
    }

    fun toggleBookmark(messageId: String) {
        viewModelScope.launch { messageRepository.toggleBookmark(messageId, chatId) }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            try { chatRepository.clearChatHistory(chatId) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(errorMessage = "Не удалось очистить: ${e.message}") }
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            try { chatRepository.deleteChat(chatId) }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(errorMessage = "Не удалось удалить: ${e.message}") }
        }
    }

    fun exportChat(context: Context) {
        viewModelScope.launch {
            val text = messageRepository.exportChatHistory(chatId)
            val file = File(context.cacheDir, "yodo_chat_export.txt")
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Экспорт чата"))
        }
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(isSearchActive = !_uiState.value.isSearchActive, searchQuery = "")
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        if (isCurrentlyMarkedTyping) {
            viewModelScope.launch { presenceRepository.setTyping(chatId, false) }
        }
    }
}
