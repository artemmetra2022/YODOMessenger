package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoOnline
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Конфигурация табов фильтрации
// ---------------------------------------------------------------------------

private data class FilterTab(
    val filter: ChatFilter,
    val label: String
)

private val filterTabs = listOf(
    FilterTab(ChatFilter.ALL,     ""),
    FilterTab(ChatFilter.PRIVATE, ""),
    FilterTab(ChatFilter.GROUPS,  ""),
    FilterTab(ChatFilter.UNREAD,  "")
)

// ---------------------------------------------------------------------------
// ChatListScreen
// ---------------------------------------------------------------------------

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCreateGroupClick: () -> Unit = {},
    onCreateChannelClick: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val colorTheme = LocalColorTheme.current
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // Заголовок + иконки
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.chat_search_icon_cd))
                    }
                    IconButton(onClick = onProfileClick, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.chat_profile_cd))
                    }
                }

                // Горизонтальные табы фильтрации (как в Telegram)
                val allActive = (uiState as? ChatListUiState.Content)?.allActiveChats ?: emptyList()
                ChatFilterTabs(
                    tabs = filterTabs,
                    activeFilter = activeFilter,
                    allChats = allActive,
                    onTabSelected = { viewModel.setFilter(it) }
                )
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = colorTheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_create_cd), tint = Color.White)
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_list_new_chat)) },
                        onClick = { showFabMenu = false; onSearchClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_list_new_group)) },
                        onClick = { showFabMenu = false; onCreateGroupClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_list_new_channel)) },
                        onClick = { showFabMenu = false; onCreateChannelClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_list_contacts)) },
                        leadingIcon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
                        onClick = { showFabMenu = false; onOpenContacts() }
                    )
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
                        text = stringResource(R.string.chat_list_empty_all),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is ChatListUiState.Content -> {
                    if (state.chats.isEmpty()) {
                        // Пустой результат фильтрации
                        Text(
                            text = emptyFilterMessage(activeFilter),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // Строка архива
                            if (state.archivedChats.isNotEmpty()) {
                                item(key = "archive_row") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenArchive() }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Archive,
                                            contentDescription = null,
                                            tint = colorTheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = stringResource(R.string.chat_list_archive),
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = state.archivedChats.size.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(state.chats, key = { it.chatId }) { chat ->
                                SwipeableChatListItem(
                                    chat = chat,
                                    colorTheme = colorTheme,
                                    onClick = { onChatClick(chat.chatId) },
                                    onTogglePin = { viewModel.togglePinChat(chat.chatId) },
                                    onToggleMute = { viewModel.toggleMuteChat(chat.chatId) },
                                    onDelete = { viewModel.deleteChat(chat.chatId) },
                                    onClearHistory = { viewModel.clearChatHistory(chat.chatId) },
                                    onToggleArchive = { viewModel.toggleArchiveChat(chat.chatId) }
                                )
                            }
                        }
                    }
                }
                is ChatListUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.chat_list_load_error), style = MaterialTheme.typography.titleLarge)
                        Text(state.message, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Горизонтальные табы фильтрации
// ---------------------------------------------------------------------------

@Composable
private fun ChatFilterTabs(
    tabs: List<FilterTab>,
    activeFilter: ChatFilter,
    allChats: List<ChatPreview>,
    onTabSelected: (ChatFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs) { tab ->
            val isActive = tab.filter == activeFilter
            // Считаем бейдж для "Непрочитанные"
            val badge = if (tab.filter == ChatFilter.UNREAD) {
                allChats.count { it.unreadCount > 0 }
            } else 0

            FilterChip(
                label = tab.label,
                isActive = isActive,
                badge = badge,
                onClick = { onTabSelected(tab.filter) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isActive: Boolean,
    badge: Int,
    onClick: () -> Unit
) {
    val colorTheme = LocalColorTheme.current
    val bgColor = if (isActive) colorTheme.primary else colorTheme.primary.copy(alpha = 0.12f)
    val textColor = if (isActive) Color.White else colorTheme.primary

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color.White.copy(alpha = 0.3f) else colorTheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badge > 99) "99+" else badge.toString(),
                        color = if (isActive) Color.White else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun emptyFilterMessage(filter: ChatFilter): String = when (filter) {
    ChatFilter.ALL     -> stringResource(R.string.chat_list_empty_all)
    ChatFilter.PRIVATE -> stringResource(R.string.chat_list_empty_private)
    ChatFilter.GROUPS  -> stringResource(R.string.chat_list_empty_groups)
    ChatFilter.UNREAD  -> stringResource(R.string.chat_list_empty_unread)
}

// ---------------------------------------------------------------------------
// SwipeableChatListItem
// ---------------------------------------------------------------------------

@Composable
internal fun SwipeableChatListItem(
    chat: ChatPreview,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onDelete: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleArchive: () -> Unit = {}
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
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.chat_list_delete_cd), tint = Color.Red,
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
            DropdownMenuItem(text = { Text(if (chat.isPinned) stringResource(R.string.chat_list_unpin) else stringResource(R.string.chat_list_pin)) }, onClick = { showMenu = false; onTogglePin() })
            DropdownMenuItem(text = { Text(if (chat.isMuted) stringResource(R.string.chat_list_unmute) else stringResource(R.string.chat_list_mute)) }, onClick = { showMenu = false; onToggleMute() })
            DropdownMenuItem(text = { Text(if (chat.isArchived) stringResource(R.string.chat_list_unarchive) else stringResource(R.string.chat_list_archive_action)) }, onClick = { showMenu = false; onToggleArchive() })
            DropdownMenuItem(text = { Text(stringResource(R.string.chat_list_clear)) }, onClick = { showMenu = false; onClearHistory() })
            DropdownMenuItem(text = { Text(stringResource(R.string.chat_list_delete), color = Color.Red) }, onClick = { showMenu = false; onDelete() })
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

// ---------------------------------------------------------------------------
// ChatListItem
// ---------------------------------------------------------------------------

@Composable
private fun ChatListItem(
    chat: ChatPreview,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isSavedChat = chat.type == ChatType.PRIVATE && chat.otherUserId == null
    val isChannel = chat.type == ChatType.CHANNEL

    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        if (isSavedChat) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape)
                    .background(colorTheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Избранное",
                    tint = colorTheme.primary, modifier = Modifier.size(28.dp))
            }
        } else if (isChannel) {
            if (chat.avatarBase64 != null) {
                UserAvatar(
                    displayName = chat.title,
                    photoUrl = null,
                    avatarBase64 = chat.avatarBase64,
                    size = 56.dp,
                    userId = chat.chatId
                )
            } else {
                // Цветная аватарка с инициалом канала
                UserAvatar(
                    displayName = if (chat.isVerified) "Y" else chat.title,
                    photoUrl = null,
                    avatarBase64 = null,
                    size = 56.dp,
                    userId = chat.chatId
                )
            }
        } else {
            Box {
                UserAvatar(
                    displayName = chat.title,
                    photoUrl = chat.avatarUrl,
                    avatarBase64 = chat.avatarBase64,
                    size = 56.dp,
                    // Передаём otherUserId для стабильного цвета по пользователю
                    userId = chat.otherUserId ?: chat.chatId
                )
                if (chat.isOnline) {
                    Box(
                        modifier = Modifier.size(14.dp).align(Alignment.BottomEnd)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.background)
                            .padding(2.dp).clip(CircleShape).background(YodoOnline)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Верхняя строка
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chat.isMuted) {
                        Icon(Icons.Filled.NotificationsOff, contentDescription = null,
                            modifier = Modifier.size(14.dp).padding(end = 3.dp), tint = Color.Gray)
                    }
                    if (chat.isPinned) {
                        Icon(Icons.Filled.PushPin, contentDescription = null,
                            modifier = Modifier.size(14.dp).padding(end = 3.dp), tint = Color.Gray)
                    }
                    Text(
                        chat.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (chat.isVerified) {
                        Box(
                            modifier = Modifier.size(18.dp).padding(start = 4.dp)
                                .clip(CircleShape).background(Color(0xFF22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Verified, contentDescription = "Верифицирован",
                                tint = Color(0xFF1D9BF0), modifier = Modifier.size(12.dp))
                        }
                    }
                    if (!chat.username.isNullOrBlank()) {
                        Text(
                            "  @${chat.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                if (!isChannel) {
                    PresenceStatusText(chat, modifier = Modifier.padding(start = 6.dp))
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            formatTimestamp(chat.lastMessageTimestamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                        if (chat.lastMessageSenderId != null && chat.lastMessageSenderId == currentUid) {
                            val isRead = chat.lastMessageStatus == "READ"
                            Icon(
                                imageVector = if (isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                                contentDescription = if (isRead) "Прочитано" else "Доставлено",
                                modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                tint = if (isRead) Color(0xFF60E6FF) else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Нижняя строка
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.draftText.isNotBlank()) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Medium)) {
                                append(stringResource(R.string.chat_list_draft_prefix))
                            }
                            append(chat.draftText)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        chat.lastMessage.ifBlank { if (isChannel) stringResource(R.string.chat_list_channel_label) else "" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!isChannel) {
                    if (chat.unreadCount > 0) {
                        Box(
                            modifier = Modifier.padding(start = 6.dp)
                                .clip(CircleShape).background(colorTheme.primary)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                color = Color.White, style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Вспомогательные composable
// ---------------------------------------------------------------------------

@Composable
private fun PresenceStatusText(chat: ChatPreview, modifier: Modifier = Modifier) {
    when {
        chat.isOnline -> Text(
            "в сети",
            style = MaterialTheme.typography.labelMedium,
            color = YodoOnline,
            maxLines = 1,
            modifier = modifier
        )
        chat.lastSeenMillis > 0 -> Text(
            stringResource(R.string.chat_list_was_online, formatLastSeen(chat.lastSeenMillis)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            maxLines = 1,
            modifier = modifier
        )
    }
}

@Composable
private fun formatLastSeen(millis: Long): String {
    val diffMillis = (System.currentTimeMillis() - millis).coerceAtLeast(0L)
    val diffSeconds = diffMillis / 1_000
    val diffMinutes = diffSeconds / 60
    return when {
        diffSeconds < 10 -> stringResource(R.string.chat_list_just_now)
        diffSeconds < 60 -> "$diffSeconds сек назад"
        diffMinutes < 60 -> "$diffMinutes мин назад"
        diffMinutes < 24 * 60 -> "${diffMinutes / 60} ч назад"
        diffMinutes < 7 * 24 * 60 -> SimpleDateFormat("EEEE, HH:mm", Locale("ru")).format(Date(millis))
            .replaceFirstChar { it.uppercase() }
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))
    }
}

@Composable
private fun formatTimestamp(millis: Long): String {
    if (millis == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - millis
    val diffDays = diff / (24 * 60 * 60 * 1000)
    return when {
        diffDays == 0L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        diffDays == 1L -> stringResource(R.string.chat_list_yesterday)
        diffDays < 7L -> "$diffDays дн назад"
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(millis))
    }
}
