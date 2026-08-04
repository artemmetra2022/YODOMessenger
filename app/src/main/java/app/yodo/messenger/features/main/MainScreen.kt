package app.yodo.messenger.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.yodo.messenger.R
import app.yodo.messenger.features.chats.ChatListScreen

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
    onOpenArchive: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            ChatListScreen(
                onChatClick = onChatClick,
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick,
                onCreateGroupClick = onCreateGroupClick,
                onCreateChannelClick = onCreateChannelClick,
                onOpenContacts = onOpenContacts,
                onOpenArchive = onOpenArchive
            )
        }
    }

    NotificationPermissionPrompt()
}