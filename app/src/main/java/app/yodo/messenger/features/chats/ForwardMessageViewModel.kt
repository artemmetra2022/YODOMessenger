package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * п.36: экран пересылки переработан — теперь показывает превью того, что
 * пересылается, поддерживает поиск по чатам и группирует их по типу
 * (закреплённые / личные / группы / канал), а не просто выводит плоский список.
 */
@HiltViewModel
class ForwardMessageViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val firebaseAuth: FirebaseAuth,
    private val pendingForwardHolder: PendingForwardHolder,
    private val pendingForwardUndoHolder: PendingForwardUndoHolder
) : ViewModel() {

    private val _allChats = MutableStateFlow<List<ChatPreview>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chats: StateFlow<List<ChatPreview>> = _chats

    private val _forwardedToChatId = MutableStateFlow<String?>(null)
    val forwardedToChatId: StateFlow<String?> = _forwardedToChatId

    private val _isForwarding = MutableStateFlow(false)
    val isForwarding: StateFlow<Boolean> = _isForwarding

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Сообщение, которое пересылаем: берём через peek(), чтобы можно было
    // показать превью в шапке экрана; из holder'а забираем (takeAndClear)
    // только после того, как пересылка реально состоялась.
    val messageToForward: Message? = pendingForwardHolder.peek()

    init {
        viewModelScope.launch {
            chatRepository.observeChatList().collect { result ->
                if (result is ChatListResult.Success) {
                    _allChats.value = result.chats
                    applyFilter()
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilter()
    }

    private fun applyFilter() {
        val query = _searchQuery.value.trim()
        _chats.value = if (query.isBlank()) {
            _allChats.value
        } else {
            _allChats.value.filter { chat ->
                chat.title.contains(query, ignoreCase = true) ||
                    chat.username?.contains(query, ignoreCase = true) == true
            }
        }
    }

    fun forwardTo(targetChatId: String) {
        val message = messageToForward ?: return
        if (_isForwarding.value) return
        val myName = firebaseAuth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Пользователь"
        val myUid = firebaseAuth.currentUser?.uid ?: return
        _isForwarding.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            when (val result = messageRepository.forwardMessage(targetChatId, message, myName, myUid)) {
                is SendMessageResult.Success -> {
                    pendingForwardHolder.takeAndClear()
                    // п.2: кладём id пересланного сообщения в holder — ChatScreen того чата,
                    // куда пользователя сейчас перекинет навигация, заберёт его и покажет
                    // плашку "Сообщение переслано" с 5-секундным окном отмены.
                    if (result.messageId != null) {
                        // НОВОЕ (п.1): передаём данные получателя, чтобы плашка могла
                        // написать "Сообщение переслано пользователю ..." с переходом в профиль.
                        val targetChat = _allChats.value.find { it.chatId == targetChatId }
                        pendingForwardUndoHolder.set(
                            PendingForwardUndo(
                                targetChatId = targetChatId,
                                messageId = result.messageId,
                                targetName = targetChat?.title,
                                targetUsername = targetChat?.username,
                                targetUserId = targetChat?.otherUserId
                            )
                        )
                    }
                    _forwardedToChatId.value = targetChatId
                }
                is SendMessageResult.Error -> {
                    _isForwarding.value = false
                    _errorMessage.value = result.message
                }
            }
        }
    }
}
