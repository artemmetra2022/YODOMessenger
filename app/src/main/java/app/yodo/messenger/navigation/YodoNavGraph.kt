package app.yodo.messenger.navigation

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
import app.yodo.messenger.features.chats.CreateGroupScreen
import app.yodo.messenger.features.chats.ForwardMessageScreen
import app.yodo.messenger.features.chats.GroupInfoScreen
import app.yodo.messenger.features.chats.ImageViewerHolder
import app.yodo.messenger.features.chats.ImageViewerScreen
import app.yodo.messenger.features.main.MainScreen
import app.yodo.messenger.features.nearby.NearbyPeopleScreen
import app.yodo.messenger.features.profile.UserProfileScreen
import app.yodo.messenger.features.search.SearchScreen
import app.yodo.messenger.features.settings.SettingsScreen
import app.yodo.messenger.offline.OfflineChatScreen
// Добавьте импорт для ProfileScreen, если он есть
import app.yodo.messenger.features.profile.ProfileScreen 

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
        // Welcome Screen
        composable(Routes.Welcome.route) {
            WelcomeScreen(
                onStartClick = { navController.navigate(Routes.Login.route) },
                onRegisterClick = { navController.navigate(Routes.Register.route) }
            )
        }

        // Login Screen
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

        // Register Screen
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

        // Phone Login Screen
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

        // Main Screen (В вашем случае это ChatList)
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
                }
            )
        }

        // Profile Screen (Свой профиль)
        composable(Routes.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Chat Screen
                // Chat Screen
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
                onForwardMessage = { 
                    // Исправлено: функция теперь без параметров, как и ожидает ChatScreen
                    navController.navigate(Routes.ForwardMessage.route)
                },
                onOpenImageViewer = { imageBase64, senderName, timestamp ->
                    ImageViewerHolder.imageBase64 = imageBase64
                    ImageViewerHolder.senderName = senderName
                    ImageViewerHolder.timestamp = timestamp
                    navController.navigate(Routes.ImageViewer.route)
                }
            )
        }

        // Image Viewer Screen (маршрут раньше не был зарегистрирован — переход на него
        // всегда падал с IllegalArgumentException "destination cannot be found")
        composable(Routes.ImageViewer.route) {
            ImageViewerScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        

        // Group Info Screen
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
                }
            )
        }

        // Create Group Screen
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

        // Forward Message Screen
        composable(Routes.ForwardMessage.route) {
            ForwardMessageScreen(
                onBackClick = { navController.popBackStack() },
                onForwarded = {
                    navController.popBackStack()
                }
            )
        }

        // User Profile Screen (Чужой профиль)
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

        // Search Screen
        composable(Routes.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId))
                },
                onViewProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                }
            )
        }

        // Settings Screen
        composable(Routes.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Routes.Profile.route) },
                onLoggedOut = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }

        // Offline Chat Screen
        composable(Routes.OfflineChat.route) {
            OfflineChatScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Nearby People Screen
        composable(Routes.NearbyPeople.route) {
            NearbyPeopleScreen(
                onBackClick = { navController.popBackStack() },
                onPersonClick = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                }
            )
        }
    }
}
