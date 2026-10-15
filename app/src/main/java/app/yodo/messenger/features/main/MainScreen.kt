package app.yodo.messenger.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.yodo.messenger.features.chats.ChatListScreen

/**
 * Экран списка чатов. Нижняя панель навигации рисуется здесь же, в собственном
 * Scaffold — как и раньше, но теперь через общий компонент [YodoBottomBar],
 * который используется ещё и на стартовом экране настроек, и на Админке.
 *
 * Панель намеренно есть только на этих трёх «корневых» экранах: в подэкранах
 * (категории настроек, «Школа», «Инструменты», чат) она не нужна и не показывается.
 *
 * @param showAdminTab показывать ли вкладку «Админка» — только для двух доверенных
 * аккаунтов, признак приходит сверху (см. YodoNavGraph), а не вычисляется здесь.
 */
@Composable
fun MainScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onOfflineClick: () -> Unit,
    onCreateGroupClick: () -> Unit = {},
    onCreateChannelClick: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onWeatherClick: () -> Unit = {},
    onOpenAdminPanel: () -> Unit = {},
    // НОВОЕ (каталог/рекомендации каналов): открытие витрины каналов.
    onDiscoverChannels: () -> Unit = {},
    // НОВОЕ (админ-функции групп): переход к экрану группы по тапу на бейдж заявок.
    onOpenGroupInfo: (String) -> Unit = {},
    // НОВОЕ (единая вкладка «Админка»): переход к сводному экрану админ-функций.
    onOpenAdminHome: () -> Unit = {},
    showAdminTab: Boolean = false
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            YodoBottomBar(
                selectedTab = TAB_CHATS,
                showAdminTab = showAdminTab,
                // Уже на списке чатов — повторный тап ничего не делает.
                onChatsClick = {},
                onNearbyClick = onNearbyClick,
                onOfflineClick = onOfflineClick,
                onSettingsClick = onSettingsClick,
                onAdminClick = onOpenAdminHome
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            ChatListScreen(
                onChatClick = onChatClick,
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick,
                onCreateGroupClick = onCreateGroupClick,
                onCreateChannelClick = onCreateChannelClick,
                onOpenContacts = onOpenContacts,
                onOpenArchive = onOpenArchive,
                onWeatherClick = onWeatherClick,
                onOpenAdminPanel = onOpenAdminPanel,
                onDiscoverChannels = onDiscoverChannels,
                onOpenGroupInfo = onOpenGroupInfo
            )
        }
    }

    NotificationPermissionPrompt()
}
