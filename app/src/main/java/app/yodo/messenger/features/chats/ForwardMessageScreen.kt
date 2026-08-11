package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
* п.36: экран пересылки полностью переработан.
* Что добавлено сверх старой версии:
*  - шапка показывает ПРЕВЬЮ пересылаемого сообщения (текст/картинку и автора),
*    чтобы было видно, что именно сейчас отправляется;
*  - строка поиска по названию/юзернейму чата;
*  - список сгруппирован по секциям (Закреплённые / Канал / Группы / Личные чаты)
*    с заголовками секций вместо плоского списка;
*  - карточка чата стала информативнее: аватар, галочка верификации, тип чата,
*    статус "в сети"/"был(а)", число участников группы, признак "без звука";
*  - индикатор загрузки на время самой пересылки и снэкбар с ошибкой при сбое.
*/
@Composable
fun ForwardMessageScreen(
    onBackClick: () -> Unit,
    onForwarded: (String) -> Unit,
    viewModel: ForwardMessageViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val forwardedToChatId by viewModel.forwardedToChatId.collectAsState()
    val isForwarding by viewModel.isForwarding.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val colorTheme = LocalColorTheme.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(forwardedToChatId) {
        forwardedToChatId?.let { onForwarded(it) }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val sections = groupChatsForForward(chats)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.forward_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.forward_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick, enabled = !isForwarding) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                        }
                    }
                )
                viewModel.messageToForward?.let { message ->
                    ForwardPreviewCard(message = message, colorTheme = colorTheme)
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.forward_search_placeholder)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.forward_clear_cd))
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (chats.isEmpty()) {
                Text(
                    stringResource(R.string.forward_no_chats),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (sections.all { it.chats.isEmpty() }) {
                Text(
                    stringResource(R.string.forward_no_results),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sections.forEach { section ->
                        if (section.chats.isNotEmpty()) {
                            item(key = "header_${section.title}") {
                                Text(
                                    section.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorTheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(section.chats, key = { it.chatId }) { chat ->
                                ForwardChatItem(
                                    chat = chat,
                                    colorTheme = colorTheme,
                                    enabled = !isForwarding,
                                    onClick = { viewModel.forwardTo(chat.chatId) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
            if (isForwarding) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorTheme.primary)
                }
            }
        }
    }
}

/** Компактная карточка "что пересылаем" в шапке экрана. */
@Composable
private fun ForwardPreviewCard(message: Message, colorTheme: ColorTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(3.dp).height(36.dp)
                .background(colorTheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        if (message.text.isBlank() && message.imageBase64 != null) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.forward_forwarded_msg),
                style = MaterialTheme.typography.labelSmall,
                color = colorTheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                message.previewText(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class ForwardSection(val title: String, val chats: List<ChatPreview>)

/** п.36: делит список чатов на информативные секции вместо одного плоского списка. */
@androidx.compose.runtime.Composable
private fun groupChatsForForward(chats: List<ChatPreview>): List<ForwardSection> {
    val pinned = chats.filter { it.isPinned }
    val remaining = chats.filterNot { it.isPinned }
    val channels = remaining.filter { it.type == ChatType.CHANNEL }
    val groups = remaining.filter { it.type == ChatType.GROUP }
    val personal = remaining.filter { it.type == ChatType.PRIVATE }
    return listOf(
        ForwardSection(stringResource(R.string.forward_section_pinned), pinned),
        ForwardSection(stringResource(R.string.forward_section_channels), channels),
        ForwardSection(stringResource(R.string.forward_section_groups), groups),
        ForwardSection(stringResource(R.string.forward_section_personal), personal)
    )
}

@Composable
private fun ForwardChatItem(
    chat: ChatPreview,
    colorTheme: ColorTheme,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isSavedChat = chat.type == ChatType.PRIVATE && chat.otherUserId == null
    val isChannel = chat.type == ChatType.CHANNEL
    val isGroup = chat.type == ChatType.GROUP

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        Box {
            when {
                isSavedChat -> Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(colorTheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null,
                        tint = colorTheme.primary, modifier = Modifier.size(26.dp))
                }
                isChannel -> {
                    // НОВОЕ (переработка каналов): реальная аватарка канала, если она
                    // загружена (поле avatarBase64 в документе чата); иначе логотип "Y".
                    if (chat.avatarBase64 != null) {
                        UserAvatar(
                            displayName = chat.title,
                            photoUrl = null,
                            avatarBase64 = chat.avatarBase64,
                            size = 52.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                .background(colorTheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Y", style = MaterialTheme.typography.titleLarge,
                                color = colorTheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                isGroup -> Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(colorTheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null,
                        tint = colorTheme.primary, modifier = Modifier.size(24.dp))
                }
                else -> UserAvatar(
                    displayName = chat.title,
                    photoUrl = chat.avatarUrl,
                    avatarBase64 = chat.avatarBase64,
                    size = 52.dp
                )
            }
            if (!isChannel && !isGroup && !isSavedChat && chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isPinned) {
                    Icon(Icons.Filled.PushPin, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp))
                }
                if (isChannel) {
                    Icon(Icons.Filled.Campaign, contentDescription = null,
                        tint = colorTheme.primary,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp))
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
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                            .clip(CircleShape).background(Color(0xFF22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null,
                            tint = Color(0xFF1D9BF0),
                            modifier = Modifier.size(10.dp))
                    }
                }
                if (chat.isMuted) {
                    Icon(Icons.Filled.NotificationsOff, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp).padding(start = 4.dp))
                }
            }
            // Дополнительная информативная строка
            val subtitle = when {
                isChannel -> stringResource(R.string.forward_official_channel)
                isSavedChat -> stringResource(R.string.forward_saved_messages)
                isGroup -> stringResource(R.string.forward_group_chat)
                chat.isOnline -> stringResource(R.string.forward_online)
                chat.lastSeenMillis > 0 -> stringResource(R.string.forward_was_online, formatForwardLastSeen(chat.lastSeenMillis))
                !chat.username.isNullOrBlank() -> "@${chat.username}"
                chat.lastMessage.isNotBlank() -> chat.lastMessage
                else -> stringResource(R.string.forward_chat_default)
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Время последнего сообщения
        if (chat.lastMessageTimestamp > 0 && !isChannel) {
            Text(
                formatForwardTimestamp(chat.lastMessageTimestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun formatForwardTimestamp(millis: Long): String {
    if (millis == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - millis
    val diffDays = diff / (24 * 60 * 60 * 1000)
    return when {
        diffDays == 0L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        diffDays == 1L -> stringResource(R.string.forward_yesterday)
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(millis))
    }
}

@androidx.compose.runtime.Composable
private fun formatForwardLastSeen(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)
    return when {
        minutes < 1 -> stringResource(R.string.forward_just_now)
        minutes < 60 -> "$minutes мин. назад"
        hours < 24 -> "$hours ч. назад"
        days == 1L -> stringResource(R.string.forward_yesterday)
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(millis))
    }
}