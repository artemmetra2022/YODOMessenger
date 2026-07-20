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
    // emoji -> список uid, поставивших эту реакцию
    val reactions: Map<String, List<String>> = emptyMap(),
    val imageBase64: String? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val forwardedFromSenderName: String? = null
)
