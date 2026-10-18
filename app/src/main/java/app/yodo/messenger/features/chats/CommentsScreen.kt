package app.yodo.messenger.features.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.Comment
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.components.clickable
import app.yodo.messenger.ui.components.combinedClickable
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val GROUP_GAP_MILLIS = 5 * 60 * 1000L

/** Палитра для имён авторов: цвет стабилен для одного и того же uid. */
private val AuthorPalette = listOf(
    Color(0xFFE5655A), Color(0xFFE08A3C), Color(0xFF3DA35D), Color(0xFF2FA4B8),
    Color(0xFF4C8DDB), Color(0xFF8E6CD8), Color(0xFFD0599A), Color(0xFF7A9A2E)
)

/**
 * Экран комментариев к посту канала.
 * Сверху — карточка поста (разворачивается), ниже — лента комментариев в виде пузырей
 * с аватарками, группировкой по автору и разделителями по дням, внизу — поле ввода.
 * Долгий тап по комментарию — меню: копировать / удалить (свой или админ).
 */
@Composable
fun CommentsScreen(
    onBackClick: () -> Unit,
    viewModel: CommentsViewModel = hiltViewModel()
) {
    val message by viewModel.message.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val avatars by viewModel.senderAvatars.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val colorTheme = LocalColorTheme.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var inputText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Comment?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Индексы: 0 — пост, 1..n — комментарии, n+1 — нижний отступ.
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.size + 1)
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Удалить комментарий?") },
            text = {
                Text(
                    target.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteComment(target.id)
                    deleteTarget = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Комментарии", style = MaterialTheme.typography.titleLarge)
                        if (message != null) {
                            Text(
                                if (comments.isEmpty()) "Пока без комментариев"
                                else "${comments.size} ${pluralComments(comments.size)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            CommentInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                colorTheme = colorTheme,
                onSend = {
                    viewModel.sendComment(inputText.trim())
                    inputText = ""
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val bubbleMaxWidth = maxWidth * 0.78f
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "post") {
                    message?.let { post ->
                        PostCard(
                            text = post.previewText(),
                            time = formatCommentDateTime(post.timestamp),
                            colorTheme = colorTheme
                        )
                    }
                }

                if (comments.isEmpty()) {
                    item(key = "empty") { EmptyComments(colorTheme) }
                }

                itemsIndexed(comments, key = { _, c -> c.id }) { index, comment ->
                    val prev = comments.getOrNull(index - 1)
                    val next = comments.getOrNull(index + 1)
                    val newDay = prev == null || !sameDay(prev.timestamp, comment.timestamp)
                    val groupStart = newDay || prev!!.senderId != comment.senderId ||
                        comment.timestamp - prev.timestamp > GROUP_GAP_MILLIS
                    val groupEnd = next == null || !sameDay(comment.timestamp, next.timestamp) ||
                        next.senderId != comment.senderId ||
                        next.timestamp - comment.timestamp > GROUP_GAP_MILLIS
                    val (photoUrl, avatarBase64) = avatars[comment.senderId] ?: (null to null)
                    val isOwn = comment.senderId == viewModel.currentUserId
                    // Модерация: свой комментарий или глобальный админ — можно удалить любой.
                    val canDelete = isOwn || viewModel.isGlobalAdmin

                    Column(modifier = Modifier.animateItemPlacement()) {
                        if (newDay) DayChip(formatDayLabel(comment.timestamp))
                        CommentRow(
                            comment = comment,
                            isOwn = isOwn,
                            groupStart = groupStart,
                            groupEnd = groupEnd,
                            photoUrl = photoUrl,
                            avatarBase64 = avatarBase64,
                            canDelete = canDelete,
                            isDark = isDark,
                            colorTheme = colorTheme,
                            maxBubbleWidth = bubbleMaxWidth,
                            onDelete = { deleteTarget = comment }
                        )
                    }
                }
                item(key = "bottom_space") { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun PostCard(text: String, time: String, colorTheme: ColorTheme) {
    var expanded by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(colorTheme.primary)
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Campaign, contentDescription = null,
                        tint = colorTheme.primary, modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Пост канала",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorTheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow }
                )
                if (overflow || expanded) {
                    Text(
                        if (expanded) "Свернуть" else "Показать полностью",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorTheme.primary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { expanded = !expanded }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyComments(colorTheme: ColorTheme) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colorTheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Comment, contentDescription = null,
                tint = colorTheme.primary, modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Комментариев пока нет",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Начните обсуждение — напишите первым!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DayChip(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    isOwn: Boolean,
    groupStart: Boolean,
    groupEnd: Boolean,
    photoUrl: String?,
    avatarBase64: String?,
    canDelete: Boolean,
    isDark: Boolean,
    colorTheme: ColorTheme,
    maxBubbleWidth: androidx.compose.ui.unit.Dp,
    onDelete: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember(comment.id) { mutableStateOf(false) }

    val bubbleColor = when {
        isOwn -> colorTheme.bubbleOwn
        isDark -> colorTheme.bubbleOther
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isOwn -> colorTheme.bubbleOwnText
        isDark -> colorTheme.bubbleOtherText
        else -> MaterialTheme.colorScheme.onSurface
    }
    val big = 18.dp
    val small = 5.dp
    val bubbleShape = if (isOwn) {
        RoundedCornerShape(
            topStart = big, topEnd = if (groupStart) big else small,
            bottomEnd = if (groupEnd) small else small, bottomStart = big
        )
    } else {
        RoundedCornerShape(
            topStart = if (groupStart) big else small, topEnd = big,
            bottomEnd = big, bottomStart = if (groupEnd) small else small
        )
    }
    val authorColor = AuthorPalette[abs(comment.senderId.hashCode()) % AuthorPalette.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = if (groupStart) 6.dp else 1.5.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            if (groupEnd) {
                UserAvatar(
                    displayName = comment.senderName,
                    photoUrl = photoUrl,
                    avatarBase64 = avatarBase64,
                    size = 36.dp
                )
            } else {
                Spacer(modifier = Modifier.width(36.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box {
            Column(
                modifier = Modifier
                    .widthIn(min = 64.dp, max = maxBubbleWidth)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .then(
                        if (!isOwn && !isDark) Modifier.border(
                            0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), bubbleShape
                        ) else Modifier
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true }
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 5.dp)
            ) {
                if (!isOwn && groupStart) {
                    Text(
                        comment.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = authorColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(
                    formatCommentTime(comment.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End).padding(top = 1.dp)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Копировать") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        clipboard.setText(AnnotatedString(comment.text))
                        menuOpen = false
                    }
                )
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
        if (isOwn) Spacer(modifier = Modifier.width(2.dp))
    }
}

@Composable
private fun CommentInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    colorTheme: ColorTheme,
    onSend: () -> Unit
) {
    val canSend = value.isNotBlank()
    val sendColor by animateColorAsState(
        if (canSend) colorTheme.primary else colorTheme.primary.copy(alpha = 0.3f),
        label = "sendColor"
    )
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.9f, label = "sendScale")
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Написать комментарий…") },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                maxLines = 5,
                shape = RoundedCornerShape(23.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(sendScale)
                    .clip(CircleShape)
                    .background(sendColor)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить",
                    tint = Color.White, modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun formatDayLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    if (sameDay(millis, now)) return "Сегодня"
    if (sameDay(millis, now - 24L * 60 * 60 * 1000)) return "Вчера"
    val sameYear = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR) ==
        Calendar.getInstance().get(Calendar.YEAR)
    val pattern = if (sameYear) "d MMMM" else "d MMMM yyyy"
    return SimpleDateFormat(pattern, Locale("ru")).format(Date(millis))
}

private fun formatCommentTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatCommentDateTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))

internal fun pluralComments(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "комментариев"
        mod10 == 1 -> "комментарий"
        mod10 in 2..4 -> "комментария"
        else -> "комментариев"
    }
}
