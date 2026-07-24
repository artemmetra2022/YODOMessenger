package app.yodo.messenger.domain.model

enum class ChatType { PRIVATE, GROUP, CHANNEL }

data class ChatPreview(
    val chatId: String,
    val title: String,
    val username: String? = null,
    val avatarUrl: String?,
    // ИСПРАВЛЕНИЕ (баг "аватарки не видны у других"): раньше это поле отсутствовало,
    // и ChatListScreen передавал в UserAvatar жёстко null. Теперь base64-аватар
    // собеседника (для приватных чатов) реально доходит до списка чатов.
    val avatarBase64: String? = null,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val isOnline: Boolean,
    val type: ChatType,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    // Внутреннее поле — id собеседника для приватного чата, нужно ChatRepositoryImpl,
    // чтобы дозагрузить его аватар. UI это поле не использует напрямую.
    val otherUserId: String? = null
)
