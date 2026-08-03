package app.yodo.messenger.features.chats

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateChannelUiState(
    val isCreating: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CreateChannelViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateChannelUiState())
    val uiState: StateFlow<CreateChannelUiState> = _uiState

    private val _createdChatId = MutableStateFlow<String?>(null)
    val createdChatId: StateFlow<String?> = _createdChatId

    // НОВОЕ (переработка каналов): аватарка канала (Bitmap после кропа),
    // сохраняется вместе с созданием канала.
    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap

    fun setAvatar(bitmap: Bitmap) { _avatarBitmap.value = bitmap }
    fun clearAvatar() { _avatarBitmap.value = null }

    fun createChannel(title: String, description: String) {
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Введите название канала")
            return
        }
        _uiState.value = _uiState.value.copy(isCreating = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.createChannel(title, description, _avatarBitmap.value)) {
                is CreateChatResult.Success -> {
                    _uiState.value = _uiState.value.copy(isCreating = false)
                    _createdChatId.value = result.chatId
                }
                is CreateChatResult.Error -> {
                    _uiState.value = _uiState.value.copy(isCreating = false, errorMessage = result.message)
                }
            }
        }
    }

    fun consumeCreatedChatId() { _createdChatId.value = null }
}