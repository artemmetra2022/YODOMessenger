package app.yodo.messenger.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.yodo.messenger.features.chats.ChatListScreen
import app.yodo.messenger.features.settings.SettingsScreen

private const val TAB_CHATS = 0
private const val TAB_SETTINGS = 1

@Composable
fun MainScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onOfflineChatClick: () -> Unit,
    onNearbyPeopleClick: () -> Unit,
    onLoggedOut: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(TAB_CHATS) }
    var showAddMenu by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == TAB_CHATS,
                    onClick = { selectedTab = TAB_CHATS },
                    icon = { Icon(Icons.Filled.ChatBubble, contentDescription = "Чаты") },
                    label = { Text("Чаты") }
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_SETTINGS,
                    onClick = { selectedTab = TAB_SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == TAB_CHATS) {
                Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Новый чат")
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Новый чат") },
                            leadingIcon = { Icon(Icons.Filled.PersonSearch, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                onSearchClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Новая группа") },
                            leadingIcon = { Icon(Icons.Filled.Group, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                onCreateGroupClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Офлайн-чат (Bluetooth)") },
                            leadingIcon = { Icon(Icons.Filled.BluetoothSearching, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                onOfflineChatClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Кто рядом") },
                            leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                onNearbyPeopleClick()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                TAB_CHATS -> ChatListScreen(
                    onChatClick = onChatClick,
                    onProfileClick = onProfileClick,
                    onSettingsClick = { selectedTab = TAB_SETTINGS },
                    onSearchClick = onSearchClick
                )
                TAB_SETTINGS -> SettingsScreen(
                    onBackClick = { selectedTab = TAB_CHATS },
                    onLoggedOut = onLoggedOut
                )
            }
        }
    }
}
