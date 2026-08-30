package app.yodo.messenger.domain.model

// ИСПРАВЛЕНО (индикатор прочитано/доставлено): добавлено промежуточное состояние
// DELIVERED. Раньше статус прыгал сразу SENT -> READ, из-за чего получатель не мог
// увидеть, что сообщение дошло до сервера/устройства собеседника, но ещё не прочитано.
enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val topicId: String? = null,
    val text: String,
    val timestamp: Long,
    val status: MessageStatus,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val imageBase64: String? = null,
    // НОВОЕ (несколько фото в одном сообщении): если не пустой — это альбом из
    // нескольких фото, которые показываются сеткой/коллажем в одном пузыре.
    val imagesBase64: List<String> = emptyList(),
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    // НОВОЕ (баг 10): сообщение удалено администратором (по жалобе или модерацией) —
    // вместо обычной заглушки показываем «Сообщение удалено администратором».
    val deletedByAdmin: Boolean = false,
    val forwardedFromSenderName: String? = null,
    val forwardedFromSenderId: String? = null,
    val forwardedFromSenderPhotoUrl: String? = null,
    val forwardedFromSenderAvatarBase64: String? = null,
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
    val poll: Poll? = null,
    // НОВОЕ (одноразовые медиа): фото "на один просмотр" — как только получатель открыл его
    // полноэкранно один раз, imageBase64 удаляется на сервере (см. MessageRepositoryImpl.
    // markViewOnceImageOpened) и локально показывается только заглушка "Фото открыто".
    val isViewOnce: Boolean = false,
    val viewOnceOpened: Boolean = false,
    // НОВОЕ (F3 статистика постов канала): число уникальных просмотров поста.
    val viewCount: Int = 0
) {
    fun previewText(): String = when {
        text.isNotBlank() -> text
        poll != null -> "📊 ${poll.question}"
        voiceBase64 != null -> "Голосовое сообщение"
        isViewOnce -> if (viewOnceOpened) "Фото открыто" else "📷 Фото (один просмотр)"
        imagesBase64.isNotEmpty() -> if (imagesBase64.size > 1) "📷 Фото (${imagesBase64.size})" else "Картинка"
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
    val isClosed: Boolean = false,
    // НОВОЕ (расширенные опросы): если задано — опрос автоматически считается закрытым
    // после этого момента времени (мс), независимо от isClosed.
    val closesAt: Long? = null,
    // НОВОЕ (викторина): режим викторины. isQuiz=true — у опроса есть один правильный
    // ответ (correctOptionIndex) и необязательное пояснение (explanation), которое
    // показывается участнику после голосования.
    val isQuiz: Boolean = false,
    val correctOptionIndex: Int? = null,
    val explanation: String? = null
) {
    fun totalVotes(): Int = votesByOption.values.sumOf { it.size }
    /** Викторина: является ли вариант правильным ответом. */
    fun isCorrectOption(optionIndex: Int): Boolean = isQuiz && correctOptionIndex == optionIndex
    /** Викторина: ответил ли пользователь правильно (проголосовал за верный вариант). */
    fun answeredCorrectly(uid: String): Boolean =
        isQuiz && correctOptionIndex != null && correctOptionIndex in votedOptions(uid)
    fun votesFor(optionIndex: Int): Int = votesByOption[optionIndex]?.size ?: 0
    fun hasVoted(uid: String): Boolean = votesByOption.values.any { uid in it }
    fun votedOptions(uid: String): Set<Int> =
        votesByOption.filterValues { uid in it }.keys
    /** Опрос считается завершённым, если его явно закрыли или истёк срок действия. */
    fun isEffectivelyClosed(nowMillis: Long = System.currentTimeMillis()): Boolean =
        isClosed || (closesAt != null && closesAt <= nowMillis)
    /** Ведущий вариант (для отображения результата после закрытия). Null при равенстве голосов. */
    fun leadingOption(): Int? {
        val maxVotes = votesByOption.values.maxOfOrNull { it.size } ?: return null
        if (maxVotes == 0) return null
        val leaders = votesByOption.filterValues { it.size == maxVotes }.keys
        return leaders.singleOrNull()
    }
}