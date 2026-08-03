package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.Comment
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.SendMessageResult
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (переработка каналов): комментарии к посту канала.
 * Аватары авторов подтягиваются лениво и кэшируются (uid -> photoUrl/avatarBase64).
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val messageId: String = checkNotNull(savedStateHandle["messageId"])
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _senderAvatars = MutableStateFlow<Map<String, Pair<String?, String?>>>(emptyMap())
    val senderAvatars: StateFlow<Map<String, Pair<String?, String?>>> = _senderAvatars

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        viewModelScope.launch {
            messageRepository.observeMessage(chatId, messageId).collect { _message.value = it }
        }
        viewModelScope.launch {
            messageRepository.observeComments(chatId, messageId).collect { list ->
                _comments.value = list
                loadAvatars(list)
            }
        }
    }

    private suspend fun loadAvatars(comments: List<Comment>) {
        val missing = comments.map { it.senderId }.distinct().filter { it !in _senderAvatars.value }
        if (missing.isEmpty()) return
        val updated = _senderAvatars.value.toMutableMap()
        missing.forEach { uid ->
            val user = userRepository.getUserById(uid)
            updated[uid] = (user?.photoUrl to user?.avatarBase64)
        }
        _senderAvatars.value = updated
    }

    fun sendComment(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val result = messageRepository.addComment(chatId, messageId, text)) {
                is SendMessageResult.Error -> _errorMessage.value = result.message
                else -> {}
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            when (val result = messageRepository.deleteComment(chatId, messageId, commentId)) {
                is SendMessageResult.Error -> _errorMessage.value = result.message
                else -> {}
            }
        }
    }

    fun consumeError() { _errorMessage.value = null }
}