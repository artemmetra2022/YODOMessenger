package app.yodo.messenger.features.chats

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Campaign
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.components.swipeToGoBack
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.util.AudioUtils
import app.yodo.messenger.util.ChatScreenshotUtils
import app.yodo.messenger.util.FileUtils
import app.yodo.messenger.util.ImageUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
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
    onOpenChatStats: (String) -> Unit,
    onForwardMessage: () -> Unit,
    onOpenImageViewer: (String, String, Long) -> Unit,
    onOpenChannelProfile: (String) -> Unit,
    onOpenComments: (chatId: String, messageId: String) -> Unit,
    onInviteToChannel: (String) -> Unit = {},
    onOpenChannelStats: (String) -> Unit = {},
    onShareContactQr: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val advancedPollsEnabled by viewModel.advancedPollsEnabled.collectAsState()
    val hideKeyboardOnSend by viewModel.hideKeyboardOnSend.collectAsState()
    val chatBackgroundType by viewModel.chatBackgroundType.collectAsState()
    val chatBackgroundCustomPath by viewModel.chatBackgroundCustomPath.collectAsState()
    val colorTheme = LocalColorTheme.current
    var inputText by remember { mutableStateOf("") }
    // НОВОЕ (система жалоб): сообщение, на которое сейчас ��ткрыт диалог "Пожаловаться".
    var reportTargetMessage by remember { mutableStateOf<app.yodo.messenger.domain.model.Message?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var showScheduledList by remember { mutableStateOf(false) }

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
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
    }

    // НОВОЕ (одноразовые медиа): true, если следующее выбранное фото нужно отправить как
    // "на один просмотр" — флаг выставляется перед запуском imagePicker в AttachMenuDialog
    // и сбрасывается сразу после отправки.
    var pendingImageIsViewOnce by remember { mutableStateOf(false) }
    // НОВОЕ (одноразовые медиа): id сообщения, чьё view-once фото сейчас показано на весь
    // экран поверх чата. Null — оверлей закрыт.
    var viewOnceOverlayMessage by remember { mutableStateOf<app.yodo.messenger.domain.model.Message?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val asViewOnce = pendingImageIsViewOnce
            pendingImageIsViewOnce = false
            coroutineScope.launch {
                val base64 = withContext(Dispatchers.Default) {
                    ImageUtils.compressChatImageToBase64(context, it)
                }
                if (base64 != null) viewModel.sendImage(base64, isViewOnce = asViewOnce)
                else snackbarHostState.showSnackbar("Не удалось обработать фото")
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val result = withContext(Dispatchers.Default) {
                    FileUtils.prepareFileForSending(context, it)
                }
                when (result) {
                    is FileUtils.PickResult.Success -> viewModel.sendFile(
                        result.file.base64, result.file.fileName, result.file.mimeType, result.file.sizeBytes
                    )
                    is FileUtils.PickResult.TooLarge -> snackbarHostState.showSnackbar(
                        "Файл слишком большой (${FileUtils.formatSize(result.actualSizeBytes)}). " +
                                "Максимум — ${FileUtils.formatSize(FileUtils.MAX_FILE_SIZE_BYTES)}. " +
                                "Попробуйте отправить его через другое приложение (например, облако)."
                    )
                    FileUtils.PickResult.Error -> snackbarHostState.showSnackbar("Не удалось прочитать файл")
                }
            }
        }
    }

    var showAttachMenu by remember { mutableStateOf(false) }
    // НОВОЕ (картинки из буфера + подпись): base64 картинки, ожидающей подписью
    // и подтверждения отправки (не моментальная отправка).
    var pendingCaptionImageBase64 by remember { mutableStateOf<String?>(null) }

    // НОВОЕ (картинки из буфера): читаем изображение из системного буфера обмена.
    fun pasteImageFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = cm?.primaryClip
        val uri: Uri? = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).uri else null
        if (uri == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("В буфере обмена нет изображения") }
            return
        }
        coroutineScope.launch {
            val base64 = withContext(Dispatchers.Default) {
                ImageUtils.compressChatImageToBase64(context, uri)
            }
            if (base64 != null) pendingCaptionImageBase64 = base64
            else snackbarHostState.showSnackbar("Не удалось обработать изображение из буфера")
        }
    }

    var showLocationPicker by remember { mutableStateOf(false) }
    var showPollCreation by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) showLocationPicker = true
        else coroutineScope.launch { snackbarHostState.showSnackbar("Нужен доступ к геолокации, чтобы отправить точку на карте") }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    var pendingMessageTtlSeconds by remember { mutableStateOf<Long?>(null) }
    var pendingMessageTtlExplicitlySet by remember { mutableStateOf(false) }
    var showPerMessageTtlDialog by remember { mutableStateOf(false) }
    var messagesAreaWindowBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // НОВОЕ (секретная фича «тихие публикации»): режим тихой публикации для каналов.
    var channelSilentMode by remember { mutableStateOf(false) }

    fun trySend() {
        if (inputText.isNotBlank()) {
            viewModel.sendMessage(
                inputText,
                explicitTtlSeconds = pendingMessageTtlSeconds,
                hasExplicitTtl = pendingMessageTtlExplicitlySet,
                silent = (uiState.chatType == "CHANNEL") && channelSilentMode
            )
            inputText = ""
            pendingMessageTtlSeconds = null
            pendingMessageTtlExplicitlySet = false
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
    val isChannel = uiState.chatType == "CHANNEL"

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
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
                            val headerModifier = when {
                                otherUserId != null -> Modifier.clickable { onOpenUserProfile(otherUserId) }
                                uiState.chatType == "GROUP" -> Modifier.clickable { onOpenGroupInfo(chatId) }
                                uiState.chatType == "CHANNEL" -> Modifier.clickable { onOpenChannelProfile(chatId) }
                                else -> Modifier
                            }
                            Row(
                                modifier = headerModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isChannel) {
                                    if (uiState.channelAvatarBase64 != null) {
                                        UserAvatar(
                                            displayName = uiState.chatTitle,
                                            photoUrl = null,
                                            avatarBase64 = uiState.channelAvatarBase64,
                                            size = 36.dp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                                .background(colorTheme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Y", fontWeight = FontWeight.Bold, color = colorTheme.primary,
                                                style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                } else if (otherUserId != null) {
                                    UserAvatar(
                                        displayName = uiState.chatTitle,
                                        photoUrl = uiState.otherUserPhotoUrl,
                                        avatarBase64 = uiState.otherUserAvatarBase64,
                                        size = 36.dp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = uiState.chatTitle, style = MaterialTheme.typography.titleLarge)
                                        if (uiState.isVerified) {
                                            var showVerifiedInfo by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.padding(start = 6.dp)) {
                                                Box(
                                                    modifier = Modifier.size(20.dp).clip(CircleShape)
                                                        .background(Color(0xFF22C55E))
                                                        .clickable { showVerifiedInfo = true },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Verified,
                                                        contentDescription = "Верифицирован",
                                                        tint = Color(0xFF1D9BF0),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showVerifiedInfo,
                                                    onDismissRequest = { showVerifiedInfo = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Это официальный аккаунт, ему можно доверять.") },
                                                        onClick = { showVerifiedInfo = false }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (isChannel) {
                                        Text(
                                            text = "${uiState.subscriberCount} " + subscriberCountLabel(uiState.subscriberCount),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.Gray
                                        )
                                    } else if (uiState.chatType == "GROUP") {
                                        if (uiState.typingUserNames.isNotEmpty()) {
                                            val label = when (uiState.typingUserNames.size) {
                                                1 -> "${uiState.typingUserNames.first()} печатает..."
                                                else -> "${uiState.typingUserNames.size} печатают..."
                                            }
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colorTheme.primary
                                            )
                                        }
                                    } else {
                                        val subtitle = when {
                                            uiState.isOtherUserTyping -> "печатает..."
                                            uiState.otherUserPresence?.isOnline == true -> "в сети"
                                            uiState.otherUserPresence != null && uiState.otherUserPresence!!.lastSeenMillis > 0 -> {
                                                @Suppress("UNUSED_EXPRESSION") nowTick
                                                "был(а) ${formatLastSeen(uiState.otherUserPresence!!.lastSeenMillis)}"
                                            }
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
                            var showDisappearingDialog by remember { mutableStateOf(false) }
                            var showDeleteChannelDialog by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showChatMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                                }
                                DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                                    if (isChannel && uiState.isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text("Пригласить в канал") },
                                            leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                                            onClick = { showChatMenu = false; onInviteToChannel(chatId) }
                                        )
                                    }
                                    if (isChannel && uiState.isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text("Отложенные посты") },
                                            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                                            onClick = { showChatMenu = false; showScheduledList = true }
                                        )
                                    }
                                    if (isChannel && uiState.isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text("Статистика канала") },
                                            leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                                            onClick = { showChatMenu = false; onOpenChannelStats(chatId) }
                                        )
                                    }
                                    if (isChannel && !uiState.isChannelOwner) {
                                        DropdownMenuItem(
                                            text = { Text(if (uiState.isSubscribed) "Отписаться" else "Подписаться") },
                                            leadingIcon = {
                                                Icon(
                                                    if (uiState.isSubscribed) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = { showChatMenu = false; viewModel.toggleChannelSubscription() }
                                        )
                                    }
                                    if (!isChannel) {
                                        DropdownMenuItem(
                                            text = { Text("Исчезающие сообщения" + if (uiState.disappearingTtlSeconds != null) " (${disappearingTtlLabel(uiState.disappearingTtlSeconds)})" else "") },
                                            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                                            onClick = { showChatMenu = false; showDisappearingDialog = true }
                                        )
                                    }
                                    // НОВОЕ (поделиться контактом абонента): делимся QR-кодом контакта собеседника, а не своего.
                                    if (uiState.chatType == "PRIVATE" && uiState.otherUserId != null) {
                                        DropdownMenuItem(
                                            text = { Text("Поделиться контактом абонента (QR)") },
                                            leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                                            onClick = { showChatMenu = false; onShareContactQr(uiState.otherUserId!!) }
                                        )
                                    }
                                    DropdownMenuItem(text = { Text("Очистить историю") }, onClick = { showChatMenu = false; viewModel.clearChatHistory() })
                                    DropdownMenuItem(text = { Text("Статистика чата") }, onClick = { showChatMenu = false; onOpenChatStats(chatId) })
                                    DropdownMenuItem(text = { Text("Экспорт чата") }, onClick = { showChatMenu = false; viewModel.exportChat(context) })
                                    DropdownMenuItem(
                                        text = { Text("Сделать скриншот") },
                                        leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                                        onClick = {
                                            showChatMenu = false
                                            val bounds = messagesAreaWindowBounds
                                            val window = ChatScreenshotUtils.findActivity(context)?.window
                                            if (bounds == null || window == null) {
                                                coroutineScope.launch { snackbarHostState.showSnackbar("Не удалось сделать скриншот") }
                                            } else {
                                                coroutineScope.launch {
                                                    val success = ChatScreenshotUtils.captureAndSaveChatScreenshot(context, window, bounds)
                                                    snackbarHostState.showSnackbar(
                                                        if (success) "Скриншот сохр��нён в галерею" else "Не удалось сделать скриншот"
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    if (isChannel && uiState.isChannelOwner) {
                                        DropdownMenuItem(
                                            text = { Text("Удалить канал", color = MaterialTheme.colorScheme.error) },
                                            onClick = { showChatMenu = false; showDeleteChannelDialog = true }
                                        )
                                    } else {
                                        DropdownMenuItem(text = { Text("Удалить чат", color = MaterialTheme.colorScheme.error) }, onClick = { showChatMenu = false; viewModel.deleteChat(); onBackClick() })
                                    }
                                }
                            }
                            if (showDisappearingDialog) {
                                DisappearingMessagesDialog(
                                    currentTtlSeconds = uiState.disappearingTtlSeconds,
                                    onSelect = { ttl -> viewModel.setDisappearingTtl(ttl); showDisappearingDialog = false },
                                    onDismiss = { showDisappearingDialog = false }
                                )
                            }
                            if (showDeleteChannelDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteChannelDialog = false },
                                    title = { Text("Удалить канал?") },
                                    text = { Text("Канал будет удалён безвозвратно для всех подписчиков. Это действие нельзя отменить.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showDeleteChannelDialog = false
                                            viewModel.deleteChannel()
                                            onBackClick()
                                        }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteChannelDialog = false }) { Text("Отмена") }
                                    }
                                )
                            }
                        }
                    }
                )
                if (uiState.disappearingTtlSeconds != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Text(
                            "Исчезающие сообщения: ${disappearingTtlLabel(uiState.disappearingTtlSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                if (uiState.pinnedMessages.isNotEmpty()) {
                    val pinned = uiState.pinnedMessages.first()
                    var showUnpinConfirm by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val idx = displayedMessages.indexOfFirst { it.id == pinned.id }
                                if (idx >= 0) coroutineScope.launch { listState.animateScrollToItem(idx) }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Закреплённое сообщение", style = MaterialTheme.typography.labelSmall, color = colorTheme.primary, fontWeight = FontWeight.Bold)
                            Text(pinned.text.ifBlank { "📷 Фото" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Открепить",
                            tint = colorTheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(18.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showUnpinConfirm = true }
                                )
                        )
                    }
                    if (showUnpinConfirm) {
                        AlertDialog(
                            onDismissRequest = { showUnpinConfirm = false },
                            title = { Text("От��репить сообщение?") },
                            text = { Text("Сообщение будет откреплено из этого чата.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showUnpinConfirm = false
                                    viewModel.togglePinMessage(pinned.id)
                                }) { Text("Открепить") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUnpinConfirm = false }) { Text("Отмена") }
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column {
                if (uiState.scheduledMessages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showScheduledList = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            if (isChannel) "Отложенные посты: ${uiState.scheduledMessages.size}"
                            else "Отложенные сообщения: ${uiState.scheduledMessages.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorTheme.primary,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                if (showScheduledList) {
                    ScheduledMessagesDialog(
                        items = uiState.scheduledMessages,
                        onCancel = { viewModel.cancelScheduledMessage(it) },
                        onDismiss = { showScheduledList = false }
                    )
                }
                if (uiState.justForwardedMessageId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Forward, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
                        val targetName = uiState.justForwardedTargetName
                        val targetUserId = uiState.justForwardedTargetUserId
                        Row(
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        ) {
                            Text(
                                text = if (targetName != null) "Сообщение переслано " else "Сообщение переслано",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorTheme.primary
                            )
                            if (targetName != null) {
                                Text(
                                    text = "пользователю $targetName",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorTheme.primary,
                                    modifier = Modifier
                                        .then(
                                            if (targetUserId != null)
                                                Modifier.clickable { onOpenUserProfile(targetUserId) }
                                            else Modifier
                                        )
                                )
                            }
                        }
                        Text(
                            text = "${uiState.forwardUndoSecondsLeft}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        TextButton(onClick = { viewModel.undoForward() }) {
                            Text("Отменить")
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
                if (isChannel && !uiState.isAdmin) {
                    ChannelBottomBar(
                        isSubscribed = uiState.isSubscribed,
                        isOfficial = uiState.isVerified,
                        colorTheme = colorTheme,
                        onSubscribe = { viewModel.toggleChannelSubscription() }
                    )
                } else {
                    var showScheduleDialog by remember { mutableStateOf(false) }
                    var isRecording by remember { mutableStateOf(false) }
                    var recordingElapsedMs by remember { mutableStateOf(0L) }
                    var activeRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
                    var activeRecordingFile by remember { mutableStateOf<java.io.File?>(null) }
                    var recordedVoiceFile by remember { mutableStateOf<java.io.File?>(null) }
                    var recordedVoiceDurationMs by remember { mutableStateOf(0L) }
                    var micPressHeld by remember { mutableStateOf(false) }

                    fun beginRecordingInternal() {
                        val result = AudioUtils.startRecording(context)
                        if (result != null) {
                            activeRecorder = result.first
                            activeRecordingFile = result.second
                            isRecording = true
                            recordingElapsedMs = 0L
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Не удалось начать запись") }
                        }
                    }
                    val micPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            if (micPressHeld) {
                                beginRecordingInternal()
                            }
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Нужен доступ к микрофону") }
                        }
                    }
                    fun startVoiceRecording() {
                        micPressHeld = true
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            beginRecordingInternal()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    fun discardRecordingInternal() {
                        val recorder = activeRecorder
                        val file = activeRecordingFile
                        if (recorder != null && file != null) {
                            AudioUtils.cancelRecording(recorder, file)
                        }
                        activeRecorder = null
                        activeRecordingFile = null
                        isRecording = false
                        recordingElapsedMs = 0L
                    }
                    fun cancelVoiceRecording() {
                        discardRecordingInternal()
                    }
                    fun stopVoiceRecordingToPreview() {
                        micPressHeld = false
                        val recorder = activeRecorder
                        val file = activeRecordingFile
                        isRecording = false
                        if (recorder == null || file == null) return
                        val stopped = AudioUtils.stopRecording(recorder)
                        activeRecorder = null
                        activeRecordingFile = null
                        if (!stopped) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Не удалось сохранить запись") }
                            return
                        }
                        if (recordingElapsedMs < 700L) {
                            file.delete()
                            recordingElapsedMs = 0L
                            return
                        }
                        recordedVoiceFile = file
                        recordedVoiceDurationMs = recordingElapsedMs
                        recordingElapsedMs = 0L
                    }
                    fun sendRecordedVoice() {
                        val file = recordedVoiceFile ?: return
                        recordedVoiceFile = null
                        coroutineScope.launch {
                            val encoded = withContext(Dispatchers.Default) {
                                AudioUtils.fileToBase64(file)
                            }
                            if (encoded != null) {
                                viewModel.sendVoice(encoded.first, encoded.second)
                            } else {
                                snackbarHostState.showSnackbar("Запись слишком длинная, попробуйте короче")
                            }
                        }
                    }
                    fun discardRecordedVoice() {
                        recordedVoiceFile?.delete()
                        recordedVoiceFile = null
                        recordedVoiceDurationMs = 0L
                    }
                    LaunchedEffect(isRecording) {
                        if (isRecording) {
                            val startedAt = System.currentTimeMillis()
                            while (isRecording) {
                                recordingElapsedMs = System.currentTimeMillis() - startedAt
                                if (recordingElapsedMs >= AudioUtils.MAX_RECORDING_MS) {
                                    stopVoiceRecordingToPreview()
                                    break
                                }
                                kotlinx.coroutines.delay(200L)
                            }
                        }
                    }
                    // НОВОЕ (секретная фича «тихие публикации»): тумблер тихого режима для каналов.
                    if (isChannel) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { channelSilentMode = !channelSilentMode }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (channelSilentMode) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = if (channelSilentMode) colorTheme.accent else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (channelSilentMode) "Тихая публикация: без уведомления" else "Обычная публикация",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (channelSilentMode) colorTheme.accent else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    when {
                        isRecording -> {
                            VoiceRecordingBar(
                                elapsedMs = recordingElapsedMs,
                                colorTheme = colorTheme,
                                onCancel = { cancelVoiceRecording() },
                                onSend = { stopVoiceRecordingToPreview() }
                            )
                        }
                        recordedVoiceFile != null -> {
                            VoicePreviewBar(
                                file = recordedVoiceFile!!,
                                durationMs = recordedVoiceDurationMs,
                                colorTheme = colorTheme,
                                onCancel = { discardRecordedVoice() },
                                onSend = { sendRecordedVoice() }
                            )
                        }
                        else -> {
                            MessageInputBar(
                                text = inputText,
                                onTextChange = { inputText = it; viewModel.onInputTextChanged(it) },
                                onSendClick = { trySend() },
                                onSendLongPress = { if (inputText.isNotBlank()) showScheduleDialog = true },
                                onKeyboardSend = { if (sendOnEnter) trySend() },
                                sendOnEnter = sendOnEnter,
                                isSending = uiState.isSending,
                                onAttachClick = { showAttachMenu = true },
                                onMicPressStart = { startVoiceRecording() },
                                onMicPressEnd = { stopVoiceRecordingToPreview() },
                                colorTheme = colorTheme,
                                placeholder = if (isChannel && uiState.isAdmin) "Вы админ, вам можно писать" else "Сообщение...",
                                pendingTtlSeconds = pendingMessageTtlSeconds,
                                isTtlExplicitlySet = pendingMessageTtlExplicitlySet,
                                onTtlIconClick = { showPerMessageTtlDialog = true },
                                onScheduleClick = { if (inputText.isNotBlank()) showScheduleDialog = true }
                            )
                        }
                    }
                    if (showPerMessageTtlDialog) {
                        DisappearingMessagesDialog(
                            currentTtlSeconds = if (pendingMessageTtlExplicitlySet) pendingMessageTtlSeconds else uiState.disappearingTtlSeconds,
                            onSelect = { ttl ->
                                pendingMessageTtlSeconds = ttl
                                pendingMessageTtlExplicitlySet = true
                                showPerMessageTtlDialog = false
                            },
                            onDismiss = { showPerMessageTtlDialog = false },
                            perMessage = true
                        )
                    }
                    if (showScheduleDialog) {
                        ScheduleMessageDialog(
                            onConfirm = { millis ->
                                viewModel.scheduleMessage(inputText, millis)
                                inputText = ""
                                showScheduleDialog = false
                            },
                            onDismiss = { showScheduleDialog = false }
                        )
                    }
                    if (showAttachMenu) {
                        AttachMenuDialog(
                            onDismiss = { showAttachMenu = false },
                            onPickPhoto = {
                                showAttachMenu = false
                                pendingImageIsViewOnce = false
                                imagePicker.launch("image/*")
                            },
                            onPickViewOncePhoto = {
                                showAttachMenu = false
                                pendingImageIsViewOnce = true
                                imagePicker.launch("image/*")
                            },
                            onPickFile = {
                                showAttachMenu = false
                                filePicker.launch(arrayOf("*/*"))
                            },
                            onPickLocation = {
                                showAttachMenu = false
                                if (hasLocationPermission()) {
                                    showLocationPicker = true
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                }
                            },
                            onPickPoll = {
                                showAttachMenu = false
                                showPollCreation = true
                            },
                            onPasteImage = {
                                showAttachMenu = false
                                pasteImageFromClipboard()
                            }
                        )
                    }
                    // НОВОЕ (картинки из буфера + подпись): превью + поле подписи перед отправкой.
                    pendingCaptionImageBase64?.let { imgBase64 ->
                        ImageCaptionDialog(
                            imageBase64 = imgBase64,
                            onDismiss = { pendingCaptionImageBase64 = null },
                            onSend = { caption ->
                                viewModel.sendImage(imgBase64, caption = caption)
                                pendingCaptionImageBase64 = null
                            }
                        )
                    }
                    if (showLocationPicker) {
                        LocationPickerDialog(
                            onDismiss = { showLocationPicker = false },
                            onConfirm = { lat, lng ->
                                viewModel.sendLocation(lat, lng)
                                showLocationPicker = false
                            }
                        )
                    }
                    if (showPollCreation) {
                        PollCreationDialog(
                            advancedPollsEnabled = advancedPollsEnabled,
                            colorTheme = colorTheme,
                            onDismiss = { showPollCreation = false },
                            onConfirm = { question, options, isAnonymous, allowMultiple, closesAtMillis, isQuiz, correctOptionIndex, explanation ->
                                viewModel.sendPoll(question, options, isAnonymous, allowMultiple, closesAtMillis, isQuiz, correctOptionIndex, explanation)
                                showPollCreation = false
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { coordinates ->
                    val boundsInWindow = coordinates.boundsInWindow()
                    messagesAreaWindowBounds = android.graphics.Rect(
                        boundsInWindow.left.toInt(),
                        boundsInWindow.top.toInt(),
                        boundsInWindow.right.toInt(),
                        boundsInWindow.bottom.toInt()
                    )
                }
                .then(
                    if (chatBackgroundType != app.yodo.messenger.data.local.ChatBackgroundType.CUSTOM_IMAGE) {
                        Modifier.background(chatBackgroundBrush(chatBackgroundType, colorTheme))
                    } else Modifier
                )
                .swipeToGoBack(onBack = onBackClick)
        ) {
            if (chatBackgroundType == app.yodo.messenger.data.local.ChatBackgroundType.CUSTOM_IMAGE &&
                chatBackgroundCustomPath.isNotBlank()
            ) {
                coil.compose.AsyncImage(
                    model = chatBackgroundCustomPath,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
            if (displayedMessages.isEmpty()) {
                val emptyText = when {
                    isChannel -> "Здесь пока ничего нет"
                    uiState.isSearchActive -> "Ничего не найдено"
                    else -> "Сообщений пока нет.\nНапишите первым!"
                }
                Text(
                    text = emptyText,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var previousDateLabel: String? = null
                    displayedMessages.forEach { message ->
                        val dateLabel = formatDateSeparator(message.timestamp)
                        if (dateLabel != previousDateLabel) {
                            item(key = "date_${message.id}") {
                                DateSeparator(dateLabel)
                            }
                            previousDateLabel = dateLabel
                        }
                        item(key = message.id) {
                            // НОВОЕ (F3): отмечаем просмотр поста канала при появлени�� на экране.
                            if (isChannel) {
                                LaunchedEffect(message.id) { viewModel.registerPostView(message.id) }
                            }
                            SwipeableMessageBubble(
                                message = message,
                                isOwnMessage = message.senderId == viewModel.currentUserId,
                                currentUserId = viewModel.currentUserId,
                                autoDownloadImages = autoDownloadImages,
                                colorTheme = colorTheme,
                                isChannel = isChannel,
                                onCommentsClick = { onOpenComments(chatId, message.id) },
                                onReply = { viewModel.setReplyingTo(message) },
                                onEdit = { viewModel.setEditingMessage(message) },
                                onDelete = { viewModel.deleteMessage(message) },
                                onForward = { viewModel.prepareForward(message); onForwardMessage() },
                                onReport = { reportTargetMessage = message },
                                onReact = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                                onPin = { viewModel.togglePinMessage(message.id) },
                                onSaveToFavorite = { viewModel.saveToFavorite(message) },
                                onVotePoll = { optionIndex -> viewModel.voteOnPoll(message.id, optionIndex) },
                                onClosePoll = { viewModel.closePoll(message.id) },
                                onImageClick = { base64 ->
                                    onOpenImageViewer(base64, uiState.chatTitle, message.timestamp)
                                },
                                // НОВОЕ (одноразовые медиа): открываем фото в оверлее ПОВЕРХ
                                // чата (не через onOpenImageViewer/н����вигацию — там есть общий
                                // держатель картинки и повторные открытия), и только для чужих
                                // сообщений своей же отправки не помечаем "открыто", т.к. это
                                // сделает получатель на своём устройстве.
                                onViewOnceClick = { msg ->
                                    if (msg.imageBase64 != null) viewOnceOverlayMessage = msg
                                },
                                onReplyQuoteClick = { targetMessageId ->
                                    val targetIndex = displayedMessages.indexOfFirst { it.id == targetMessageId }
                                    if (targetIndex >= 0) {
                                        coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                                    }
                                },
                                onForwardedSenderClick = { senderId -> onOpenUserProfile(senderId) },
                                onSwipeBack = onBackClick
                            )
                        }
                    }
                }
            }
        }
    }

    // НОВОЕ (система жалоб, п.5 ТЗ): диалог подачи жалобы на сообщение.
    reportTargetMessage?.let { message ->
        ReportDialog(
            chatId = chatId,
            targetUserId = message.senderId,
            targetUserName = "",
            messageId = message.id,
            messagePreview = message.previewText(),
            onDismiss = { reportTargetMessage = null },
            onSubmitted = {
                reportTargetMessage = null
                coroutineScope.launch { snackbarHostState.showSnackbar("Жалоба отправлена") }
            }
        )
    }

    // НОВОЕ (одноразовые медиа): полноэкранный показ view-once фото поверх чата.
    // Не переиспользует ImageViewerHolder/ImageViewerScreen намеренно — там доступны
    // сохранение/шеринг и повторный показ, а у view-once фото не должно быть ни того,
    // ни другого. Как только пользователь открыл экран, сразу помечаем сообщение как
    // просмотренное и стираем imageBase64 на сервере — повторно открыть уже нельзя,
    // в т.ч. если сообщение отправил сам пользователь себе на другое устройство.
    // НОВОЕ (детектор скриншотов): защита от повторной отправки уведомле��ия, если
    // ContentObserver.onChange сработает несколько раз на один и тот же файл скриншота
    // (типично для MediaStore — сначала PENDING-запись, потом финализация).
    var screenshotNoticeSent by remember(viewOnceOverlayMessage?.id) { mutableStateOf(false) }
    viewOnceOverlayMessage?.let { msg ->
        ViewOnceImageOverlay(
            imageBase64 = msg.imageBase64,
            onOpened = {
                if (!msg.viewOnceOpened) viewModel.markViewOnceImageOpened(msg.id)
            },
            onDismiss = { viewOnceOverlayMessage = null },
            onScreenshotDetected = {
                if (!screenshotNoticeSent) {
                    screenshotNoticeSent = true
                    viewModel.notifyScreenshotTaken()
                }
            }
        )
    }
}

@Composable
private fun ViewOnceImageOverlay(
    imageBase64: String?,
    onOpened: () -> Unit,
    onDismiss: () -> Unit,
    onScreenshotDetected: () -> Unit
) {
    // Помечаем как открытое ровно один раз, сразу при показе оверлея (не при закрытии) —
    // так фото стирается на сервере, даже если пользователь свернёт приложение до того,
    // как явно закроет полноэкранный просмотр.
    LaunchedEffect(imageBase64) { onOpened() }

    val context = LocalContext.current
    // НОВОЕ (защита от скриншотов, слой 1): FLAG_SECURE на окне Activity — стандартный
    // системный способ заблокировать скриншот/запись экрана для текущего окна (система
    // просто отдаёт чёрный кадр). Ставим при входе в оверлей и снимаем при выходе, а не
    // на всё время работы приложения — иначе пользователь не смог бы делать скриншоты
    // обычных сообщений, что не входит в задачу.
    DisposableEffect(Unit) {
        val activity = app.yodo.messenger.util.ChatScreenshotUtils.findActivity(context)
        activity?.window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    // НОВОЕ (защита от скриншотов, слой 2): детектор на случай, если FLAG_SECURE всё же
    // обойдён (см. комментарий в ScreenshotDetector.kt).
    app.yodo.messenger.util.DetectScreenshots(onScreenshotDetected = onScreenshotDetected)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Диалог Compose открывает СВОЁ окно поверх окна Activity — FLAG_SECURE, выставленный
        // только на activity.window, не наследуется на него автоматически, поэтому дублируем
        // флаг на окно самого диалога через DialogWindowProvider.
        val dialogWindowProvider = LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider
        DisposableEffect(Unit) {
            dialogWindowProvider?.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
            onDispose { }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val bitmap = remember(imageBase64) { imageBase64?.let { ImageUtils.decodeBase64ToBitmap(it) } }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Фото на один просмотр",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
            }
            Text(
                "Это фото исчезнет после закрытия",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            )
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SwipeableMessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    currentUserId: String?,
    autoDownloadImages: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    isChannel: Boolean,
    onCommentsClick: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReport: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onSaveToFavorite: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onClosePoll: () -> Unit,
    onImageClick: (String) -> Unit,
    onViewOnceClick: (Message) -> Unit,
    onReplyQuoteClick: (String) -> Unit,
    onForwardedSenderClick: (String) -> Unit,
    onSwipeBack: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val dpPerCm = 160f / 2.54f
    val replyThresholdDp = 1.5f * dpPerCm
    val forwardZoneEndDp = 2f * dpPerCm
    val maxDragDp = 220f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                val replyThresholdPx = replyThresholdDp * density
                val forwardZoneEndPx = forwardZoneEndDp * density
                val maxDragPx = maxDragDp * density
                detectHorizontalDragGestures(
                    onDragStart = { offsetX = 0f },
                    onDragEnd = {
                        when {
                            offsetX > replyThresholdPx -> onReply()
                            offsetX < -forwardZoneEndPx -> onSwipeBack()
                            // НОВОЕ (одноразовые медиа): свайп-пересылка недоступна для
                            // view-once сообщений — см. запрет в DropdownMenuItem выше.
                            offsetX < 0f && !message.isViewOnce -> onForward()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                    }
                )
            }
    ) {
        if (offsetX > 12f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Ответить",
                tint = colorTheme.primary.copy(alpha = (offsetX / (replyThresholdDp * 1f)).coerceAtMost(1f)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(24.dp)
            )
        } else if (offsetX < -12f) {
            Icon(
                Icons.Filled.Forward,
                contentDescription = "Переслать",
                tint = colorTheme.primary.copy(alpha = (-offsetX / forwardZoneEndDp).coerceAtMost(1f)),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(24.dp)
            )
        }
        Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
            MessageBubble(
                message = message, isOwnMessage = isOwnMessage,
                currentUserId = currentUserId, autoDownloadImages = autoDownloadImages,
                colorTheme = colorTheme,
                isChannel = isChannel, onCommentsClick = onCommentsClick,
                onReply = onReply, onEdit = onEdit, onDelete = onDelete,
                onForward = onForward, onReact = onReact, onPin = onPin,
                onReport = onReport,
                onSaveToFavorite = onSaveToFavorite,
                onVotePoll = onVotePoll,
                onClosePoll = onClosePoll,
                onImageClick = onImageClick, onViewOnceClick = onViewOnceClick,
                onReplyQuoteClick = onReplyQuoteClick,
                onForwardedSenderClick = onForwardedSenderClick
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
    isChannel: Boolean,
    onCommentsClick: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReport: () -> Unit,
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onSaveToFavorite: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onClosePoll: () -> Unit,
    onImageClick: (String) -> Unit,
    onViewOnceClick: (Message) -> Unit,
    onReplyQuoteClick: (String) -> Unit,
    onForwardedSenderClick: (String) -> Unit
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
                        .shadow(
                            elevation = 1.dp,
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                                bottomEnd = if (isOwnMessage) 4.dp else 16.dp
                            )
                        )
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
                        val senderId = message.forwardedFromSenderId
                        Text(
                            "Переслано от $it", style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.75f), fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 12.dp, top = 8.dp, end = 12.dp)
                                .then(
                                    if (senderId != null) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onForwardedSenderClick(senderId) }
                                        )
                                    } else Modifier
                                )
                        )
                    }
                    message.replyToText?.let { replyText ->
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
                            Text(replyText.ifBlank { "Картинка" }, style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                    if (message.isViewOnce) {
                        // НОВОЕ (одноразовые медиа): у view-once сообщений своя отрисовка —
                        // никогда не показываем imageBase64 инлайн в пузыре (только полноэкранно
                        // через onViewOnceClick), а после открытия показываем серую заглушку.
                        if (message.viewOnceOpened) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(72.dp)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textColor.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Фото открыто", color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textColor.copy(alpha = 0.12f))
                                    .clickable(enabled = message.imageBase64 != null) { onViewOnceClick(message) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = textColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Тап, чтобы посмотреть (один раз)", color = textColor)
                                }
                            }
                        }
                    } else {
                        message.imageBase64?.let { base64 ->
                            if (revealImage) {
                                val bitmap = remember(base64) { ImageUtils.decodeBase64ToBitmap(base64) }
                                bitmap?.let { bmp ->
                                    val aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Фото",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .widthIn(min = 140.dp, max = 260.dp)
                                            .heightIn(max = 320.dp)
                                            .aspectRatio(aspectRatio, matchHeightConstraintsFirst = aspectRatio < 1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onImageClick(base64) }
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(120.dp)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(textColor.copy(alpha = 0.12f))
                                        .clickable { revealImage = true },
                                    contentAlignment = Alignment.Center
                                ) { Text("Тап, чтобы загрузить фото", color = textColor) }
                            }
                        }
                    }
                    message.voiceBase64?.let { voiceBase64 ->
                        VoicePlayerBubble(
                            messageId = message.id,
                            voiceBase64 = voiceBase64,
                            durationMs = message.voiceDurationMs ?: 0L,
                            textColor = textColor,
                            accentColor = colorTheme.primary
                        )
                    }
                    message.fileBase64?.let { fileBase64 ->
                        FileAttachmentBubble(
                            messageId = message.id,
                            fileBase64 = fileBase64,
                            fileName = message.fileName ?: "Файл",
                            sizeBytes = message.fileSizeBytes ?: 0L,
                            textColor = textColor,
                            accentColor = colorTheme.primary
                        )
                    }
                    if (message.locationLat != null && message.locationLng != null) {
                        LocationMessageBubble(
                            lat = message.locationLat,
                            lng = message.locationLng,
                            textColor = textColor
                        )
                    }
                    message.poll?.let { poll ->
                        PollMessageBubble(
                            poll = poll,
                            currentUserId = currentUserId,
                            isOwnMessage = isOwnMessage,
                            textColor = textColor,
                            accentColor = colorTheme.primary,
                            onVote = { optionIndex -> onVotePoll(optionIndex) },
                            onClosePoll = onClosePoll
                        )
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text, color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 12.dp)
                                .padding(top = if (message.replyToText != null || message.imageBase64 != null) 4.dp else 8.dp)
                        )
                        LinkPreviewSection(
                            messageText = message.text,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp)
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
                        if (message.expiresAt != null) {
                            Icon(
                                Icons.Filled.Timer, contentDescription = "Исчезающее сообщение",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp).padding(end = 3.dp)
                            )
                        }
                        Text(formatMessageTime(message.timestamp), color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        // НОВОЕ: число просмотров канала теперь рядом со временем.
                        if (isChannel) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Visibility, contentDescription = "Просмотры",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp).padding(end = 3.dp)
                            )
                            Text("${message.viewCount}", color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        }
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
                    if (isChannel) {
                        DropdownMenuItem(
                            text = { Text("Комментарии") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null) },
                            onClick = { showMenu = false; onCommentsClick() }
                        )
                    }
                    DropdownMenuItem(text = { Text(if (message.isPinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) }, onClick = { showMenu = false; onPin() })
                    // НОВОЕ (одноразовые медиа): не даём сохранять view-once фото в избранное.
                    if (!message.isViewOnce) {
                        DropdownMenuItem(text = { Text("В избранное") }, leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null) }, onClick = { showMenu = false; onSaveToFavorite() })
                    }
                    if (message.text.isNotBlank()) {
                        DropdownMenuItem(text = { Text("Копировать") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }, onClick = { showMenu = false; clipboardManager.setText(AnnotatedString(message.text)) })
                    }
                    // НОВОЕ (одноразовые медиа): фото "на один просмотр" нельзя пересылать.
                    if (!message.isViewOnce) {
                        DropdownMenuItem(text = { Text("Переслать") }, leadingIcon = { Icon(Icons.Filled.Forward, contentDescription = null) }, onClick = { showMenu = false; onForward() })
                    }
                    if (isOwnMessage) {
                        DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Удалить") }, leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }, onClick = { showMenu = false; onDelete() })
                    } else {
                        // НОВОЕ (система жалоб, п.5 ТЗ): жалоба на чужое сообщение.
                        DropdownMenuItem(
                            text = { Text("Пожаловаться") },
                            leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                            onClick = { showMenu = false; onReport() }
                        )
                    }
                }
            }

            // ═══════════════ ЭТАП 14: АНИМИРОВАННЫЕ РЕАКЦИИ ═══════════════
            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.filterValues { it.isNotEmpty() }.forEach { (emoji, uids) ->
                        val reactedByMe = currentUserId in uids
                        val reactionCount = uids.size
                        var burstScale by remember { mutableFloatStateOf(1f) }
                        val animatedScale by animateFloatAsState(
                            targetValue = burstScale,
                            animationSpec = spring(
                                dampingRatio = 0.55f,
                                stiffness = 400f
                            ),
                            label = "reaction_burst"
                        )
                        LaunchedEffect(reactionCount) {
                            burstScale = 1.35f
                            kotlinx.coroutines.delay(120)
                            burstScale = 1f
                        }
                        AnimatedVisibility(
                            visible = true,
                            enter = scaleIn(
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                            ) + fadeIn(tween(200)),
                            exit = scaleOut(
                                animationSpec = tween(150)
                            ) + fadeOut(tween(150))
                        ) {
                            Row(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (reactedByMe) colorTheme.primary.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onReact(emoji) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    emoji,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
                                )
                                if (uids.size > 1) {
                                    Text(
                                        " ${uids.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isChannel) {
                Row(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onCommentsClick)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment, contentDescription = null,
                        tint = colorTheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // НОВОЕ: текст кнопки «Комментарии» теперь такого же размера, что и текст сообщения.
                    Text(
                        if (message.commentsCount > 0) "Комментарии · ${message.commentsCount}" else "Комментировать",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colorTheme.primary
                    )
                }
                // НОВОЕ (бейдж «🔥 Популярное»): сами просмотры теперь показываются рядом со временем.
                val totalReactions = message.reactions.values.sumOf { it.size }
                val isPopular = message.viewCount >= 100 || totalReactions >= 10
                if (isPopular) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔥 Популярное",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorTheme.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorTheme.accent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
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

private val COMMON_EMOJIS = listOf(
    "😀", "😂", "����", "��", "😊", "😉", "😎", "🤔",
    "😢", "😭", "😡", "🥳", "👍", "👎", "❤️", "🔥",
    "🎉", "🙏", "👏", "😴", "🤗", "😅", "😱", "🤷",
    "✅", "❌", "⭐", "💯", "😇", "🤝", "👀", "💔"
)

@Composable
private fun EmojiPickerPanel(onEmojiSelected: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(8.dp)
        ) {
            gridItems(COMMON_EMOJIS) { emoji ->
                Box(
                    modifier = Modifier
                        .clickable { onEmojiSelected(emoji) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onSendLongPress: () -> Unit,
    onKeyboardSend: () -> Unit,
    sendOnEnter: Boolean,
    isSending: Boolean,
    onAttachClick: () -> Unit,
    onMicPressStart: () -> Unit,
    onMicPressEnd: () -> Unit,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    placeholder: String = "Сообщение...",
    pendingTtlSeconds: Long? = null,
    isTtlExplicitlySet: Boolean = false,
    onTtlIconClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {}
) {
    val canSend = !isSending && text.isNotBlank()
    var showEmojiPicker by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column {
            if (showEmojiPicker) {
                EmojiPickerPanel(onEmojiSelected = { emoji -> onTextChange(text + emoji) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { showEmojiPicker = !showEmojiPicker },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.EmojiEmotions,
                        contentDescription = "Смайлики",
                        tint = if (showEmojiPicker) colorTheme.primary else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(colorTheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onAttachClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить фото", tint = colorTheme.primary, modifier = Modifier.size(24.dp))
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                ) {
                    OutlinedTextField(
                        value = text, onValueChange = onTextChange,
                        placeholder = { Text(placeholder, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(), maxLines = 5,
                        shape = RoundedCornerShape(22.dp),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default),
                        keyboardActions = KeyboardActions(onSend = { onKeyboardSend() })
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                if (text.isBlank() && !isSending) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(colorTheme.primary)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onMicPressStart()
                                        tryAwaitRelease()
                                        onMicPressEnd()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Удерживайте для записи голосового сообщения", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    // НОВОЕ: рядом с кнопкой отправки — две кнопки: таймер (настройка исчезания)
                    // и запланированная отправка.
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTtlIconClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = if (isTtlExplicitlySet) "Таймер сообщения: ${disappearingTtlLabel(pendingTtlSeconds)}" else "Таймер исчезновения сообщения",
                            tint = if (isTtlExplicitlySet) colorTheme.primary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSend, onClick = onScheduleClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = "Запланировать отправку",
                            tint = if (canSend) colorTheme.primary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (canSend) colorTheme.primary else colorTheme.primary.copy(alpha = 0.3f))
                            .clickable(enabled = canSend, onClick = onSendClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSending) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelBottomBar(
    isSubscribed: Boolean,
    isOfficial: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onSubscribe: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        if (!isSubscribed && !isOfficial) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(colorTheme.primary, colorTheme.accent)
                        )
                    )
                    .clickable(onClick = onSubscribe)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Notifications, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Подписаться на канал",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Campaign, contentDescription = null,
                    tint = Color.Gray, modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isOfficial) "Это официальный канал — публиковать посты могут только админы мессенджера"
                    else "Публиковать посты могут только админы канала. Комментировать могут все подписчики",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ���═════════════════════════════════════════════════════════════════
// VoiceRecordingBar (находится внутри этого файла, не является отдельным файлом)
// ИСПРАВЛЕНО: используем Animatable вместо infiniteTransition.animateFloat 
// для обхода бага компилятора Compose с выводом типов.
// ══════════════════════════════════════════════════════════════════
@Composable
private fun VoiceRecordingBar(
    elapsedMs: Long,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Отменить запись", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            
            // Пульсирующая точка-индикатор записи (исправлено через Animatable)
            val alphaAnim = remember { androidx.compose.animation.core.Animatable(1f) }
            LaunchedEffect(Unit) {
                while (true) {
                    alphaAnim.animateTo(0.2f, animationSpec = tween(700))
                    alphaAnim.animateTo(1f, animationSpec = tween(700))
                }
            }
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = alphaAnim.value))
            )
            
            Text(
                AudioUtils.formatDuration(elapsedMs),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            Text(
                "Запись голосового...",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colorTheme.primary)
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Отправить голосовое", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun VoicePreviewBar(
    file: java.io.File,
    durationMs: Long,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    var mediaPlayer by remember(file) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember(file) { mutableStateOf(false) }
    DisposableEffect(file) {
        onDispose {
            mediaPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
            mediaPlayer = null
        }
    }
    fun togglePlayback() {
        val existing = mediaPlayer
        if (existing != null) {
            if (isPlaying) {
                existing.pause()
                isPlaying = false
            } else {
                if (existing.currentPosition >= existing.duration) {
                    existing.seekTo(0)
                }
                existing.start()
                isPlaying = true
            }
            return
        }
        try {
            val player = android.media.MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                isPlaying = false
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            isPlaying = true
        } catch (e: Exception) { }
    }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column {
            Text(
                "Вы записали голосовое сообщение",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Отменить", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(colorTheme.primary.copy(alpha = 0.15f))
                        .clickable { togglePlayback() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Прослушать",
                        tint = colorTheme.primary, modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    AudioUtils.formatDuration(durationMs),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colorTheme.primary)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ScheduleMessageDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember {
        val now = System.currentTimeMillis()
        listOf(
            "Через 1 час" to now + 60 * 60 * 1000L,
            "Через 3 часа" to now + 3 * 60 * 60 * 1000L,
            "Завтра утром (9:00)" to nextDayAt(9, 0),
            "Завтра вечером (19:00)" to nextDayAt(19, 0)
        )
    }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Запланировать отправку") },
        text = {
            Column {
                Text(
                    "Сообщение будет автоматически отправлено в выбранное время.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                presets.forEach { (label, millis) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onConfirm(millis) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { showDateTimePicker(context) { picked -> onConfirm(picked) } }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Выбрать дату и время", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun nextDayAt(hour: Int, minute: Int): Long {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
    cal.set(java.util.Calendar.MINUTE, minute)
    cal.set(java.util.Calendar.SECOND, 0)
    return cal.timeInMillis
}

private fun showDateTimePicker(context: android.content.Context, onPicked: (Long) -> Unit) {
    val cal = java.util.Calendar.getInstance()
    android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            android.app.TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = java.util.Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                    }
                    onPicked(picked.timeInMillis)
                },
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true
            ).show()
        },
        cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)
    ).show()
}

@Composable
private fun ScheduledMessagesDialog(
    items: List<app.yodo.messenger.domain.model.ScheduledMessage>,
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отложенные сообщения") },
        text = {
            if (items.isEmpty()) {
                Text("Нет отложенных сообщений", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.text.ifBlank { "📷 Фото" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatScheduledTime(item.scheduledFor),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { onCancel(item.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Отменить отправку")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

private fun formatScheduledTime(millis: Long): String {
    return SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(millis))
}

private val DISAPPEARING_TTL_OPTIONS: List<Pair<String, Long?>> = listOf(
    "Выключено" to null,
    "30 секунд" to 30L,
    "1 минута" to 60L,
    "1 час" to 60 * 60L,
    "1 день" to 24 * 60 * 60L,
    "1 неделя" to 7 * 24 * 60 * 60L,
    "1 месяц" to 30 * 24 * 60 * 60L
)

private fun disappearingTtlLabel(ttlSeconds: Long?): String {
    DISAPPEARING_TTL_OPTIONS.firstOrNull { it.second == ttlSeconds }?.let { return it.first }
    if (ttlSeconds == null) return "Выключено"
    return formatCustomTtlLabel(ttlSeconds)
}

private fun formatCustomTtlLabel(ttlSeconds: Long): String {
    val days = ttlSeconds / (24 * 60 * 60L)
    val hours = ttlSeconds / (60 * 60L)
    val minutes = ttlSeconds / 60L
    return when {
        ttlSeconds % (24 * 60 * 60L) == 0L && days > 0 -> pluralizeRu(days, "день", "дня", "дней")
        ttlSeconds % (60 * 60L) == 0L && hours > 0 -> pluralizeRu(hours, "час", "часа", "часов")
        ttlSeconds % 60L == 0L && minutes > 0 -> pluralizeRu(minutes, "минута", "минуты", "минут")
        else -> pluralizeRu(ttlSeconds, "секунда", "секунды", "секунд")
    }
}

private fun pluralizeRu(count: Long, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1L -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$count $word"
}

private fun subscriberCountLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "подписчик��в"
        mod10 == 1 -> "подписчик"
        mod10 in 2..4 -> "подписчика"
        else -> "подписчиков"
    }
}

@Composable
private fun DisappearingMessagesDialog(
    currentTtlSeconds: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    perMessage: Boolean = false
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val isCustomSelected = currentTtlSeconds != null && DISAPPEARING_TTL_OPTIONS.none { it.second == currentTtlSeconds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (perMessage) "Таймер для этого сообщения" else "Исчезающие сообщения") },
        text = {
            Column {
                Text(
                    if (perMessage)
                        "Сообщение будет автоматически удалено у всех участников чата через выбранное время после отправки."
                    else
                        "Новые сообщения в этом чате будут по умолчанию автоматически удаляться через вы��ранное время после отправки. Таймер для ��тдельного сообщения можно изменить перед его отправкой (иконка часов рядом с полем ввода).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                DISAPPEARING_TTL_OPTIONS.forEach { (label, ttl) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSelect(ttl) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !isCustomSelected && ttl == currentTtlSeconds, onClick = { onSelect(ttl) })
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { showCustomDialog = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isCustomSelected, onClick = { showCustomDialog = true })
                    Text(
                        if (isCustomSelected) "Свой вариант (${disappearingTtlLabel(currentTtlSeconds)})" else "Свой вариант…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
    if (showCustomDialog) {
        CustomDisappearingDurationDialog(
            onConfirm = { ttl -> showCustomDialog = false; onSelect(ttl) },
            onDismiss = { showCustomDialog = false }
        )
    }
}

@Composable
private fun CustomDisappearingDurationDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("1") }
    val units = listOf("секунды" to 1L, "минуты" to 60L, "часы" to 3600L, "дни" to 86400L)
    var selectedUnitIndex by remember { mutableStateOf(1) }
    val amount = amountText.toLongOrNull()
    val isValid = amount != null && amount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой вариант") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) amountText = new },
                    label = { Text("Количество") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                units.forEachIndexed { index, (label, _) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selectedUnitIndex = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedUnitIndex == index, onClick = { selectedUnitIndex = index })
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val seconds = amount!! * units[selectedUnitIndex].second
                    onConfirm(seconds)
                }
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun VoicePlayerBubble(
    messageId: String,
    voiceBase64: String,
    durationMs: Long,
    textColor: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var speed by remember { mutableStateOf(1.0f) }
    val speedOptions = remember { listOf(1.0f, 1.5f, 2.0f, 0.5f) }
    DisposableEffect(messageId) {
        onDispose {
            mediaPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
            mediaPlayer = null
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null) {
                try {
                    currentPositionMs = mp.currentPosition
                    if (!mp.isPlaying) {
                        isPlaying = false
                        currentPositionMs = 0
                        mp.seekTo(0)
                    }
                } catch (e: Exception) { isPlaying = false }
            }
            kotlinx.coroutines.delay(200L)
        }
    }
    fun applySpeed(player: android.media.MediaPlayer, newSpeed: Float, wasPlaying: Boolean) {
        try {
            player.playbackParams = player.playbackParams.setSpeed(newSpeed)
            if (wasPlaying && !player.isPlaying) player.start()
        } catch (e: Exception) { }
    }
    fun togglePlayback() {
        val existing = mediaPlayer
        if (existing != null) {
            if (isPlaying) {
                existing.pause()
                isPlaying = false
            } else {
                applySpeed(existing, speed, wasPlaying = true)
                if (!existing.isPlaying) existing.start()
                isPlaying = true
            }
            return
        }
        val file = AudioUtils.base64ToTempFile(context, voiceBase64, messageId) ?: return
        try {
            val player = android.media.MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
            }
            player.prepare()
            applySpeed(player, speed, wasPlaying = false)
            player.start()
            mediaPlayer = player
            isPlaying = true
        } catch (e: Exception) { }
    }
    val effectiveDuration = if (durationMs > 0) durationMs.toInt() else 1
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).widthIn(min = 220.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(textColor.copy(alpha = 0.15f))
                .clickable { togglePlayback() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Слушать",
                tint = textColor, modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Slider(
                value = currentPositionMs.coerceIn(0, effectiveDuration).toFloat(),
                valueRange = 0f..effectiveDuration.toFloat(),
                onValueChange = { newValue ->
                    currentPositionMs = newValue.toInt()
                    mediaPlayer?.let { runCatching { it.seekTo(newValue.toInt()) } }
                },
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = textColor, activeTrackColor = textColor, inactiveTrackColor = textColor.copy(alpha = 0.3f)
                ),
                modifier = Modifier.height(24.dp)
            )
            Text(
                AudioUtils.formatDuration(
                    if (isPlaying || currentPositionMs > 0) currentPositionMs.toLong() else durationMs
                ),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.75f)
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(textColor.copy(alpha = 0.15f))
                .clickable {
                    val currentIndex = speedOptions.indexOf(speed).let { if (it < 0) 0 else it }
                    val newSpeed = speedOptions[(currentIndex + 1) % speedOptions.size]
                    speed = newSpeed
                    mediaPlayer?.let { applySpeed(it, newSpeed, wasPlaying = isPlaying) }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "${if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()}x",
                style = MaterialTheme.typography.labelSmall,
                color = textColor, fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatMessageTime(millis: Long): String {
    if (millis == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun formatDateSeparator(millis: Long): String {
    if (millis == 0L) return ""
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val now = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
                a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay(cal, now) -> "Сегодня"
        sameDay(cal, yesterday) -> "Вчера"
        cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) ->
            SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))
        else -> SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(millis))
    }
}

private fun formatLastSeen(millis: Long): String {
    val diffMillis = (System.currentTimeMillis() - millis).coerceAtLeast(0L)
    val diffSeconds = diffMillis / 1_000
    val diffMinutes = diffSeconds / 60
    return when {
        diffSeconds < 10 -> "только что"
        diffSeconds < 60 -> "$diffSeconds сек назад"
        diffMinutes < 60 -> "$diffMinutes мин назад"
        diffMinutes < 24 * 60 -> "${diffMinutes / 60} ч назад"
        diffMinutes < 7 * 24 * 60 -> SimpleDateFormat("EEEE, HH:mm", Locale("ru")).format(Date(millis))
            .replaceFirstChar { it.uppercase() }
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(millis))
    }
}

@Composable
private fun AttachMenuDialog(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickViewOncePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickLocation: () -> Unit,
    onPickPoll: () -> Unit,
    onPasteImage: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Отправить",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                AttachMenuRow(icon = Icons.Filled.Photo, label = "Фото", onClick = onPickPhoto)
                // НОВОЕ (картинки из буфера): вставить изображение из буфера обмена (с подписью).
                AttachMenuRow(icon = Icons.Filled.ContentPaste, label = "Вставить из буфера", onClick = onPasteImage)
                // НОВОЕ (одноразовые медиа): фото, которое получатель сможет открыть
                // полноэкранно только один раз — после этого оно стирается на сервере.
                AttachMenuRow(icon = Icons.Filled.RemoveRedEye, label = "Фото на один просмотр", onClick = onPickViewOncePhoto)
                AttachMenuRow(icon = Icons.Filled.InsertDriveFile, label = "Файл", onClick = onPickFile)
                AttachMenuRow(icon = Icons.Filled.LocationOn, label = "Геопо��иция", onClick = onPickLocation)
                AttachMenuRow(icon = Icons.Filled.Poll, label = "Опрос", onClick = onPickPoll)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * НОВОЕ (картинки из буфера + подпись): превью изображения с полем подписи;
 * отправка происходит только по кнопке «Отправить» (не моментально).
 */
@Composable
private fun ImageCaptionDialog(
    imageBase64: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    val bitmap = remember(imageBase64) { ImageUtils.decodeBase64ToBitmap(imageBase64) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Отправить изображение",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Превью",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Подпись (необязательно)…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSend(caption.trim()) }) { Text("Отправить") }
                }
            }
        }
    }
}

@Composable
private fun AttachMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current
    var centerLat = 55.7558
    var centerLng = 37.6173
    var hasLocatedUser by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    LaunchedEffect(Unit) {
        OsmConfiguration.getInstance().osmdroidTileCache = context.cacheDir
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: fusedClient.lastLocation.await()
            if (location != null) {
                centerLat = location.latitude
                centerLng = location.longitude
                hasLocatedUser = true
                mapViewRef?.controller?.setCenter(GeoPoint(centerLat, centerLng))
            }
        } catch (e: Exception) { }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Отмена")
                    }
                    Text("Отправить геопозицию", style = MaterialTheme.typography.titleMedium)
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(centerLat, centerLng))
                                addMapListener(object : MapListener {
                                    override fun onScroll(event: ScrollEvent?): Boolean {
                                        mapCenter?.let { centerLat = it.latitude; centerLng = it.longitude }
                                        return true
                                    }
                                    override fun onZoom(event: ZoomEvent?): Boolean = false
                                })
                                mapViewRef = this
                            }
                        }
                    )
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Выбранная точка",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).size(40.dp).offset(y = (-20).dp)
                    )
                    if (!hasLocatedUser) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            Text(
                                "Определяем ваше местоположение…",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Text(
                    "Переместите карту, чтобы выбрать точку — она отправится как отметка в центре экрана",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = { onConfirm(centerLat, centerLng) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отправить")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileAttachmentBubble(
    messageId: String,
    fileBase64: String,
    fileName: String,
    sizeBytes: Long,
    textColor: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .padding(8.dp)
            .widthIn(min = 200.dp, max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(textColor.copy(alpha = 0.08f))
            .clickable {
                val file = FileUtils.base64ToTempFile(context, fileBase64, messageId, fileName)
                if (file == null) return@clickable
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(openIntent)
                } catch (e: Exception) {
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(shareIntent, "Открыть файл через")) }
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                FileUtils.extensionLabel(fileName),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                fileName, color = textColor, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                FileUtils.formatSize(sizeBytes),
                color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun PollMessageBubble(
    poll: app.yodo.messenger.domain.model.Poll,
    currentUserId: String?,
    isOwnMessage: Boolean,
    textColor: Color,
    accentColor: Color,
    onVote: (Int) -> Unit,
    onClosePoll: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val isClosed = poll.isEffectivelyClosed(now)
    val totalVotes = poll.totalVotes()
    val votedOptions = currentUserId?.let { poll.votedOptions(it) } ?: emptySet()
    val hasVoted = votedOptions.isNotEmpty()
    val showResults = isClosed || hasVoted
    Column(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 260.dp)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Poll, contentDescription = null, tint = textColor.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                when {
                    poll.isQuiz && isClosed -> "Викторина завершена"
                    poll.isQuiz -> "Викторина"
                    isClosed -> "Опрос завершён"
                    else -> "Опрос"
                },
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            poll.question,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        poll.options.forEachIndexed { index, optionText ->
            val votesForOption = poll.votesFor(index)
            val fraction = if (totalVotes > 0) votesForOption.toFloat() / totalVotes.toFloat() else 0f
            val isSelected = index in votedOptions
            // НОВОЕ (викторина): после показа результатов подсвечиваем правильный вариант
            // зелёным, а ошибочно выбранный пользователем — красным.
            val isCorrect = poll.isCorrectOption(index)
            val quizReveal = poll.isQuiz && showResults
            val correctColor = Color(0xFF2E7D32)
            val wrongColor = Color(0xFFC62828)
            val barColor = when {
                quizReveal && isCorrect -> correctColor
                quizReveal && isSelected && !isCorrect -> wrongColor
                else -> accentColor
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(textColor.copy(alpha = 0.08f))
                    .clickable(enabled = !isClosed) { onVote(index) }
            ) {
                if (showResults) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(barColor.copy(alpha = if (isSelected || isCorrect) 0.35f else 0.18f))
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (quizReveal && isCorrect) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Правильный ответ",
                            tint = correctColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (quizReveal && isSelected && !isCorrect) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Неверный ответ",
                            tint = wrongColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Ваш выбор",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        optionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (showResults) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${(fraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        // НОВОЕ (викторина): итог для проголосовавшего �� пояснение после голосования.
        if (poll.isQuiz && showResults) {
            Spacer(modifier = Modifier.height(6.dp))
            val answeredRight = currentUserId?.let { poll.answeredCorrectly(it) } ?: false
            if (hasVoted) {
                Text(
                    if (answeredRight) "✓ Правильно!" else "✗ Неверно",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (answeredRight) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
            poll.explanation?.takeIf { it.isNotBlank() }?.let { exp ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    exp,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$totalVotes " + when {
                    totalVotes % 10 == 1 && totalVotes % 100 != 11 -> "голос"
                    totalVotes % 10 in 2..4 && totalVotes % 100 !in 12..14 -> "голоса"
                    else -> "голосов"
                } + if (poll.allowMultipleAnswers) " · несколько вариантов" else "",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f)
            )
            if (isOwnMessage && !isClosed) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Завершить",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.clickable { onClosePoll() }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PollCreationDialog(
    advancedPollsEnabled: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onDismiss: () -> Unit,
    onConfirm: (question: String, options: List<String>, isAnonymous: Boolean, allowMultiple: Boolean, closesAtMillis: Long?, isQuiz: Boolean, correctOptionIndex: Int?, explanation: String?) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var isAnonymous by remember { mutableStateOf(true) }
    var allowMultiple by remember { mutableStateOf(false) }
    var closesInHours by remember { mutableStateOf<Int?>(null) }
    // НОВОЕ (викторина): режим викт��рины с ����равильным ответом и пояснением.
    var isQuiz by remember { mutableStateOf(false) }
    var correctIndex by remember { mutableStateOf<Int?>(null) }
    var explanation by remember { mutableStateOf("") }
    val validOptionsCount = options.count { it.isNotBlank() }
    val correctIsValid = correctIndex?.let { it in options.indices && options[it].isNotBlank() } ?: false
    val canSubmit = question.isNotBlank() && validOptionsCount >= 2 && (!isQuiz || correctIsValid)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Poll, contentDescription = null, tint = colorTheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Новый опрос", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Вопрос") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEachIndexed { index, value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                options = options.toMutableList().also { it[index] = newValue }
                            },
                            label = { Text("Вариант ${index + 1}") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        if (options.size > 2) {
                            IconButton(onClick = {
                                options = options.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Удалить вариант")
                            }
                        }
                    }
                }
                if (options.size < 10) {
                    Text(
                        "+ Добавить вариант",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorTheme.primary,
                        modifier = Modifier
                            .clickable { options = options + "" }
                            .padding(vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAnonymous = !isAnonymous }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Анонимный опрос", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Голоса не привязываются к именам в интерфейсе",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorTheme.primary)
                    )
                }
                // НОВОЕ (викторина): переключатель режима викторины и выбор правильного ответа.
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isQuiz = !isQuiz
                            if (isQuiz) allowMultiple = false else correctIndex = null
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Режим викторины", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Отметьте правильный ответ — участники увидят его после голосования",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isQuiz,
                        onCheckedChange = {
                            isQuiz = it
                            if (it) allowMultiple = false else correctIndex = null
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorTheme.primary)
                    )
                }
                if (isQuiz) {
                    Text(
                        "Правильный ответ",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    options.forEachIndexed { index, optionText ->
                        val label = optionText.trim().ifEmpty { "Вариант ${index + 1}" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { correctIndex = index }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = correctIndex == index,
                                onClick = { correctIndex = index }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = explanation,
                        onValueChange = { explanation = it },
                        label = { Text("Пояснение (необязательно)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (advancedPollsEnabled) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { allowMultiple = !allowMultiple }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Несколько вариантов ответа", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Участники смогут выбрать более одного варианта",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowMultiple,
                            onCheckedChange = { allowMultiple = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorTheme.primary)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Автозакрытие голосования",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                    val durationOptions = listOf<Pair<String, Int?>>(
                        "Бессрочно" to null,
                        "1 час" to 1,
                        "24 часа" to 24,
                        "7 дней" to 24 * 7
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durationOptions.forEach { (label, hours) ->
                            val selected = closesInHours == hours
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) colorTheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { closesInHours = hours }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            val closesAtMillis = closesInHours?.let {
                                System.currentTimeMillis() + it.toLong() * 60 * 60 * 1000
                            }
                            // Индекс правильного ответа нужно пересчитать относительно
                            // очищенного списка (без пустых вариантов).
                            val remappedCorrect = if (isQuiz) correctIndex?.let { ci ->
                                if (options.getOrNull(ci)?.isBlank() != false) null
                                else options.take(ci).count { it.isNotBlank() }
                            } else null
                            onConfirm(
                                question.trim(),
                                options.map { it.trim() }.filter { it.isNotEmpty() },
                                isAnonymous,
                                if (advancedPollsEnabled) allowMultiple else false,
                                if (advancedPollsEnabled) closesAtMillis else null,
                                isQuiz,
                                remappedCorrect,
                                if (isQuiz) explanation.trim().ifEmpty { null } else null
                            )
                        }
                    ) {
                        Text("Создать")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationMessageBubble(lat: Double, lng: Double, textColor: Color) {
    var showFullscreen by remember { mutableStateOf(false) }
    if (showFullscreen) {
        FullscreenLocationViewer(lat = lat, lng = lng, onDismiss = { showFullscreen = false })
    }
    Box(
        modifier = Modifier
            .padding(8.dp)
            .width(220.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { showFullscreen = true }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    OsmConfiguration.getInstance().osmdroidTileCache = ctx.cacheDir
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    setOnTouchListener { _, _ -> true }
                    controller.setZoom(14.0)
                    controller.setCenter(GeoPoint(lat, lng))
                    val marker = Marker(this)
                    marker.position = GeoPoint(lat, lng)
                    overlays.add(marker)
                }
            }
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Геопозиция", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FullscreenLocationViewer(lat: Double, lng: Double, onDismiss: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            OsmConfiguration.getInstance().osmdroidTileCache = ctx.cacheDir
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(17.0)
                            controller.setCenter(GeoPoint(lat, lng))
                            val marker = Marker(this)
                            marker.position = GeoPoint(lat, lng)
                            overlays.add(marker)
                        }
                    }
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(50))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть в приложении карт")
                }
            }
        }
    }
}


internal fun chatBackgroundPreviewBrush(
    type: app.yodo.messenger.data.local.ChatBackgroundType
): androidx.compose.ui.graphics.Brush {
    return when (type) {
        app.yodo.messenger.data.local.ChatBackgroundType.DEFAULT ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFFECECEC), Color(0xFFF7F7F7))
            )
        app.yodo.messenger.data.local.ChatBackgroundType.CUSTOM_IMAGE ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Transparent)
            )
        else -> chatBackgroundBrush(type, null)
    }
}

private fun chatBackgroundBrush(
    type: app.yodo.messenger.data.local.ChatBackgroundType,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme?
): androidx.compose.ui.graphics.Brush {
    return when (type) {
        app.yodo.messenger.data.local.ChatBackgroundType.DEFAULT ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    (colorTheme?.primary ?: Color(0xFF667EEA)).copy(alpha = 0.05f),
                    Color.Transparent
                )
            )
        app.yodo.messenger.data.local.ChatBackgroundType.GRADIENT_1 ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
            )
        app.yodo.messenger.data.local.ChatBackgroundType.GRADIENT_2 ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFFF093FB), Color(0xFFF5576C))
            )
        app.yodo.messenger.data.local.ChatBackgroundType.GRADIENT_3 ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
            )
        app.yodo.messenger.data.local.ChatBackgroundType.GRADIENT_4 ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFF43E97B), Color(0xFF38F9D7))
            )
        app.yodo.messenger.data.local.ChatBackgroundType.CUSTOM_IMAGE ->
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Transparent)
            )
    }
}
