package app.yodo.messenger.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.yodo.messenger.features.auth.ForgotPasswordScreen
import app.yodo.messenger.features.auth.LoginScreen
import app.yodo.messenger.features.auth.PhoneLoginScreen
import app.yodo.messenger.features.auth.RegisterScreen
import app.yodo.messenger.features.auth.VerifyEmailScreen
import app.yodo.messenger.features.auth.WelcomeScreen
import app.yodo.messenger.features.auth.GlobalBlockGateScreen
import app.yodo.messenger.features.auth.GlobalBlockViewModel
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
import app.yodo.messenger.features.onboarding.OnboardingScreen
import app.yodo.messenger.features.onboarding.OnboardingViewModel
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
    // НОВОЕ (AD): вьюмодель глобальной блокировки на уровне всего навиграфа.
    val globalBlockViewModel: GlobalBlockViewModel = hiltViewModel()
    val globalBlock by globalBlockViewModel.globalBlock.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
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
                    navController.navigate(Routes.TwoFactorGate.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onRequiresVerification = { email ->
                    navController.navigate(Routes.VerifyEmail.createRoute(email))
                },
                onNavigateToRegister = { navController.navigate(Routes.Register.route) },
                onNavigateToPhoneLogin = { navController.navigate(Routes.PhoneLogin.route) },
                onNavigateToForgotPassword = { navController.navigate(Routes.ForgotPassword.route) }
            )
        }
        composable(Routes.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    // После первой регистрации ведём пользователя на обучение,
                    // а не сразу в список чатов.
                    navController.navigate(Routes.Onboarding.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onRequiresVerification = { email ->
                    // НОВОЕ: сразу после регистрации — экран "подтвердите почту",
                    // а не онбординг/чаты. Убираем Register из бэкстека.
                    navController.navigate(Routes.VerifyEmail.createRoute(email)) {
                        popUpTo(Routes.Welcome.route) { inclusive = false }
                    }
                },
                onNavigateToLogin = { navController.navigate(Routes.Login.route) }
            )
        }
        // НОВОЕ: экран ожидания подтверждения email по ссылке из письма.
        composable(Routes.VerifyEmail.route) { backStackEntry ->
            val encodedEmail = backStackEntry.arguments?.getString(Routes.VerifyEmail.ARG_EMAIL).orEmpty()
            val email = java.net.URLDecoder.decode(encodedEmail, "UTF-8")
            VerifyEmailScreen(
                email = email,
                onVerified = {
                    // Подтвердили — как и при обычной регистрации, ведём на онбординг.
                    navController.navigate(Routes.Onboarding.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    // Выходим из недоподтверждённого аккаунта и возвращаемся ко входу.
                    navController.navigate(Routes.Login.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = false }
                    }
                }
            )
        }
        // НОВОЕ: экран обучения — показывается один раз, сразу после первой регистрации.
        composable(Routes.Onboarding.route) {
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                onFinish = {
                    onboardingViewModel.markOnboardingCompleted()
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Onboarding.route) { inclusive = true }
                    }
                },
                onSkip = {
                    onboardingViewModel.markOnboardingCompleted()
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        // НОВОЕ (Y): экран смены аккаунта.
        composable(Routes.SwitchAccount.route) {
            app.yodo.messenger.features.auth.SwitchAccountScreen(
                onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(Routes.AddAccount.route) },
                onSwitched = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
        // НОВОЕ (Y): отдельное окно добавления аккаунта.
        composable(Routes.AddAccount.route) {
            app.yodo.messenger.features.auth.AddAccountScreen(
                onBack = { navController.popBackStack() },
                onAdded = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PhoneLogin.route) {
            PhoneLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.TwoFactorGate.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToEmailLogin = { navController.navigate(Routes.Login.route) }
            )
        }
        // НОВОЕ: гейт 2FA — показывается сразу после успешного входа (пароль/username,
        // телефон или Google), если у пользователя включена 2FA по email. Если 2FA
        // выключена, TwoFactorGateScreen сам сразу пропускает дальше (см. onPassed).
        composable(Routes.TwoFactorGate.route) {
            app.yodo.messenger.features.auth.TwoFactorGateScreen(
                onPassed = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.TwoFactorGate.route) { inclusive = true }
                    }
                },
                onCancelled = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(Routes.TwoFactorGate.route) { inclusive = true }
                    }
                }
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
                },
                // НОВОЕ (каталог/рекомендации каналов): открытие витрины каналов.
                onDiscoverChannels = {
                    navController.navigate(Routes.DiscoverChannels.route)
                },
                // НОВОЕ (админ-функции групп): тап по бейджу заявок на карточке группы —
                // сразу к экрану информации о группе (раздел «Заявки»).
                onOpenGroupInfo = { chatId ->
                    navController.navigate(Routes.GroupInfo.createRoute(chatId))
                }
            )
        }

        // НОВОЕ (каталог/рекомендации каналов): витрина каналов без поискового запроса.
        composable(Routes.DiscoverChannels.route) {
            app.yodo.messenger.features.chats.DiscoverChannelsScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                },
                onOpenChannelProfile = { channelId ->
                    navController.navigate(Routes.ChannelProfile.createRoute(channelId))
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
        // НОВОЕ (История изменений профиля): реальный экран вместо заглушки.
        composable(Routes.ProfileHistory.route) {
            app.yodo.messenger.features.profile.ProfileHistoryScreen(
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
        // НОВОЕ (батч 7): экран «Фишки и инструменты».
        composable(Routes.Tools.route) {
            app.yodo.messenger.features.tools.ToolsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.SecurityCenter.route) {
            app.yodo.messenger.features.security.SecurityCenterScreen(
                onBackClick = { navController.popBackStack() },
                onOpenQrLogin = { navController.navigate(Routes.QrLogin.route) }
            )
        }
        // НОВОЕ (вход по QR-коду): сканер QR с веб-версии.
        composable(Routes.QrLogin.route) {
            app.yodo.messenger.features.security.QrLoginScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.Chat.route,
            arguments = listOf(
                navArgument(Routes.Chat.ARG_CHAT_ID) { type = NavType.StringType },
                // НОВОЕ (форумные группы): необязательные параметры темы форума.
                navArgument(Routes.Chat.ARG_TOPIC_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Routes.Chat.ARG_TOPIC_TITLE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
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
                // НОВОЕ (поделиться контактом абонента): "Поделиться контактом" → QR-код контакта собеседника.
                onShareContactQr = { otherUserId ->
                    navController.navigate(Routes.ContactQr.createRoute(otherUserId))
                }
            )
        }
        // НОВОЕ (поделиться контактом абонента): QR-карточка контакта собеседника.
        composable(
            route = Routes.ContactQr.route,
            arguments = listOf(navArgument(Routes.ContactQr.ARG_USER_ID) { type = NavType.StringType })
        ) {
            app.yodo.messenger.features.contacts.ContactQrScreen(
                onBackClick = { navController.popBackStack() }
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
                // НОВОЕ (статистика для владельца канала): переход к расширенной аналитике канала.
                onOpenChannelStats = { chatId ->
                    navController.navigate(Routes.ChannelStats.createRoute(chatId))
                },
                // НОВОЕ: после удаления канала владельцем — назад в список чатов.
                onChannelDeleted = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.ChatList.route) { inclusive = true }
                    }
                }
            )
        }
        // НОВОЕ (статистика для владельца канала): расширенная аналитика канала.
        composable(
            route = Routes.ChannelStats.route,
            arguments = listOf(navArgument(Routes.ChannelStats.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            app.yodo.messenger.features.chats.ChannelStatsScreen(
                onBackClick = { navController.popBackStack() }
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
                },
                onOpenForumTopics = { chatId ->
                    navController.navigate(Routes.ForumTopics.createRoute(chatId))
                }
            )
        }
        // НОВОЕ (форумные группы): список разделов (тем) форума.
        composable(
            route = Routes.ForumTopics.route,
            arguments = listOf(navArgument(Routes.ForumTopics.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            app.yodo.messenger.features.chats.ForumTopicsScreen(
                onBackClick = { navController.popBackStack() },
                onOpenTopic = { chatId, topicId, topicTitle ->
                    navController.navigate(Routes.Chat.createRoute(chatId, topicId, topicTitle))
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
        // НОВОЕ (AC): глобальный раздел «Жалобы» для главных админов.
        composable(Routes.ReportInbox.route) {
            app.yodo.messenger.features.chats.ReportInboxScreen(
                onBack = { navController.popBackStack() },
                onOpenReport = { chatId, reportId ->
                    navController.navigate(Routes.ReportDetail.createRoute(chatId, reportId))
                }
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
                },
                // НОВОЕ (админ-функции групп): тап по группе в выдаче поиска —
                // в профиль группы (если не участник) или сразу в чат (если участник).
                onOpenGroupProfile = { chatId ->
                    navController.navigate(Routes.GroupProfile.createRoute(chatId))
                },
                // НОВОЕ (поиск по настройкам): тап по найденной настройке — открываем
                // экран настроек и прокручиваем сразу к нужному пункту.
                onOpenSettings = { anchorId ->
                    navController.navigate(Routes.Settings.createRoute(anchorId))
                }
            )
        }
        // НОВОЕ (админ-функции групп): профиль-превью группы из поиска.
        composable(
            route = Routes.GroupProfile.route,
            arguments = listOf(navArgument(Routes.GroupProfile.ARG_CHAT_ID) { type = NavType.StringType })
        ) {
            app.yodo.messenger.features.chats.GroupProfileScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                }
            )
        }
        composable(
            route = Routes.Settings.route,
            arguments = listOf(navArgument(Routes.Settings.ARG_ANCHOR) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Routes.Profile.route) },
                // ИСПРАВЛЕНО (AB): кнопка «Заблокированные пользователи» теперь открывает экран.
                onOpenBlockedUsers = { navController.navigate(Routes.BlockedUsers.route) },
                // НОВОЕ (п.15): настройки приватности «Кто может …».
                onOpenPrivacyWho = { navController.navigate(Routes.PrivacyWho.route) },
                // НОВОЕ (Y): открыть экран смены аккаунта.
                onSwitchAccount = { navController.navigate(Routes.SwitchAccount.route) },
                // НОВОЕ (батч 7): открыть «Фишки и инструменты».
                onOpenTools = { navController.navigate(Routes.Tools.route) },
                onOpenSecurity = { navController.navigate(Routes.SecurityCenter.route) },
                // НОВОЕ (AC): открыть раздел «Жалобы» (только админы).
                onOpenReports = { navController.navigate(Routes.ReportInbox.route) },
                // НОВОЕ (обучение): повторный показ онбординга из настроек.
                onOpenOnboarding = { navController.navigate(Routes.OnboardingReplay.route) },
                // НОВОЕ (поиск по настройкам): прокрутка к пункту, если пришли из общего поиска.
                initialAnchorId = backStackEntry.arguments?.getString(Routes.Settings.ARG_ANCHOR),
                onLoggedOut = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
        // НОВОЕ (обучение): повторный показ онбординга по запросу из настроек. В отличие
        // от Routes.Onboarding, здесь просто возвращаемся назад — не трогаем
        // markOnboardingCompleted() (флаг уже true, раз пользователь дошёл до настроек)
        // и не чистим бэкстек, чтобы вернуться туда, откуда пришли.
        composable(Routes.OnboardingReplay.route) {
            OnboardingScreen(
                onFinish = { navController.popBackStack() },
                onSkip = { navController.popBackStack() }
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
        // НОВОЕ (п.15): настройки приватности «Кто может приглашать в группы / писать / смотреть профиль».
        composable(Routes.PrivacyWho.route) {
            app.yodo.messenger.features.settings.PrivacyWhoScreen(
                onBackClick = { navController.popBackStack() }
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
        // НОВОЕ (AD): оверлей блокировки — перекрывает весь интерфейс, когда аккаунт заблокирован.
        if (globalBlock != null) {
            GlobalBlockGateScreen(viewModel = globalBlockViewModel)
        }
    }
}