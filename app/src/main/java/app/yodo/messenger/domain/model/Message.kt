package app.yodo.messenger.domain.model

enum class MessageStatus { SENDING, SENT, READ, FAILED }

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val status: MessageStatus,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val imageBase64: String? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val forwardedFromSenderName: String? = null,
    val forwardedFromSenderId: String? = null,
    val isPinned: Boolean = false,
    val expiresAt: Long? = null,
    val voiceBase64: String? = null,
    val voiceDurationMs: Long? = null,
    val fileBase64: String? = null,
    val fileName: String? = null,
    val fileMimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    // НОВОЕ (переработка каналов): число комментариев под постом канала.
    val commentsCount: Int = 0,
    // НОВОЕ (опросы): если не null — это сообщение-опрос.
    val poll: Poll? = null
) {
    fun previewText(): String = when {
        text.isNotBlank() -> text
        poll != null -> "📊 ${poll.question}"
        voiceBase64 != null -> "Голосовое сообщение"
        imageBase64 != null -> "Картинка"
        locationLat != null && locationLng != null -> "Геопозиция"
        fileBase64 != null || fileName != null -> "📎 ${fileName ?: "Файл"}"
        else -> text
    }
}

// НОВОЕ (опросы): вопрос + варианты ответа. votesByOption хранит uid проголосовавших
// за каждый вариант (индекс варианта -> список uid), что позволяет и посчитать голоса,
// и узнать, проголосовал ли текущий пользователь, и (для не анонимных опросов) кто именно.
data class Poll(
    val question: String,
    val options: List<String>,
    val votesByOption: Map<Int, List<String>> = emptyMap(),
    val isAnonymous: Boolean = true,
    val allowMultipleAnswers: Boolean = false,
    val isClosed: Boolean = false
) {
    fun totalVotes(): Int = votesByOption.values.sumOf { it.size }
    fun votesFor(optionIndex: Int): Int = votesByOption[optionIndex]?.size ?: 0
    fun hasVoted(uid: String): Boolean = votesByOption.values.any { uid in it }
    fun votedOptions(uid: String): Set<Int> =
        votesByOption.filterValues { uid in it }.keys
}