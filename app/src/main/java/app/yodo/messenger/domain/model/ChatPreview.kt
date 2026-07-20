package app.yodo.messenger.domain.model

enum class ChatType { PRIVATE, GROUP, CHANNEL }

data class ChatPreview(
    val chatId: String,
    val title: String,
    val avatarUrl: String?,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val isOnline: Boolean,
    val type: ChatType,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
)
