package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.repository.ChatListResult
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class ChatListUiState {
    data object Loading : ChatListUiState()
    data object Empty : ChatListUiState()
    data class Content(val chats: List<ChatPreview>) : ChatListUiState()
    data class Error(val message: String) : ChatListUiState()
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState

    init {
        observeChats()
        syncFcmToken()
    }

    private fun observeChats() {
        viewModelScope.launch {
            chatRepository.observeChatList().collect { result ->
                _uiState.value = when (result) {
                    is ChatListResult.Success -> {
                        if (result.chats.isEmpty()) ChatListUiState.Empty
                        else ChatListUiState.Content(result.chats)
                    }
                    is ChatListResult.Error -> ChatListUiState.Error(result.message)
                }
            }
        }
    }

    fun togglePinChat(chatId: String) {
        viewModelScope.launch { chatRepository.togglePinChat(chatId) }
    }

    fun toggleMuteChat(chatId: String) {
        viewModelScope.launch { chatRepository.toggleMuteChat(chatId) }
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
