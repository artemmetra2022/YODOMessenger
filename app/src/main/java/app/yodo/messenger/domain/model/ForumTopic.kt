package app.yodo.messenger.domain.model

data class ForumTopic(
    val id: String,
    val title: String,
    val createdBy: String,
    val createdAt: Long,
    val isClosed: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L
)
