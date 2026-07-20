package app.yodo.messenger.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.yodo.messenger.features.auth.LoginScreen
import app.yodo.messenger.features.auth.PhoneLoginScreen
import app.yodo.messenger.features.auth.RegisterScreen
import app.yodo.messenger.features.auth.WelcomeScreen
import app.yodo.messenger.features.chats.ChatScreen
import app.yodo.messenger.features.chats.CreateGroupScreen
import app.yodo.messenger.features.chats.ForwardMessageScreen
import app.yodo.messenger.features.chats.GroupInfoScreen
import app.yodo.messenger.features.main.MainScreen
import app.yodo.messenger.features.profile.ProfileScreen
import app.yodo.messenger.features.profile.UserProfileScreen
import app.yodo.messenger.features.search.SearchScreen
import app.yodo.messenger.features.nearby.NearbyPeopleScreen
import app.yodo.messenger.offline.OfflineChatScreen

@Composable
fun YodoNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.Welcome.route
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.Welcome.route) {
            WelcomeScreen(
                onStartClick = { navController.navigate(Routes.Login.route) },
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

        composable(Routes.PhoneLogin.route) {
            PhoneLoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToEmailLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.ChatList.route) {
            MainScreen(
                onChatClick = { chatId -> navController.navigate(Routes.Chat.createRoute(chatId)) },
                onProfileClick = { navController.navigate(Routes.Profile.route) },
                onSearchClick = { navController.navigate(Routes.Search.route) },
                onCreateGroupClick = { navController.navigate(Routes.CreateGroup.route) },
                onOfflineChatClick = { navController.navigate(Routes.OfflineChat.route) },
                onNearbyPeopleClick = { navController.navigate(Routes.NearbyPeople.route) },
                onLoggedOut = {
                    navController.navigate(Routes.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CreateGroup.route) {
            CreateGroupScreen(
                onBackClick = { navController.popBackStack() },
                onGroupCreated = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.ChatList.route)
                    }
                }
            )
        }

        composable(Routes.OfflineChat.route) {
            OfflineChatScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Routes.NearbyPeople.route) {
            NearbyPeopleScreen(
                onBackClick = { navController.popBackStack() },
                onPersonClick = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                }
            )
        }

        composable(Routes.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.ChatList.route)
                    }
                },
                onViewProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                }
            )
        }

        composable(Routes.Chat.route) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString(Routes.Chat.ARG_CHAT_ID).orEmpty()
            ChatScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() },
                onOpenUserProfile = { userId ->
                    navController.navigate(Routes.UserProfile.createRoute(userId))
                },
                onOpenGroupInfo = { groupChatId ->
                    navController.navigate(Routes.GroupInfo.createRoute(groupChatId))
                },
                onForwardMessage = {
                    navController.navigate(Routes.ForwardMessage.route)
                }
            )
        }

        composable(Routes.ForwardMessage.route) {
            ForwardMessageScreen(
                onBackClick = { navController.popBackStack() },
                onForwarded = { targetChatId ->
                    navController.navigate(Routes.Chat.createRoute(targetChatId)) {
                        popUpTo(Routes.ChatList.route)
                    }
                }
            )
        }

        composable(Routes.GroupInfo.route) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString(Routes.GroupInfo.ARG_CHAT_ID).orEmpty()
            GroupInfoScreen(
                onBackClick = { navController.popBackStack() },
                onLeftGroup = {
                    navController.navigate(Routes.ChatList.route) {
                        popUpTo(Routes.ChatList.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.UserProfile.route) {
            UserProfileScreen(
                onBackClick = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.Chat.createRoute(chatId)) {
                        popUpTo(Routes.ChatList.route)
                    }
                }
            )
        }

        composable(Routes.Profile.route) {
            ProfileScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
