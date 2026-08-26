package app.yodo.messenger.features.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.MessageRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** Разбивка сообщений чата по типу контента. */
data class ContentTypeBreakdown(
    val textCount: Int = 0,
    val imageCount: Int = 0,
    val voiceCount: Int = 0,
    val fileCount: Int = 0,
    val locationCount: Int = 0,
    val pollCount: Int = 0
)

/** Число сообщений одного участника чата. */
data class SenderStat(
    val senderId: String,
    val displayName: String,
    val messageCount: Int
)

/** Точка графика активности — день и число сообщений за него. */
data class DailyActivityPoint(
    val dateLabel: String,
    val count: Int
)

/** Распределение сообщений по часам суток (0..23), для графика "когда чат активнее всего". */
data class HourlyActivityPoint(
    val hour: Int,
    val count: Int
)

data class ChatStatsUiState(
    val isLoading: Boolean = true,
    val chatTitle: String = "",
    val totalMessages: Int = 0,
    val senderStats: List<SenderStat> = emptyList(),
    val dailyActivity: List<DailyActivityPoint> = emptyList(),
    val hourlyActivity: List<HourlyActivityPoint> = emptyList(),
    val contentBreakdown: ContentTypeBreakdown = ContentTypeBreakdown(),
    val firstMessageDateLabel: String? = null
)

@HiltViewModel
class ChatStatsViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(ChatStatsUiState())
    val uiState: StateFlow<ChatStatsUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val chatInfo = chatRepository.getChatInfo(chatId)
            val groupInfo = chatRepository.getGroupInfo(chatId)
            val currentUserId = firebaseAuth.currentUser?.uid

            // Карта senderId -> отображаемое имя: для групп/каналов берём из участников,
            // для приватного чата — это либо "Вы", либо собеседник из ChatInfo.
            val nameById: Map<String, String> = buildMap {
                groupInfo?.members?.forEach { member -> put(member.uid, member.displayName) }
                currentUserId?.let { put(it, "Вы") }
                if (chatInfo?.otherUserId != null) {
                    put(chatInfo.otherUserId, chatInfo.title)
                }
            }

            messageRepository.observeMessages(chatId).collect { messages ->
                val realMessages = messages.filter { !it.isDeleted }
                _uiState.value = ChatStatsUiState(
                    isLoading = false,
                    chatTitle = chatInfo?.title ?: "Чат",
                    totalMessages = realMessages.size,
                    senderStats = computeSenderStats(realMessages, nameById),
                    dailyActivity = computeDailyActivity(realMessages),
                    hourlyActivity = computeHourlyActivity(realMessages),
                    contentBreakdown = computeContentBreakdown(realMessages),
                    firstMessageDateLabel = realMessages.minByOrNull { it.timestamp }
                        ?.let { formatDay(it.timestamp) }
                )
            }
        }
    }

    private fun computeSenderStats(messages: List<Message>, nameById: Map<String, String>): List<SenderStat> {
        return messages.groupingBy { it.senderId }
            .eachCount()
            .map { (senderId, count) ->
                SenderStat(
                    senderId = senderId,
                    displayName = nameById[senderId] ?: "Пользователь",
                    messageCount = count
                )
            }
            .sortedByDescending { it.messageCount }
    }

    private fun computeDailyActivity(messages: List<Message>): List<DailyActivityPoint> {
        val byDay = messages.groupingBy { formatDay(it.timestamp) }.eachCount()
        // Сохраняем хронологический порядок, а не алфавитный порядок ключей.
        return messages
            .map { formatDay(it.timestamp) }
            .distinct()
            .sortedBy { label -> messages.first { formatDay(it.timestamp) == label }.timestamp }
            .map { label -> DailyActivityPoint(label, byDay[label] ?: 0) }
    }

    private fun computeHourlyActivity(messages: List<Message>): List<HourlyActivityPoint> {
        val calendar = Calendar.getInstance()
        val byHour = IntArray(24)
        messages.forEach { msg ->
            calendar.timeInMillis = msg.timestamp
            byHour[calendar.get(Calendar.HOUR_OF_DAY)]++
        }
        return byHour.mapIndexed { hour, count -> HourlyActivityPoint(hour, count) }
    }

    private fun computeContentBreakdown(messages: List<Message>): ContentTypeBreakdown {
        var text = 0; var image = 0; var voice = 0; var file = 0; var location = 0; var poll = 0
        messages.forEach { msg ->
            when {
                msg.poll != null -> poll++
                msg.voiceBase64 != null -> voice++
                msg.imageBase64 != null -> image++
                msg.locationLat != null && msg.locationLng != null -> location++
                msg.fileBase64 != null || msg.fileName != null -> file++
                msg.text.isNotBlank() -> text++
            }
        }
        return ContentTypeBreakdown(text, image, voice, file, location, poll)
    }

    private fun formatDay(timestampMillis: Long): String =
        SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(timestampMillis)
}
