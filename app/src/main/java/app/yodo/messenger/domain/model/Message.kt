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
    val isPinned: Boolean = false
) {
    /**
     * НОВОЕ (п.6): текст для превью сообщения (цитата ответа и т.п.).
     * Если у сообщения нет текста, но есть картинка — показываем "Картинка" вместо пустой строки.
     * Единая точка правды: используется и при формировании ReplyContext на отправке,
     * и в UI (ReplyPreviewBar, цитата внутри бабла).
     */
    fun previewText(): String = when {
        text.isNotBlank() -> text
        imageBase64 != null -> "Картинка"
        else -> text
    }
}
