package app.yodo.messenger.features.chats

internal enum class ChatMenuId { PROFILE, SEARCH, MEDIA, BOARD, VERIFY_KEYS, INVITE, SCHEDULED, CHANNEL_STATS,
    SUBSCRIPTION, DISAPPEARING, SHARE_CONTACT, CHAT_STATS, EXPORT, SCREENSHOT, CLEAR, DELETE_CHAT, DELETE_CHANNEL }

internal data class ChatMenuContext(
    val chatId: String,
    val chatType: String,
    val isAdmin: Boolean = false,
    val isChannelOwner: Boolean = false,
    val hasOtherUser: Boolean = false
)

/** Retains the previous role/type checks; presentation never grants server permissions. */
internal fun chatMenuIds(context: ChatMenuContext): List<ChatMenuId> = with(context) {
    val channel = chatType == "CHANNEL"
    buildList {
        if (chatType in setOf("GROUP", "CHANNEL") || hasOtherUser) add(ChatMenuId.PROFILE)
        add(ChatMenuId.SEARCH)
        // НОВОЕ (раздел «Медиа, файлы, ссылки»): доступен во всех типах чатов.
        add(ChatMenuId.MEDIA)
        add(ChatMenuId.BOARD)
        if (chatType == "PRIVATE" && !chatId.startsWith("support_") && !chatId.startsWith("saved_")) add(ChatMenuId.VERIFY_KEYS)
        if (channel && isAdmin) {
            add(ChatMenuId.INVITE); add(ChatMenuId.SCHEDULED); add(ChatMenuId.CHANNEL_STATS)
        }
        if (channel && !isChannelOwner) add(ChatMenuId.SUBSCRIPTION)
        if (!channel) add(ChatMenuId.DISAPPEARING)
        if (chatType == "PRIVATE" && hasOtherUser) add(ChatMenuId.SHARE_CONTACT)
        add(ChatMenuId.CHAT_STATS); add(ChatMenuId.EXPORT); add(ChatMenuId.SCREENSHOT)
        add(ChatMenuId.CLEAR)
        add(if (channel && isChannelOwner) ChatMenuId.DELETE_CHANNEL else ChatMenuId.DELETE_CHAT)
    }
}
