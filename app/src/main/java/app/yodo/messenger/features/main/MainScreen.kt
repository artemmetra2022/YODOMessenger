package app.yodo.messenger.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.features.chats.AdminHomeViewModel
import app.yodo.messenger.features.chats.ChatListScreen
import app.yodo.messenger.data.local.InterfaceStyle
import app.yodo.messenger.ui.theme.LocalInterfaceStyle
import app.yodo.messenger.ui.components.glassTint
import app.yodo.messenger.ui.components.liquidGlass

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
    onOpenAdminPanel: () -> Unit = {},
    // НОВОЕ (каталог/рекомендации каналов): открытие витрины каналов.
    onDiscoverChannels: () -> Unit = {},
    // НОВОЕ (админ-функции групп): переход к экрану группы по тапу на бейдж заявок.
    onOpenGroupInfo: (String) -> Unit = {},
    // НОВОЕ (единая вкладка «Админка»): переход к сводному экрану админ-функций.
    // Сама вкладка видна только двум доверенным аккаунтам (isAppAdmin).
    onOpenAdminHome: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    // НОВОЕ (единая вкладка «Админка»): используем тот же ViewModel, что и сам
    // экран Админки, только чтобы узнать isAppAdmin — без лишнего дублирования
    // проверки ADMIN_EMAILS.
    val isAppAdmin = hiltViewModel<AdminHomeViewModel>().isAppAdmin
    val experimentalInterface = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            NavigationBar(
                modifier = Modifier
                    .then(if (experimentalInterface) Modifier.padding(horizontal = 10.dp, vertical = 6.dp) else Modifier)
                    .liquidGlass(
                        enabled = experimentalInterface,
                        shape = RoundedCornerShape(28.dp),
                        tint = glassTint(isDark),
                        dark = isDark,
                        elevation = 12
                    ),
                containerColor = if (experimentalInterface) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = if (experimentalInterface) 0.dp else 3.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = stringResource(R.string.nav_chats_cd)) },
                    label = { Text(stringResource(R.string.nav_chats)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onNearbyClick() },
                    icon = { Icon(Icons.Filled.NearMe, contentDescription = stringResource(R.string.nav_nearby_cd)) },
                    label = { Text(stringResource(R.string.nav_nearby)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onOfflineClick() },
                    icon = { Icon(Icons.Filled.WifiOff, contentDescription = stringResource(R.string.nav_offline_cd)) },
                    label = { Text(stringResource(R.string.nav_offline)) }
                )
                // ИСПРАВЛЕНО: "Настройки" → stringResource(R.string.settings_title)
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { onSettingsClick() },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title)) },
                    label = { Text(stringResource(R.string.settings_title)) }
                )
                // НОВОЕ (единая вкладка «Админка»): видна только двум доверенным
                // аккаунтам — у остальных пользователей нижняя навигация из 4
                // пунктов, без изменений.
                if (isAppAdmin) {
                    NavigationBarItem(
                        selected = false,
                        onClick = { onOpenAdminHome() },
                        icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Админка") },
                        label = { Text("Админка") }
                    )
                }
            }
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
                onOpenAdminPanel = onOpenAdminPanel,
                onDiscoverChannels = onDiscoverChannels,
                onOpenGroupInfo = onOpenGroupInfo
            )
        }
    }

    NotificationPermissionPrompt()
}