package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.ChannelCategorySection
import app.yodo.messenger.domain.repository.ChannelSearchItem
import app.yodo.messenger.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (каталог/рекомендации каналов): состояние экрана DiscoverChannelsScreen —
 * витрина каналов без необходимости вводить поисковый запрос.
 */
data class DiscoverChannelsUiState(
    val isLoading: Boolean = true,
    val trending: List<ChannelSearchItem> = emptyList(),
    val byCategory: List<ChannelCategorySection> = emptyList(),
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && trending.isEmpty() && byCategory.isEmpty()
}

@HiltViewModel
class DiscoverChannelsViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverChannelsUiState())
    val uiState: StateFlow<DiscoverChannelsUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    private val _openChannelProfileId = MutableStateFlow<String?>(null)
    val openChannelProfileId: StateFlow<String?> = _openChannelProfileId

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val directory = chatRepository.getChannelDirectory()
                _uiState.value = DiscoverChannelsUiState(
                    isLoading = false,
                    trending = directory.trending,
                    byCategory = directory.byCategory
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Не удалось загрузить каталог каналов"
                )
            }
        }
    }

    /** Тап по каналу в каталоге: подписан → открыть чат, иначе → профиль канала. */
    fun openChannel(channel: ChannelSearchItem) {
        if (channel.isSubscribed) {
            _openChatId.value = channel.chatId
        } else {
            _openChannelProfileId.value = channel.chatId
        }
    }

    fun consumeOpenChatId() { _openChatId.value = null }
    fun consumeOpenChannelProfileId() { _openChannelProfileId.value = null }
    fun consumeErrorMessage() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
