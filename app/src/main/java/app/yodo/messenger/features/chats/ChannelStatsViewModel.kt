package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.ChannelStats
import app.yodo.messenger.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (статистика для владельца канала): состояние экрана ChannelStatsScreen —
 * рост аудитории, охваты постов, топ постов. В отличие от общего ChatStatsScreen
 * (доступного для любого чата), здесь акцент на метриках канала как медиаресурса.
 */
data class ChannelStatsUiState(
    val isLoading: Boolean = true,
    val channelTitle: String = "",
    val stats: ChannelStats? = null,
    val errorMessage: String? = null,
    val accessDenied: Boolean = false
)

@HiltViewModel
class ChannelStatsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(ChannelStatsUiState())
    val uiState: StateFlow<ChannelStatsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val profile = chatRepository.getChannelProfile(chatId)
            val stats = chatRepository.getChannelStats(chatId)
            _uiState.value = if (stats == null) {
                ChannelStatsUiState(
                    isLoading = false,
                    channelTitle = profile?.title.orEmpty(),
                    accessDenied = true
                )
            } else {
                ChannelStatsUiState(
                    isLoading = false,
                    channelTitle = profile?.title.orEmpty(),
                    stats = stats
                )
            }
        }
    }
}
