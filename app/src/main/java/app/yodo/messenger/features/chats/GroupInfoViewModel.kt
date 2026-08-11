package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.GroupInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GroupInfoUiState {
    data object Loading : GroupInfoUiState()
    data class Content(val info: GroupInfo) : GroupInfoUiState()
    data object NotFound : GroupInfoUiState()
}

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow<GroupInfoUiState>(GroupInfoUiState.Loading)
    val uiState: StateFlow<GroupInfoUiState> = _uiState

    private val _didLeave = MutableStateFlow(false)
    val didLeave: StateFlow<Boolean> = _didLeave

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val info = chatRepository.getGroupInfo(chatId)
            _uiState.value = if (info != null) GroupInfoUiState.Content(info) else GroupInfoUiState.NotFound
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            chatRepository.leaveGroup(chatId)
            _didLeave.value = true
        }
    }
}
