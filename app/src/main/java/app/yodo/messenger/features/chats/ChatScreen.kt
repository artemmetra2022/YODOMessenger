package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.ui.components.swipeToGoBack
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@Composable
fun ChatScreen(
    chatId: String,
    onBackClick: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenGroupInfo: (String) -> Unit,
    onForwardMessage: () -> Unit,
    onOpenImageViewer: (String, String, Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val hideKeyboardOnSend by viewModel.hideKeyboardOnSend.collectAsState()
    val colorTheme = LocalColorTheme.current

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.initialDraft) {
        uiState.initialDraft?.let { if (inputText.isBlank()) inputText = it }
    }
    LaunchedEffect(uiState.editingMessage) {
        uiState.editingMessage?.let { inputText = it.text }
    }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val base64 = withContext(Dispatchers.Default) {
                    ImageUtils.compressChatImageToBase64(context, it)
                }
                if (base64 != null) viewModel.sendImage(base64)
                else snackbarHostState.showSnackbar("Не удалось обработать фото")
            }
        }
    }

    fun trySend() {
        if (inputText.isNotBlank()) {
            viewModel.sendMessage(inputText)
            inputText = ""
            if (hideKeyboardOnSend) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
    }

    val displayedMessages = if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
        uiState.messages.filter { it.text.contains(uiState.searchQuery, ignoreCase = true) }
    } else {
        uiState.messages
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Поиск по сообщениям") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val otherUserId = uiState.otherUserId
                        Column(
                            modifier = when {
                                otherUserId != null -> Modifier.clickable { onOpenUserProfile(otherUserId) }
                                uiState.chatType == "GROUP" -> Modifier.clickable { onOpenGroupInfo(chatId) }
                                else -> Modifier
                            }
                        ) {
                            Text(text = uiState.chatTitle, style = MaterialTheme.typography.titleLarge)
                            val subtitle = when {
                                uiState.isOtherUserTyping -> "печатает..."
                                uiState.otherUserPresence?.isOnline == true -> "в сети"
                                uiState.otherUserPresence != null && uiState.otherUserPresence!!.lastSeenMillis > 0 ->
                                    "был(а) ${formatLastSeen(uiState.otherUserPresence!!.lastSeenMillis)}"
                                else -> null
                            }
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (uiState.isOtherUserTyping) colorTheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = if (uiState.isSearchActive) { { viewModel.toggleSearch() } } else onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!uiState.isSearchActive) {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "Поиск")
                        }
                        var showChatMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showChatMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                            }
                            DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Очистить историю") },
                                    onClick = { showChatMenu = false; viewModel.clearChatHistory() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Экспорт чата") },
                                    onClick = { showChatMenu = false; viewModel.exportChat(context) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить чат", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showChatMenu = false; viewModel.deleteChat(); onBackClick() }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (uiState.pinnedMessages.isNotEmpty()) {
                    val pinned = uiState.pinnedMessages.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Закреплённое сообщение", style = MaterialTheme.typography.labelSmall, color = colorTheme.primary)
                            Text(pinned.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                uiState.editingMessage?.let { editing ->
                    EditPreviewBar(message = editing, onCancel = { viewModel.setEditingMessage(null); inputText = "" })
                }
                uiState.replyingTo?.let { replyMessage ->
                    ReplyPreviewBar(
                        message = replyMessage,
                        isOwn = replyMessage.senderId == viewModel.currentUserId,
                        onCancel = { viewModel.setReplyingTo(null) }
                    )
                }
                MessageInputBar(
                    text = inputText,
                    onTextChange = { inputText = it; viewModel.onInputTextChanged(it) },
                    onSendClick = { trySend() },
                    onKeyboardSend = { if (sendOnEnter) trySend() },
                    sendOnEnter = sendOnEnter,
                    isSending = uiState.isSending,
                    onAttachClick = { imagePicker.launch("image/*") },
                    primaryColor = colorTheme.primary
                )
            }
        }
    ) { padding ->
        // НОВОЕ (п.8): свайп от левого края экрана вправо — переход назад (как в Telegram/iOS).
        // Зона старта жеста узкая (24dp от края), поэтому не конфликтует со свайпом
        // по самому сообщению (ответ), который стартует из любой точки бабла.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .swipeToGoBack(onBack = onBackClick)
        ) {
            if (displayedMessages.isEmpty()) {
                Text(
                    text = if (uiState.isSearchActive) "Ничего не найдено" else "Сообщений пока нет.\nНапишите первым!",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedMessages, key = { it.id }) { message ->
                        SwipeableMessageBubble(
                            message = message,
                            isOwnMessage = message.senderId == viewModel.currentUserId,
                            currentUserId = viewModel.currentUserId,
                            autoDownloadImages = autoDownloadImages,
                            colorTheme = colorTheme,
                            onReply = { viewModel.setReplyingTo(message) },
                            onEdit = { viewModel.setEditingMessage(message) },
                            onDelete = { viewModel.deleteMessage(message) },
                            onForward = { viewModel.prepareForward(message); onForwardMessage() },
                            onReact = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                            onPin = { viewModel.togglePinMessage(message.id) },
                            onBookmark = { viewModel.toggleBookmark(message.id) },
                            onImageClick = { base64 ->
                                onOpenImageViewer(base64, uiState.chatTitle, message.timestamp)
                            },
                            onReplyQuoteClick = { targetMessageId ->
                                // НОВОЕ (п.7): переход к оригинальному сообщению по клику на цитату.
                                // Если сообщение не найдено (удалено или скрыто поиском) — просто ничего не делаем.
                                val targetIndex = displayedMessages.indexOfFirst { it.id == targetMessageId }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableMessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    currentUserId: String?,
    autoDownloadImages: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onBookmark: () -> Unit,
    onImageClick: (String) -> Unit,
    onReplyQuoteClick: (String) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 80f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > swipeThreshold) onReply()
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(0f, 150f)
                    }
                )
            }
    ) {
        if (offsetX > 20f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Ответить",
                tint = colorTheme.primary.copy(alpha = (offsetX / swipeThreshold).coerceAtMost(1f)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(24.dp)
            )
        }
        Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
            MessageBubble(
                message = message, isOwnMessage = isOwnMessage,
                currentUserId = currentUserId, autoDownloadImages = autoDownloadImages,
                colorTheme = colorTheme,
                onReply = onReply, onEdit = onEdit, onDelete = onDelete,
                onForward = onForward, onReact = onReact, onPin = onPin,
                onBookmark = onBookmark, onImageClick = onImageClick,
                onReplyQuoteClick = onReplyQuoteClick
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    currentUserId: String?,
    autoDownloadImages: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onBookmark: () -> Unit,
    onImageClick: (String) -> Unit,
    onReplyQuoteClick: (String) -> Unit
) {
    val bubbleColor = if (isOwnMessage) colorTheme.bubbleOwn else colorTheme.bubbleOther
    val textColor = if (isOwnMessage) colorTheme.bubbleOwnText else colorTheme.bubbleOtherText
    val alignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var revealImage by remember { mutableStateOf(autoDownloadImages) }

    if (message.isDeleted) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            Text("Сообщение удалено", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(8.dp))
        }
        return
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start) {
            if (message.isPinned) {
                Row(modifier = Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Закреплено", tint = colorTheme.primary, modifier = Modifier.size(12.dp))
                    Text("Закреплено", style = MaterialTheme.typography.labelSmall, color = colorTheme.primary)
                }
            }
            Box {
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                            bottomEnd = if (isOwnMessage) 4.dp else 16.dp
                        ))
                        .background(bubbleColor)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { showMenu = true }
                        )
                ) {
                    message.forwardedFromSenderName?.let {
                        Text(
                            "Переслано от $it", style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.75f), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp)
                        )
                    }
                    message.replyToText?.let { replyText ->
                        // НОВОЕ (п.7): клик на цитату → переход к оригинальному сообщению.
                        // replyToMessageId может отсутствовать в старых сообщениях — тогда клик просто ничего не делает.
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .background(textColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable(enabled = message.replyToMessageId != null) {
                                    message.replyToMessageId?.let { onReplyQuoteClick(it) }
                                }
                                .padding(6.dp)
                        ) {
                            Text(message.replyToSenderName ?: "Сообщение", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = textColor)
                            // НОВОЕ (п.6): показываем "Картинка" вместо пустой строки для ответа на фото
                            Text(replyText.ifBlank { "Картинка" }, style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                    message.imageBase64?.let { base64 ->
                        if (revealImage) {
                            val bitmap = remember(base64) { ImageUtils.decodeBase64ToBitmap(base64) }
                            bitmap?.let { bmp ->
                                // Ширина картинки под её реальное соотношение сторон, а не фиксированный
                                // fillMaxWidth — иначе высокие/узкие фото тонут в широких полях пузыря
                                // по бокам. Высота ограничена, чтобы очень вытянутые фото не растягивали чат.
                                val aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()
                                val imageMaxWidth = 260.dp
                                val imageMaxHeight = 320.dp
                                val imageMinWidth = 140.dp
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Фото",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .padding(
                                            horizontal = 4.dp,
                                            vertical = if (message.forwardedFromSenderName != null || message.replyToText != null) 4.dp else 4.dp
                                        )
                                        .widthIn(min = imageMinWidth, max = imageMaxWidth)
                                        .heightIn(max = imageMaxHeight)
                                        .aspectRatio(aspectRatio, matchHeightConstraintsFirst = aspectRatio < 1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onImageClick(base64) }
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textColor.copy(alpha = 0.12f))
                                    .clickable { revealImage = true },
                                contentAlignment = Alignment.Center
                            ) { Text("Тап, чтобы загрузить фото", color = textColor) }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text, color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 0.dp
                            ).padding(top = if (message.replyToText != null || message.imageBase64 != null) 4.dp else 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End)
                            .padding(horizontal = 12.dp)
                            .padding(top = 2.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isEdited) {
                            Text("изменено ", color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(formatMessageTime(message.timestamp), color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        if (isOwnMessage) {
                            val statusIcon = if (message.status == MessageStatus.READ) Icons.Filled.DoneAll else Icons.Filled.Done
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = if (message.status == MessageStatus.READ) "Прочитано" else "Отправлено",
                                tint = if (message.status == MessageStatus.READ) Color(0xFF60E6FF) else textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp).padding(start = 4.dp)
                            )
                        }
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        QUICK_REACTIONS.forEach { emoji ->
                            Text(emoji, fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                modifier = Modifier.clickable { onReact(emoji); showMenu = false }.padding(6.dp))
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Ответить") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) }, onClick = { showMenu = false; onReply() })
                    DropdownMenuItem(text = { Text(if (message.isPinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) }, onClick = { showMenu = false; onPin() })
                    DropdownMenuItem(text = { Text("В избранное") }, leadingIcon = { Icon(Icons.Filled.BookmarkBorder, contentDescription = null) }, onClick = { showMenu = false; onBookmark() })
                    if (message.text.isNotBlank()) {
                        DropdownMenuItem(text = { Text("Копировать") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }, onClick = { showMenu = false; clipboardManager.setText(AnnotatedString(message.text)) })
                    }
                    DropdownMenuItem(text = { Text("Переслать") }, leadingIcon = { Icon(Icons.Filled.Forward, contentDescription = null) }, onClick = { showMenu = false; onForward() })
                    if (isOwnMessage) {
                        DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }, onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.filterValues { it.isNotEmpty() }.forEach { (emoji, uids) ->
                        val reactedByMe = currentUserId in uids
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (reactedByMe) colorTheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onReact(emoji) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, style = MaterialTheme.typography.labelMedium)
                            if (uids.size > 1) Text(" ${uids.size}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPreviewBar(message: Message, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Edit, contentDescription = null, tint = LocalColorTheme.current.primary)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text("Редактирование", style = MaterialTheme.typography.labelLarge, color = LocalColorTheme.current.primary, fontWeight = FontWeight.Bold)
            Text(message.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Отменить") }
    }
}

@Composable
private fun ReplyPreviewBar(message: Message, isOwn: Boolean, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(width = 3.dp, height = 32.dp).background(LocalColorTheme.current.primary))
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(if (isOwn) "Вы" else "Ответ", style = MaterialTheme.typography.labelLarge, color = LocalColorTheme.current.primary, fontWeight = FontWeight.Bold)
            Text(message.previewText(), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Отменить ответ") }
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onKeyboardSend: () -> Unit,
    sendOnEnter: Boolean,
    isSending: Boolean,
    onAttachClick: () -> Unit,
    primaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttachClick) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить фото", tint = primaryColor)
        }
        OutlinedTextField(
            value = text, onValueChange = onTextChange,
            placeholder = { Text("Сообщение...") },
            modifier = Modifier.weight(1f), maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default),
            keyboardActions = KeyboardActions(onSend = { onKeyboardSend() })
        )
        IconButton(onClick = onSendClick, enabled = !isSending && text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = primaryColor)
        }
    }
}

private fun formatMessageTime(millis: Long): String {
    if (millis == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun formatLastSeen(millis: Long): String {
    val diffMillis = System.currentTimeMillis() - millis
    val diffMinutes = diffMillis / 60_000
    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> "$diffMinutes мин назад"
        diffMinutes < 24 * 60 -> "${diffMinutes / 60} ч назад"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))
    }
}
