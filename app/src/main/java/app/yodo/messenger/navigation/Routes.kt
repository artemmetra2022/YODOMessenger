package app.yodo.messenger.navigation

/**
 * Единый источник правды для маршрутов навигации.
 * Экраны по ТЗ: Приветствие, Вход, Регистрация, Список чатов,
 * Личный чат, Групповой чат, Профиль, Настройки, Звонок, Видеозвонок.
 */
sealed class Routes(val route: String) {
    data object Welcome : Routes("welcome")
    data object Login : Routes("login")
    data object PhoneLogin : Routes("phone_login")
    data object Register : Routes("register")
    data object ChatList : Routes("chat_list")
    data object Search : Routes("search")
    data object CreateGroup : Routes("create_group")
    data object OfflineChat : Routes("offline_chat")
    data object NearbyPeople : Routes("nearby_people")
    data object ForwardMessage : Routes("forward_message")

    data object GroupInfo : Routes("group_info/{chatId}") {
        fun createRoute(chatId: String) = "group_info/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }

    data object Profile : Routes("profile")
    data object Settings : Routes("settings")

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
}
