package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.ui.theme.YodoAccent
import app.yodo.messenger.ui.theme.YodoOnline
import app.yodo.messenger.ui.theme.YodoPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yodo Messenger",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Поиск")
                }
                IconButton(onClick = onProfileClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = "Профиль")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is ChatListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is ChatListUiState.Empty -> {
                    Text(
                        text = "У вас пока нет чатов.\nНачните новый разговор!",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                is ChatListUiState.Content -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.chats, key = { it.chatId }) { chat ->
                            ChatListItem(
                                chat = chat,
                                onClick = { onChatClick(chat.chatId) },
                                onTogglePin = { viewModel.togglePinChat(chat.chatId) },
                                onToggleMute = { viewModel.toggleMuteChat(chat.chatId) }
                            )
                        }
                    }
                }

                is ChatListUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Не удалось загрузить чаты",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(
    chat: ChatPreview,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
    androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(if (chat.isPinned) "Открепить" else "Закрепить") },
            onClick = { showMenu = false; onTogglePin() }
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(if (chat.isMuted) "Включить уведомления" else "Отключить уведомления") },
            onClick = { showMenu = false; onToggleMute() }
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickableCompat(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (chat.isOnline) {
                            Brush.linearGradient(listOf(YodoPrimary, YodoAccent))
                        } else {
                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(YodoPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chat.title.take(1).uppercase(),
                        color = YodoPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(YodoOnline)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isMuted) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = "Без звука",
                        modifier = Modifier.size(14.dp).padding(end = 4.dp),
                        tint = Color.Gray
                    )
                }
                if (chat.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Закреплён",
                        modifier = Modifier.size(14.dp).padding(end = 4.dp),
                        tint = Color.Gray
                    )
                }
                Text(
                    text = formatTimestamp(chat.lastMessageTimestamp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(YodoPrimary)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.then(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )

private fun formatTimestamp(millis: Long): String {
    if (millis == 0L) return ""
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}
