package app.yodo.messenger.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelSearchItem
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.UserRepository
import app.yodo.messenger.features.settings.SettingsSearchItem
import app.yodo.messenger.features.settings.SettingsSearchMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data object NoResults : SearchUiState()
    // НОВОЕ (переработка каналов): результаты теперь — и люди, и каналы.
    // НОВОЕ (поиск по настройкам): а также совпадения по настройкам приложения.
    // НОВОЕ (админ-функции групп): и группы (открытые/модерируемые видны в поиске).
    data class Results(
        val users: List<YodoUser>,
        val channels: List<ChannelSearchItem>,
        val groups: List<ChannelSearchItem> = emptyList(),
        val settings: List<SettingsSearchItem> = emptyList()
    ) : SearchUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    // НОВОЕ (поиск по настройкам): нужен, чтобы узнать, включён ли показ настроек в общем поиске.
    private val userSettingsPreferences: UserSettingsPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId

    // НОВОЕ: если тапнули по каналу, на который пользователь ещё не подписан —
    // открываем профиль канала (там можно подписаться), а не чат.
    private val _openChannelProfileId = MutableStateFlow<String?>(null)
    val openChannelProfileId: StateFlow<String?> = _openChannelProfileId

    // НОВОЕ (админ-функции групп): тап по группе, в которой пользователь не состоит —
    // открываем профиль группы (там можно вступить/подать заявку), а не чат.
    private val _openGroupProfileId = MutableStateFlow<String?>(null)
    val openGroupProfileId: StateFlow<String?> = _openGroupProfileId

    // ИЗМЕНЕНО (разделение настроек по категориям): раньше хранился только
    // anchorId и всегда открывался единственный экран настроек. Теперь нужно
    // ещё знать, в какой именно экран категории переходить — храним весь
    // найденный пункт (item.categoryRoute + item.anchorId).
    private val _openSettingsItem = MutableStateFlow<SettingsSearchItem?>(null)
    val openSettingsItem: StateFlow<SettingsSearchItem?> = _openSettingsItem

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        _uiState.value = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            delay(350) // debounce — не долбим Firestore на каждое нажатие клавиши
            val users = userRepository.searchUsers(query)
            val channels = chatRepository.searchChannels(query)
            // НОВОЕ (админ-функции групп): группы в поиске (открытые/модерируемые).
            val groups = chatRepository.searchGroups(query)
            // НОВОЕ (поиск по настройкам): подмешиваем совпадения по настройкам,
            // если пользователь не отключил их показ в общем поиске.
            val showSettings = userSettingsPreferences.showSettingsInGlobalSearch.first()
            val settings = if (showSettings) SettingsSearchMatcher.search(query) else emptyList()
            _uiState.value = if (users.isEmpty() && channels.isEmpty() && groups.isEmpty() && settings.isEmpty()) {
                SearchUiState.NoResults
            } else {
                SearchUiState.Results(users = users, channels = channels, groups = groups, settings = settings)
            }
        }
    }

    fun openChatWith(user: YodoUser) {
        viewModelScope.launch {
            when (val result = chatRepository.createOrGetPrivateChat(user.uid)) {
                is CreateChatResult.Success -> _openChatId.value = result.chatId
                is CreateChatResult.Error -> _errorMessage.value = result.message
            }
        }
    }

    // НОВОЕ: тап по каналу в выдаче поиска.
    fun openChannel(channel: ChannelSearchItem) {
        if (channel.isSubscribed) {
            _openChatId.value = channel.chatId
        } else {
            _openChannelProfileId.value = channel.chatId
        }
    }

    // НОВОЕ (админ-функции групп): тап по группе в выдаче поиска.
    fun openGroup(group: ChannelSearchItem) {
        if (group.isSubscribed) {
            _openChatId.value = group.chatId
        } else {
            _openGroupProfileId.value = group.chatId
        }
    }

    // НОВОЕ (поиск по настройкам): тап по найденной настройке.
    fun openSetting(item: SettingsSearchItem) {
        _openSettingsItem.value = item
    }

    fun consumeErrorMessage() { _errorMessage.value = null }
    fun consumeOpenChatId() { _openChatId.value = null }
    fun consumeOpenChannelProfileId() { _openChannelProfileId.value = null }
    fun consumeOpenGroupProfileId() { _openGroupProfileId.value = null }
    fun consumeOpenSettingsItem() { _openSettingsItem.value = null }
}
