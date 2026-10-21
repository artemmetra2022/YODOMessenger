package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (раздел «Медиа, файлы, ссылки»): вложения одного чата (или темы форума).
 *
 * Схема хранения сообщений не менялась: у старых сообщений нет денормализованных
 * флагов «это фото/файл/ссылка», поэтому раздел читает историю страницами
 * (getMessagesPage) и раскладывает сообщения по вкладкам на клиенте. Каждая вкладка
 * помнит свой курсор, так что переключение между ними не сканирует историю заново.
 */
@HiltViewModel
class ChatMediaViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class ChatMediaUiState(
        val tab: ChatMediaTab = ChatMediaTab.MEDIA,
        val items: List<Message> = emptyList(),
        val isLoading: Boolean = false,
        val endReached: Boolean = false,
        val error: String? = null,
        val initialLoadDone: Boolean = false
    )

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])
    private val topicId: String? = savedStateHandle["topicId"]

    private val _uiState = MutableStateFlow(ChatMediaUiState())
    val uiState: StateFlow<ChatMediaUiState> = _uiState

    /** Уже собранные вложения по вкладкам (новые сверху). */
    private val itemsByTab = mutableMapOf<ChatMediaTab, List<Message>>()
    /** Курсор сканирования по вкладке: timestamp самого старого прочитанного сообщения. */
    private val cursorByTab = mutableMapOf<ChatMediaTab, Long?>()
    private val endReachedByTab = mutableMapOf<ChatMediaTab, Boolean>()
    private var loadingJob: kotlinx.coroutines.Job? = null

    init {
        loadMore()
    }

    fun selectTab(tab: ChatMediaTab) {
        if (_uiState.value.tab == tab) return
        _uiState.value = _uiState.value.copy(
            tab = tab,
            items = itemsByTab[tab].orEmpty(),
            endReached = endReachedByTab[tab] == true,
            initialLoadDone = itemsByTab.containsKey(tab) || endReachedByTab[tab] == true,
            error = null
        )
        if (!itemsByTab.containsKey(tab) && endReachedByTab[tab] != true) loadMore()
    }

    /** Подгружает следующую порцию вложений для активной вкладки. */
    fun loadMore() {
        if (loadingJob?.isActive == true) return
        val tab = _uiState.value.tab
        if (endReachedByTab[tab] == true) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        loadingJob = viewModelScope.launch {
            try {
                val collected = mutableListOf<Message>()
                var scannedPages = 0
                // Документы могут быть без вложений вообще, поэтому сканируем несколько
                // страниц подряд, пока не наберём достаточно элементов для вкладки.
                while (collected.size < ITEMS_PER_PAGE && scannedPages < MAX_SCAN_PAGES) {
                    val page = messageRepository.getMessagesPage(
                        chatId = chatId,
                        topicId = topicId,
                        beforeTimestamp = cursorByTab[tab],
                        limit = SCAN_PAGE_SIZE
                    )
                    if (page.isEmpty()) {
                        endReachedByTab[tab] = true
                        break
                    }
                    cursorByTab[tab] = page.first().timestamp
                    collected += page.filter { it.belongsTo(tab) }
                    scannedPages++
                    if (page.size < SCAN_PAGE_SIZE) {
                        endReachedByTab[tab] = true
                        break
                    }
                }
                val merged = (itemsByTab[tab].orEmpty() + collected)
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
                itemsByTab[tab] = merged
                if (_uiState.value.tab == tab) {
                    _uiState.value = _uiState.value.copy(
                        items = merged,
                        endReached = endReachedByTab[tab] == true,
                        initialLoadDone = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.tab == tab) {
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Не удалось загрузить вложения",
                        initialLoadDone = true
                    )
                }
            } finally {
                loadingJob = null
                _uiState.value = _uiState.value.copy(isLoading = false)
                // Если за время загрузки пользователь переключил вкладку, для неё ещё
                // ничего не загружено — запускаем отдельный проход уже по новой вкладке.
                val current = _uiState.value
                if (current.tab != tab && !itemsByTab.containsKey(current.tab) &&
                    endReachedByTab[current.tab] != true) {
                    loadMore()
                }
            }
        }
    }

    private companion object {
        const val SCAN_PAGE_SIZE = 60
        const val MAX_SCAN_PAGES = 4
        const val ITEMS_PER_PAGE = 24
    }
}
