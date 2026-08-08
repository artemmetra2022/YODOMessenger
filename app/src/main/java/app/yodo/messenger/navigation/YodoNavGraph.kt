package app.yodo.messenger.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.yodo.messenger.features.auth.LoginScreen
import app.yodo.messenger.features.auth.PhoneLoginScreen
import app.yodo.messenger.features.auth.RegisterScreen
import app.yodo.messenger.features.auth.WelcomeScreen
import app.yodo.messenger.features.chats.ChatScreen
import app.yodo.messenger.features.chats.ChatStatsScreen
// НОВОЕ (переработка каналов): три новых экрана
import app.yodo.messenger.features.chats.ChannelProfileScreen
import app.yodo.messenger.features.chats.CommentsScreen
import app.yodo.messenger.features.chats.CreateGroupScreen
import app.yodo.messenger.features.chats.EditChannelScreen
import app.yodo.messenger.features.chats.ForwardMessageScreen
import app.yodo.messenger.features.chats.GroupInfoScreen
import app.yodo.messenger.features.chats.ImageViewerHolder
import app.yodo.messenger.features.chats.ImageViewerScreen
import app.yodo.messenger.features.contacts.ContactsScreen
import app.yodo.messenger.features.main.MainScreen
import app.yodo.messenger.features.nearby.NearbyPeopleScreen
import app.yodo.messenger.features.notes.NotesScreen
import app.yodo.messenger.features.profile.ProfileScreen
import app.yodo.messenger.features.profile.UserProfileScreen
import app.yodo.messenger.features.search.SearchScreen
import app.yodo.messenger.features.settings.SettingsScreen
import app.yodo.messenger.offline.OfflineChatScreen

@Composable
fun YodoNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Welcome.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.Welcome.route) {
            WelcomeScreen(
                onStartClick = { navController.navigate(Routes.Login.route) },
                onRegisterClick = { navController.navigate(Routes.Register.route) },
                onOfflineModeClick = { navController.navigate(Routes.OfflineChat.route) }
            )
        }
        composable(Routes.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.Register.route) },
                onNavigateToPhoneLogin = { navController.navigate(Routes.PhoneLogin.route) }
            )
        }
        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.navigate(Routes.Login.route) }
            )
        }
        composable(Routes.PhoneLogin.route) {
            PhoneLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToEmailLogin = { navController.navigate(Routes.Login.route) }
            )
        }
        composable(Routes.ChatList.route) {
            MainScreen(
                onChatClick = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                },
                onSettingsClick = {
                    navController.navigate(Routes.Settings.route)
                },
                onSearchClick = {
                    navController.navigate(Routes.Search.route)
                },
                onNearbyClick = {
                    navController.navigate(Routes.NearbyPeople.route)
                },
                onProfileClick = {
                    navController.navigate(Routes.Profile.route)
                },
                onOfflineClick = {
                    navController.navigate(Routes.OfflineChat.route)
                },
                onCreateGroupClick = {
                    navController.navigate(Routes.CreateGroup.route)
                },
                onCreateChannelClick = {
                    navController.navigate(Routes.CreateChannel.route)
                },
                // НОВОЕ: кнопка "Контакты" перенесена из меню "+" в чате в главное меню "+".
                onOpenContacts = {
                    navController.navigate(Routes.Contacts.route)
                },
                // НОВОЕ (архивация чатов)
                onOpenArchive = {
                    navController.navigate(Routes.ArchivedChats.route)
                },
                // НОВОЕ (чат поддержки): открытие админ-панели поддержки (только для админов).
                onOpenAdminPanel = {
                    navController.navigate(Routes.AdminPanel.route)
                }
            )
        }
        // НОВОЕ (чат поддержки): админ-панель со списком обращений в поддержку.
        composable(Routes.AdminPanel.route) {
            app.yodo.messenger.features.chats.AdminPanelScreen(
                onBack = { navController.popBackStack() },
                onOpenConversation = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                }
            )
        }
        // НОВОЕ (архивация чатов): экран списка архивных чатов.
        composable(Routes.ArchivedChats.route) {
            app.yodo.messenger.features.chats.ArchivedChatsScreen(
                onChatClick = { chatId -> navController.navigate(Routes.Chat.createRoute(chatId)) },
                onBack = { navController.popBackStack() }
            )
        }
        // Profile Screen (Свой профиль) — п.35: добавлен переход в "Избранное"
        composable(Routes.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onOpenSavedMessages = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                },
                onOpenHistory = { navController.navigate(Routes.ProfileHistory.route) },
                onOpenQrCode = { navController.navigate(Routes.QrCode.route) },
                onOpenRecentCalls = { navController.navigate(Routes.RecentCalls.route) },
                onOpenDevices = { navController.navigate(Routes.Devices.route) },
                onLoggedOut = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
        // НОВОЕ: экраны-заглушки для блока профиля (История/QR-код/Звонки/Устройства)
        composable(Routes.ProfileHistory.route) {
            app.yodo.messenger.features.profile.ProfilePlaceholderScreen(
                title = "История",
                icon = androidx.compose.material.icons.Icons.Filled.History,
                description = "История изменений профиля появится здесь позже.",
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.QrCode.route) {
            app.yodo.messenger.features.profile.QrCodeScreen(
                onBackClick = { navController.popBackStack() },
                onScanClick = { navController.navigate(Routes.ScanContact.route) }
            )
        }
        // НОВОЕ (офлайн обмен контактами по QR): экран сканирования QR-кода контакта.
        composable(Routes.ScanContact.route) {
            app.yodo.messenger.features.contacts.ScanContactScreen(
                onBackClick = { navController.popBackStack() },
                onOpenChat = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.ScanContact.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.RecentCalls.route) {
            app.yodo.messenger.features.profile.ProfilePlaceholderScreen(
                title = "Недавние звонки",
                icon = androidx.compose.material.icons.Icons.Filled.Call,
                description = "Здесь появится история ваших звонков.",
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.Devices.route) {
            app.yodo.messenger.features.profile.DevicesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.Chat.route,
            arguments = listOf(navArgument(Routes.Chat.ARG_CHAT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString(Routes.Chat.ARG_CHAT_ID) ?: ""
            ChatScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() },
                onOpenUserProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                },
                onOpenGroupInfo = { groupId ->
                    navController.navigate(Routes.GroupInfo.createRoute(groupId))
                },
                onOpenChatStats = { statsChatId ->
                    navController.navigate(Routes.ChatStats.createRoute(statsChatId))
                },
                onForwardMessage = {
                    navController.navigate(Routes.ForwardMessage.route)
                },
                onOpenImageViewer = { imageBase64, senderName, timestamp ->
                    ImageViewerHolder.imageBase64 = imageBase64
                    ImageViewerHolder.senderName = senderName
                    ImageViewerHolder.timestamp = timestamp
                    navController.navigate(Routes.ImageViewer.route)
                },
                // НОВОЕ (переработка каналов): тап по шапке канала → профиль канала;
                // кнопка "Комментарии" под постом → экран комментариев.
                onOpenChannelProfile = { channelId ->
                    navController.navigate(Routes.ChannelProfile.createRoute(channelId))
                },
                onOpenComments = { cId, mId ->
                    navController.navigate(Routes.Comments.createRoute(cId, mId))
                },
                // НОВОЕ: "Пригласить в канал" из меню чата → экран выбора контактов.
                onInviteToChannel = { cId ->
                    navController.navigate(Routes.InviteToChannel.createRoute(cId))
                },
                // НОВОЕ (QR в личном чате): "Поделиться контактом" → мой QR-код контакта.
                onShareContactQr = {
                    navController.navigate(Routes.QrCode.route)
                }
            )
        }
        composable(Routes.ImageViewer.route) {
            ImageViewerScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        // НОВОЕ (переработка каналов): публичный профиль канала.
        composable(
            route = Routes.ChannelProfile.route,
            arguments = listOf(navArgument(Routes.ChannelProfile.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            ChannelProfileScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                },
                onEditChannel = { chatId ->
                    navController.navigate(Routes.EditChannel.createRoute(chatId))
                },
                onOpenUserProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                },
                onManageRoles = { chatId ->
                    navController.navigate(Routes.ManageRoles.createRoute(chatId))
                },
                // НОВОЕ: после удаления канала владельцем — назад в список чатов.
                onChannelDeleted = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.ChatList.route) { inclusive = true }
                    }
                }
            )
        }
        // НОВОЕ (переработка каналов): редактирование канала (владелец).
        composable(
            route = Routes.EditChannel.route,
            arguments = listOf(navArgument(Routes.EditChannel.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            EditChannelScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        // НОВОЕ: приглашение контактов в канал (владелец/админ, из меню чата).
        composable(
            route = Routes.InviteToChannel.route,
            arguments = listOf(navArgument(Routes.InviteToChannel.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            app.yodo.messenger.features.chats.InviteToChannelScreen(
                onBackClick = { navController.popBackStack() },
                onInvited = { navController.popBackStack() }
            )
        }
        // НОВОЕ (переработка каналов): комментарии к посту канала.
        composable(
            route = Routes.Comments.route,
            arguments = listOf(
                navArgument(Routes.Comments.ARG_CHAT_ID) { type = NavType.StringType },
                navArgument(Routes.Comments.ARG_MESSAGE_ID) { type = NavType.StringType }
            )
        ) {
            CommentsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.GroupInfo.route,
            arguments = listOf(navArgument(Routes.GroupInfo.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            GroupInfoScreen(
                onBackClick = { navController.popBackStack() },
                onLeftGroup = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.ChatList.route) { inclusive = true }
                    }
                },
                onOpenManageRoles = { chatId ->
                    navController.navigate(Routes.ManageRoles.createRoute(chatId))
                }
            )
        }
        composable(
            route = Routes.ChatStats.route,
            arguments = listOf(navArgument(Routes.ChatStats.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            ChatStatsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        // НОВОЕ (система ролей с гранулярными правами): управление ролями участников.
        composable(
            route = Routes.ManageRoles.route,
            arguments = listOf(navArgument(Routes.ManageRoles.ARG_CHAT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString(Routes.ManageRoles.ARG_CHAT_ID).orEmpty()
            app.yodo.messenger.features.chats.ManageRolesScreen(
                onBackClick = { navController.popBackStack() },
                onOpenAdminLog = { navController.navigate(Routes.AdminLog.createRoute(chatId)) },
                onOpenReportQueue = { navController.navigate(Routes.ReportQueue.createRoute(chatId)) }
            )
        }
        // НОВОЕ (журнал действий администраторов): просмотр и фильтрация лога.
        composable(
            route = Routes.AdminLog.route,
            arguments = listOf(navArgument(Routes.AdminLog.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            app.yodo.messenger.features.chats.AdminLogScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        // НОВОЕ (система жалоб с очередью, п.5 ТЗ): очередь жалоб и детальный просмотр.
        composable(
            route = Routes.ReportQueue.route,
            arguments = listOf(navArgument(Routes.ReportQueue.ARG_CHAT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString(Routes.ReportQueue.ARG_CHAT_ID).orEmpty()
            app.yodo.messenger.features.chats.ReportQueueScreen(
                onBackClick = { navController.popBackStack() },
                onOpenReport = { reportId ->
                    navController.navigate(Routes.ReportDetail.createRoute(chatId, reportId))
                }
            )
        }
        composable(
            route = Routes.ReportDetail.route,
            arguments = listOf(
                navArgument(Routes.ReportDetail.ARG_CHAT_ID) { type = NavType.StringType },
                navArgument(Routes.ReportDetail.ARG_REPORT_ID) { type = NavType.StringType }
            )
        ) {
            app.yodo.messenger.features.chats.ReportDetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.CreateGroup.route) {
            CreateGroupScreen(
                onBackClick = { navController.popBackStack() },
                onGroupCreated = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.CreateGroup.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CreateChannel.route) {
            app.yodo.messenger.features.chats.CreateChannelScreen(
                onBackClick = { navController.popBackStack() },
                onChannelCreated = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.CreateChannel.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ForwardMessage.route) {
            ForwardMessageScreen(
                onBackClick = { navController.popBackStack() },
                onForwarded = { targetChatId ->
                    // Уходим в чат, куда переслали сообщение — там же покажется
                    // плашка "Сообщение переслано" с окном отмены (п.2).
                    navController.navigate(Routes.Chat.createRoute(targetChatId)) {
                        popUpTo(Routes.ForwardMessage.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.UserProfile.route,
            arguments = listOf(navArgument(Routes.UserProfile.ARG_USER_ID) { type = NavType.StringType })
        ) {
            UserProfileScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                }
            )
        }
        composable(Routes.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                },
                onViewProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                },
                // НОВОЕ (переработка каналов): тап по каналу в выдаче поиска —
                // в его профиль (если не подписан) или сразу в чат (если подписан).
                onOpenChannelProfile = { channelId ->
                    navController.navigate(Routes.ChannelProfile.createRoute(channelId))
                }
            )
        }
        composable(Routes.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Routes.Profile.route) },
                onLoggedOut = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onOpenNotes = { navController.navigate(Routes.Notes.route) }
            )
        }
        // НОВОЕ: личный блокнот «Заметки».
        composable(Routes.Notes.route) {
            NotesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.OfflineChat.route) {
            OfflineChatScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.NearbyPeople.route) {
            NearbyPeopleScreen(
                onBackClick = { navController.popBackStack() },
                onPersonClick = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                }
            )
        }
        // НОВОЕ: экран "Контакты" — выбор контактов из телефонной книги с переходом в профиль.
        composable(Routes.Contacts.route) {
            ContactsScreen(
                onBackClick = { navController.popBackStack() },
                onOpenProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                }
            )
        }
        // НОВОЕ (блокировка): реальный экран со списком заблокированных и разблокировкой.
        composable(Routes.BlockedUsers.route) {
            app.yodo.messenger.features.profile.BlockedUsersScreen(
                onBackClick = { navController.popBackStack() },
                onOpenProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                }
            )
        }
        // Заглушка: "Избранное" как от��ельный экран (альтернатива переходу через chatId).
        composable(Routes.SavedMessages.route) {
            app.yodo.messenger.features.profile.ProfilePlaceholderScreen(
                title = "Избранное",
                icon = androidx.compose.material.icons.Icons.Filled.Bookmarks,
                description = "Ваши сохранённые сообщения появятся здесь.",
                onBackClick = { navController.popBackStack() }
            )
        }
        // Заглушка: экран звонка (функция в разработке).
        composable(
            route = Routes.Call.route,
            arguments = listOf(navArgument(Routes.Call.ARG_USER_ID) { type = NavType.StringType })
        ) {
            app.yodo.messenger.features.profile.ProfilePlaceholderScreen(
                title = "Звонок",
                icon = androidx.compose.material.icons.Icons.Filled.Call,
                description = "Функция звонков находится �� разработке.",
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}