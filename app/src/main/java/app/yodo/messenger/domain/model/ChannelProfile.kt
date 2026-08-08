package app.yodo.messenger.domain.model

/**
 * НОВОЕ (переработка каналов): полный профиль канала для экрана ChannelProfileScreen.
 * Собирается из документа chats/{chatId} (type == "CHANNEL").
 */
data class ChannelProfile(
    val chatId: String,
    val title: String,
    val description: String,
    val avatarBase64: String?,
    val subscriberCount: Int,
    val isVerified: Boolean,
    val ownerId: String?,
    val adminIds: List<String>,
    val isSubscribed: Boolean,
    val createdAt: Long,
    // НОВОЕ (F5 категории/обложка канала):
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val coverBase64: String? = null
)