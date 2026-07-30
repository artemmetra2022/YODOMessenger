package app.yodo.messenger.navigation

sealed class Routes(val route: String) {

    data object Welcome : Routes("welcome")
    data object Login : Routes("login")
    data object PhoneLogin : Routes("phone_login")
    data object Register : Routes("register")
    data object ChatList : Routes("chat_list")
    data object Search : Routes("search")
    data object CreateGroup : Routes("create_group")
    data object CreateChannel : Routes("create_channel")
    data object OfflineChat : Routes("offline_chat")
    data object NearbyPeople : Routes("nearby_people")
    // НОВОЕ: экран "Контакты" — контакты из телефонной книги, у кого есть аккаунт в Yodo.
    data object Contacts : Routes("contacts")
    data object ForwardMessage : Routes("forward_message")
    data object Profile : Routes("profile")
    data object Settings : Routes("settings")
    data object BlockedUsers : Routes("blocked_users")
    // НОВОЕ (архивация чатов)
    data object ArchivedChats : Routes("archived_chats")
    data object SavedMessages : Routes("saved_messages")
    // НОВОЕ: экраны-заглушки для блока профиля (История/QR-код/Звонки/Устройства)
    data object ProfileHistory : Routes("profile_history")
    data object QrCode : Routes("qr_code")
    data object RecentCalls : Routes("recent_calls")
    data object Devices : Routes("devices")

    // НОВОЕ (переработка каналов):
    data object ChannelProfile : Routes("channel_profile/{chatId}") {
        fun createRoute(chatId: String) = "channel_profile/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object EditChannel : Routes("edit_channel/{chatId}") {
        fun createRoute(chatId: String) = "edit_channel/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    // НОВОЕ: приглашение контактов в канал из меню чата (три точки).
    data object InviteToChannel : Routes("invite_to_channel/{chatId}") {
        fun createRoute(chatId: String) = "invite_to_channel/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object Comments : Routes("comments/{chatId}/{messageId}") {
        fun createRoute(chatId: String, messageId: String) = "comments/$chatId/$messageId"
        const val ARG_CHAT_ID = "chatId"
        const val ARG_MESSAGE_ID = "messageId"
    }

    data object GroupInfo : Routes("group_info/{chatId}") {
        fun createRoute(chatId: String) = "group_info/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object ChatStats : Routes("chat_stats/{chatId}") {
        fun createRoute(chatId: String) = "chat_stats/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object Chat : Routes("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object Call : Routes("call/{userId}") {
        fun createRoute(userId: String) = "call/$userId"
        const val ARG_USER_ID = "userId"
    }
    data object UserProfile : Routes("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
        const val ARG_USER_ID = "userId"
    }
    data object ImageViewer : Routes("image_viewer")
}