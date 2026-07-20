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

@HiltViewModel
class ForwardMessageViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val firebaseAuth: FirebaseAuth,
    private val pendingForwardHolder: PendingForwardHolder
) : ViewModel() {

    private val _chats = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chats: StateFlow<List<ChatPreview>> = _chats

    private val _forwardedToChatId = MutableStateFlow<String?>(null)
    val forwardedToChatId: StateFlow<String?> = _forwardedToChatId

    private val messageToForward: Message? = pendingForwardHolder.takeAndClear()

    init {
        viewModelScope.launch {
            chatRepository.observeChatList().collect { result ->
                if (result is ChatListResult.Success) _chats.value = result.chats
            }
        }
    }

    fun forwardTo(targetChatId: String) {
        val message = messageToForward ?: return
        val myName = firebaseAuth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Пользователь"
        viewModelScope.launch {
            when (messageRepository.forwardMessage(targetChatId, message, myName)) {
                is SendMessageResult.Success -> _forwardedToChatId.value = targetChatId
                is SendMessageResult.Error -> { /* можно показать ошибку при необходимости */ }
            }
        }
    }
}
