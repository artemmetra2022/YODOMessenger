package app.yodo.messenger.navigation

sealed class Routes(val route: String) {

    data object Welcome : Routes("welcome")
    // НОВОЕ: экран обучения (онбординг) — показывается один раз сразу после первой регистрации.
    data object Onboarding : Routes("onboarding")
    // НОВОЕ (обучение): повторный показ того же экрана из настроек — отдельный route,
    // чтобы не путать со связанной с ним логикой popUpTo(Welcome) при первой регистрации.
    data object OnboardingReplay : Routes("onboarding_replay")
    data object Login : Routes("login")
    // НОВОЕ: экран ввода 6-значного email-кода сразу после успешного входа
    // (пароль/username, телефон или Google) — только если у пользователя включена 2FA.
    data object TwoFactorGate : Routes("two_factor_gate")
    data object PhoneLogin : Routes("phone_login")
    data object ForgotPassword : Routes("forgot_password")
    data object Register : Routes("register")
    // НОВОЕ: экран ожидания подтверждения email после регистрации.
    data object VerifyEmail : Routes("verify_email/{email}") {
        fun createRoute(email: String) = "verify_email/${java.net.URLEncoder.encode(email, "UTF-8")}"
        const val ARG_EMAIL = "email"
    }
    data object ChatList : Routes("chat_list")
    // НОВОЕ (каталог/рекомендации каналов): витрина каналов без поискового запроса.
    data object DiscoverChannels : Routes("discover_channels")
    data object Search : Routes("search")
    data object CreateGroup : Routes("create_group")
    data object CreateChannel : Routes("create_channel")
    data object OfflineChat : Routes("offline_chat")
    data object NearbyPeople : Routes("nearby_people")
    // НОВОЕ: экран "Контакты" — контакты из телефонной книги, у кого есть аккаунт в Yodo.
    data object Contacts : Routes("contacts")
    data object ForwardMessage : Routes("forward_message")
    data object Profile : Routes("profile")
    data object Settings : Routes("settings?anchor={anchor}") {
        // НОВОЕ (поиск по настройкам): переход из общего поиска на конкретный пункт настроек.
        fun createRoute(anchor: String? = null) =
            if (anchor.isNullOrBlank()) "settings" else "settings?anchor=$anchor"
        const val ARG_ANCHOR = "anchor"
    }
    // НОВОЕ (батч 7): экран «Фишки и инструменты» (20 новых функций).
    data object Tools : Routes("tools")
    data object SecurityCenter : Routes("security_center")
    // НОВОЕ (Y): смена аккаунта и добавление нового аккаунта.
    data object SwitchAccount : Routes("switch_account")
    data object AddAccount : Routes("add_account")
    data object BlockedUsers : Routes("blocked_users")
    // НОВОЕ (архивация чатов)
    data object ArchivedChats : Routes("archived_chats")
    data object SavedMessages : Routes("saved_messages")
    // НОВОЕ: экраны-заглушки для блока профиля (История/QR-код/Звонки/Устройства)
    data object ProfileHistory : Routes("profile_history")
    data object QrCode : Routes("qr_code")
    data object ScanContact : Routes("scan_contact")
    // НОВОЕ (поделиться контактом абонента): QR-карточка контакта собеседника.
    data object ContactQr : Routes("contact_qr/{userId}") {
        fun createRoute(userId: String) = "contact_qr/$userId"
        const val ARG_USER_ID = "userId"
    }
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
    // НОВОЕ (форумные группы): список разделов (тем) форума.
    data object ForumTopics : Routes("forum_topics/{chatId}") {
        fun createRoute(chatId: String) = "forum_topics/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object ChatStats : Routes("chat_stats/{chatId}") {
        fun createRoute(chatId: String) = "chat_stats/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    // НОВОЕ (форумные группы): topicId/topicTitle — необязательные query-параметры.
    // Если чат открыт из раздела форума, здесь передаётся id и название темы;
    // при обычном открытии чата (из списка чатов) параметры отсутствуют — старое
    // поведение полностью сохраняется.
    data object Chat : Routes("chat/{chatId}?topicId={topicId}&topicTitle={topicTitle}") {
        fun createRoute(chatId: String, topicId: String? = null, topicTitle: String? = null): String {
            val base = "chat/$chatId"
            if (topicId.isNullOrBlank()) return base
            val encodedTitle = java.net.URLEncoder.encode(topicTitle.orEmpty(), "UTF-8")
            return "$base?topicId=$topicId&topicTitle=$encodedTitle"
        }
        const val ARG_CHAT_ID = "chatId"
        const val ARG_TOPIC_ID = "topicId"
        const val ARG_TOPIC_TITLE = "topicTitle"
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

    // НОВОЕ (система ролей + журнал администраторов):
    data object ManageRoles : Routes("manage_roles/{chatId}") {
        fun createRoute(chatId: String) = "manage_roles/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    // НОВОЕ (статистика для владельца канала): расширенная аналитика канала.
    data object ChannelStats : Routes("channel_stats/{chatId}") {
        fun createRoute(chatId: String) = "channel_stats/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object AdminLog : Routes("admin_log/{chatId}") {
        fun createRoute(chatId: String) = "admin_log/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    // НОВОЕ (система жалоб с очередью, п.5 ТЗ):
    data object ReportQueue : Routes("report_queue/{chatId}") {
        fun createRoute(chatId: String) = "report_queue/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
    data object ReportDetail : Routes("report_detail/{chatId}/{reportId}") {
        fun createRoute(chatId: String, reportId: String) = "report_detail/$chatId/$reportId"
        const val ARG_CHAT_ID = "chatId"
        const val ARG_REPORT_ID = "reportId"
    }
    // НОВОЕ (AC): глобальный раздел «Жалобы» для главных админов (2 почты).
    data object ReportInbox : Routes("report_inbox")
    // НОВОЕ (чат поддержки): экран админ-панели поддержки.
    data object AdminPanel : Routes("admin_panel")

    // НОВОЕ: личный блокнот «Заметки».
}