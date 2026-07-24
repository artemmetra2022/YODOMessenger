package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoOnline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
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
        // Это главный экран (низ навигации) — свайп-назад тут намеренно не подключаем,
        // возвращаться отсюда некуда. Свайп-назад подключается на дочерних экранах
        // (чат, профиль, поиск и т.д.), куда переходят ИЗ этого списка.
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is ChatListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ChatListUiState.Empty -> {
                    Text(
                        text = "У вас пока нет чатов.\nНачните новый разговор!",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is ChatListUiState.Content -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.chats, key = { it.chatId }) { chat ->
                            SwipeableChatListItem(
                                chat = chat,
                                colorTheme = colorTheme,
                                onClick = { onChatClick(chat.chatId) },
                                onTogglePin = { viewModel.togglePinChat(chat.chatId) },
                                onToggleMute = { viewModel.toggleMuteChat(chat.chatId) },
                                onDelete = { viewModel.deleteChat(chat.chatId) },
                                onClearHistory = { viewModel.clearChatHistory(chat.chatId) }
                            )
                        }
                    }
                }
                is ChatListUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Не удалось загрузить чаты", style = MaterialTheme.typography.titleLarge)
                        Text(state.message, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableChatListItem(
    chat: ChatPreview,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onDelete: () -> Unit,
    onClearHistory: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showMenu by remember { mutableStateOf(false) }
    val deleteThreshold = -100f

    Box {
        if (offsetX < -20f) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = Color.Red,
                    modifier = Modifier.padding(end = 24.dp).size(28.dp))
            }
        }
        Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
            ChatListItem(
                chat = chat, colorTheme = colorTheme,
                onClick = onClick, onLongClick = { showMenu = true }
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text(if (chat.isPinned) "Открепить" else "Закрепить") }, onClick = { showMenu = false; onTogglePin() })
            DropdownMenuItem(text = { Text(if (chat.isMuted) "Включить уведомления" else "Отключить уведомления") }, onClick = { showMenu = false; onToggleMute() })
            DropdownMenuItem(text = { Text("Очистить историю") }, onClick = { showMenu = false; onClearHistory() })
            DropdownMenuItem(text = { Text("Удалить чат", color = Color.Red) }, onClick = { showMenu = false; onDelete() })
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().pointerInput(chat.chatId) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (offsetX < deleteThreshold) onDelete()
                    offsetX = 0f
                },
                onDragCancel = { offsetX = 0f },
                onHorizontalDrag = { _, dragAmount ->
                    offsetX = (offsetX + dragAmount).coerceIn(-200f, 0f)
                }
            )
        }
    ) {}
}

@Composable
private fun ChatListItem(
    chat: ChatPreview,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            UserAvatar(
                displayName = chat.title,
                photoUrl = chat.avatarUrl,
                // ИСПРАВЛЕНИЕ (баг "аватарки не видны у других"): раньше здесь было
                // жёстко null. Теперь берём реальный base64-аватар собеседника,
                // который ChatRepositoryImpl.observeChatList() догружает из users/{uid}.
                avatarBase64 = chat.avatarBase64,
                size = 56.dp
            )
            if (chat.isOnline) {
                Box(
                    modifier = Modifier.size(14.dp).align(Alignment.BottomEnd)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.background)
                        .padding(2.dp).clip(CircleShape).background(YodoOnline)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!chat.username.isNullOrBlank()) {
                    Text("  @${chat.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                }
            }
            Text(chat.lastMessage, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isMuted) {
                    Icon(Icons.Filled.NotificationsOff, contentDescription = "Без звука", modifier = Modifier.size(14.dp).padding(end = 4.dp), tint = Color.Gray)
                }
                if (chat.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Закреплён", modifier = Modifier.size(14.dp).padding(end = 4.dp), tint = Color.Gray)
                }
                Text(formatTimestamp(chat.lastMessageTimestamp), style = MaterialTheme.typography.labelMedium)
            }
            if (chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.clip(CircleShape).background(colorTheme.primary).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(
                        text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                        color = Color.White, style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - millis
    val diffDays = diff / (24 * 60 * 60 * 1000)
    return when {
        diffDays == 0L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        diffDays == 1L -> "Вчера"
        diffDays < 7L -> "$diffDays дн назад"
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(millis))
    }
}
