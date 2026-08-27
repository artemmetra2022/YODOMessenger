package app.yodo.messenger.features.chats

import app.yodo.messenger.ui.components.OfficialChannelAvatar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.domain.model.SupportFaqData
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.components.swipeToGoBack
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.TelegramColors
import app.yodo.messenger.util.AudioUtils
import app.yodo.messenger.util.ChatImageQuality
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
    val hideKeyboardOnScroll by viewModel.hideKeyboardOnScroll.collectAsState()
    val chatBackgroundType by viewModel.chatBackgroundType.collectAsState()
    val chatBackgroundCustomPath by viewModel.chatBackgroundCustomPath.collectAsState()
    val colorTheme = LocalColorTheme.current
    var inputText by remember { mutableStateOf("") }
    // НОВОЕ (система жало��): сообщени��, на которое сейчас ��ткрыт диалог "Пожаловаться".
    var reportTargetMessage by remember { mutableStateOf<app.yodo.messenger.domain.model.Message?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val selectedMessageIds = remember { mutableStateOf<Set<String>>(emptySet()) }
    var infoMessage by remember { mutableStateOf<app.yodo.messenger.domain.model.Message?>(null) }
    val quickReaction = viewModel.quickReaction.collectAsState().value
    val isSelectionMode = selectedMessageIds.value.isNotEmpty()
    var showScheduledList by remember { mutableStateOf(false) }
    // НОВОЕ: диалог «Быстрые ответы» (шаблоны из «Фишки и инструменты»).
    var showQuickReplies by remember { mutableStateOf(false) }
    val toolsPrefs = remember { context.getSharedPreferences("yodo_tools", Context.MODE_PRIVATE) }

    // НОВОЕ (картинки из буфера + подпись): base64 картинки, ожидающей подписью
    // и подтверждения отправки (не моментальная отправка).
    var pendingCaptionImageBase64 by remember { mutableStateOf<String?>(null) }
    var pendingCaptionImagesBase64 by remember { mutableStateOf<List<String>>(emptyList()) }
    var isProcessingMedia by remember { mutableStateOf(false) }

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
    var selectedImageQuality by remember { mutableStateOf(ChatImageQuality.HIGH) }
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
                isProcessingMedia = true
                val base64 = withContext(Dispatchers.Default) {
                    ImageUtils.compressChatImageToBase64(context, it, selectedImageQuality)
                }
                isProcessingMedia = false
                if (base64 != null) {
                    if (asViewOnce) viewModel.sendImage(base64, isViewOnce = true)
                    else pendingCaptionImageBase64 = base64
                } else snackbarHostState.showSnackbar("Не удалось обработать фото")
            }
        }
    }

    // НОВОЕ (несколько фото): множественный выбор фото. Все выбранные фото
    // сжимаются и отправляются ОДНИМ сообщением-альбомом (не подряд отдельными).
    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isProcessingMedia = true
            val encoded = withContext(Dispatchers.Default) {
                uris.mapNotNull { ImageUtils.compressChatImageToBase64(context, it, selectedImageQuality) }
            }
            isProcessingMedia = false
            when {
                encoded.isEmpty() -> snackbarHostState.showSnackbar("Не удалось обработать фото")
                encoded.size == 1 -> pendingCaptionImageBase64 = encoded.first()
                else -> pendingCaptionImagesBase64 = encoded
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isProcessingMedia = true
                val result = withContext(Dispatchers.Default) {
                    FileUtils.prepareFileForSending(context, it)
                }
                isProcessingMedia = false
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

    // НОВОЕ (картинки из буф����ра): читаем изображение из системного буфера обмена.
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
                ImageUtils.compressChatImageToBase64(context, uri, selectedImageQuality)
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
    // НОВОЕ (лента новостей): выбранная тема новости для следующего поста официального канала.
    var selectedNewsTopic by remember { mutableStateOf<String?>(null) }

    fun trySend() {
        if (inputText.isNotBlank()) {
            // НОВОЕ (лента новостей): в официальном канале админ может указать тему новости (плитку).
            val outgoing = if (uiState.isOfficialChannel && uiState.isAdmin)
                app.yodo.messenger.domain.model.NewsTopic.encode(selectedNewsTopic, inputText)
            else inputText
            viewModel.sendMessage(
                outgoing,
                explicitTtlSeconds = pendingMessageTtlSeconds,
                hasExplicitTtl = pendingMessageTtlExplicitlySet,
                silent = (uiState.chatType == "CHANNEL") && channelSilentMode
            )
            inputText = ""
            selectedNewsTopic = null
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
    val selectedMessages = displayedMessages.filter { it.id in selectedMessageIds.value }
    val isChannel = uiState.chatType == "CHANNEL"
    // НОВОЕ (FAQ-бот поддержки): бот показывается только пользователю, не оператору,
    // который отвечает в этом же чате из своего аккаунта поддержки.
    val isSupportAdmin = viewModel.isSupportAdmin

    val isDarkTheme = isSystemInDarkTheme()
    val telegramBackground = if (isDarkTheme) TelegramColors.darkBackground else TelegramColors.lightBackground
    val telegramBar = if (isDarkTheme) Color(0xFF17212B) else Color.White

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedMessageIds.value = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Отменить выбор")
                        }
                        Text("Выбрано: ${selectedMessages.size}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { viewModel.deleteMessages(selectedMessages); selectedMessageIds.value = emptySet() },
                            enabled = selectedMessages.any { it.senderId == viewModel.currentUserId }
                        ) { Icon(Icons.Filled.Delete, contentDescription = "Удалить выбранные") }
                        IconButton(
                            onClick = {
                                selectedMessages.forEach { viewModel.togglePinMessage(it.id) }
                                selectedMessageIds.value = emptySet()
                            }
                        ) { Icon(Icons.Filled.PushPin, contentDescription = "Закрепить выбранные") }
                    }
                }

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
                                    } else if (uiState.isOfficialChannel) {
                                        OfficialChannelAvatar(
                                            size = 36.dp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    } else {
                                        UserAvatar(
                                            displayName = uiState.chatTitle,
                                            photoUrl = null,
                                            avatarBase64 = null,
                                            size = 36.dp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
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
                                    // НОВОЕ (поделиться контактом аб��нента): делимся QR-кодом контакта ��обеседника, а не своего.
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
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = telegramBar,
                        scrolledContainerColor = telegramBar,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (uiState.disappearingTtlSeconds != null) {
                    // ИСПРАВЛЕНО (AH): индикатор таймера в прямоугольнике со скруглёнными углами.
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                            Text(pinned.text.ifBlank { "📷 Фо��о" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    // ИСПРАВЛЕНО (AH): индикатор отложенных сообщений в прямоугольнике со скруглёнными углами.
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                // НОВОЕ (FAQ-бот поддержки): в чате поддержки вместо обычного поля ввода
                // показываем кнопочный FAQ (разделы -> вопросы -> ответ), пока пользователь
                // сам не свернёт его, чтобы написать оператору вручную.
                val isSupportChat = uiState.chatType == "SUPPORT" && !isSupportAdmin
                if (isSupportChat && uiState.supportFaqScreen != null) {
                    SupportFaqPanel(
                        screen = uiState.supportFaqScreen!!,
                        colorTheme = colorTheme,
                        onSelectSection = { viewModel.openFaqSection(it) },
                        onSelectQuestion = { sectionId, questionId ->
                            viewModel.openFaqQuestion(sectionId, questionId)
                        },
                        onBackToSections = { viewModel.backToFaqSections() },
                        onBackToQuestions = { viewModel.backToFaqQuestions(it) },
                        onContactOperator = { viewModel.closeSupportFaq() },
                        onCollapse = { viewModel.closeSupportFaq() }
                    )
                } else if (isChannel && !uiState.isAdmin) {
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
                    // ПЕРЕНЕСЕНО (X): тумблер обычная/тихая публикация теперь в долгом нажатии кнопки отправки (см. MessageInputBar).
                    // НОВОЕ (лента новостей): выбор темы новости (плитки) — только админу официального канала.
                    if (isChannel && uiState.isAdmin && uiState.isOfficialChannel) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Тема:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            app.yodo.messenger.domain.model.NewsTopic.TOPICS.forEach { topic ->
                                val selected = selectedNewsTopic == topic
                                Text(
                                    topic,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selected) Color.White else colorTheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) colorTheme.primary else colorTheme.primary.copy(alpha = 0.12f))
                                        .clickable { selectedNewsTopic = if (selected) null else topic }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
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
                        // НОВОЕ (реальная блокировка): вместо поля ввода показываем плашку,
                        // если собеседник заблокировал меня или я заблокировал его.
                        uiState.otherBlockedMe || uiState.iBlockedOther -> {
                            BlockedInputBanner(
                                theyBlockedMe = uiState.otherBlockedMe,
                                colorTheme = colorTheme,
                                onUnblock = { viewModel.setBlocked(false) }
                            )
                        }
                        else -> {
                            Column {
                                // НОВОЕ (FAQ-бот поддержки): если пользователь свернул FAQ и пишет
                                // оператору вручную, оставляем узкую плашку, чтобы можно было
                                // вернуться к разделам вопросов в один тап.
                                if (isSupportChat) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.openSupportFaqMenu() }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.HelpOutline,
                                            contentDescription = null,
                                            tint = colorTheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Открыть список вопросов",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorTheme.primary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
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
                                    placeholder = when {
                                        isChannel && uiState.isAdmin -> "Вы админ, вам можно писать"
                                        isSupportChat -> "Опишите вопрос оператору..."
                                        else -> "Сообщение..."
                                    },
                                    pendingTtlSeconds = pendingMessageTtlSeconds,
                                    isTtlExplicitlySet = pendingMessageTtlExplicitlySet,
                                    onTtlIconClick = { showPerMessageTtlDialog = true },
                                    onScheduleClick = { if (inputText.isNotBlank()) showScheduleDialog = true },
                                    onQuickReplyClick = { showQuickReplies = true },
                                    isChannel = isChannel,
                                    silentMode = channelSilentMode,
                                    onToggleSilent = { channelSilentMode = !channelSilentMode }
                                )
                            }
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
                    if (showQuickReplies) {
                        QuickRepliesDialog(
                            templates = app.yodo.messenger.util.loadPrefsList(toolsPrefs, "templates"),
                            onSelect = { template ->
                                inputText = template
                                viewModel.onInputTextChanged(template)
                                showQuickReplies = false
                            },
                            onDismiss = { showQuickReplies = false }
                        )
                    }
                    if (showAttachMenu) {
                        AttachMenuDialog(
                            selectedImageQuality = selectedImageQuality,
                            onImageQualitySelected = { selectedImageQuality = it },
                            onDismiss = { showAttachMenu = false },
                            onPickPhoto = {
                                showAttachMenu = false
                                pendingImageIsViewOnce = false
                                // Можно выбрать сразу несколько фото — они уйдут одним альбомом.
                                multiImagePicker.launch("image/*")
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
                    pendingCaptionImagesBase64.takeIf { it.isNotEmpty() }?.let { images ->
                        ImageAlbumCaptionDialog(
                            imagesBase64 = images,
                            onDismiss = { pendingCaptionImagesBase64 = emptyList() },
                            onSend = { caption ->
                                viewModel.sendImages(images, caption = caption)
                                pendingCaptionImagesBase64 = emptyList()
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
                        Modifier
                            .background(telegramBackground)
                            .drawBehind {
                                val patternColor = if (isDarkTheme) Color.White.copy(alpha = 0.018f) else Color(0xFF2F8243).copy(alpha = 0.035f)
                                val step = 96.dp.toPx()
                                var x = step / 2f
                                while (x < size.width) {
                                    var y = step / 2f
                                    while (y < size.height) {
                                        drawCircle(patternColor, radius = 1.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                                        y += step
                                    }
                                    x += step
                                }
                            }
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
            if (isProcessingMedia) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Подготавливаем медиа...", modifier = Modifier.padding(start = 12.dp))
                    }
                }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(hideKeyboardOnScroll) {
                            if (hideKeyboardOnScroll) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (kotlin.math.abs(dragAmount) > 2f) {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                }
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    var previousDateLabel: String? = null
                    displayedMessages.forEachIndexed { messageIndex, message ->
                        val dateLabel = formatDateSeparator(message.timestamp)
                        if (dateLabel != previousDateLabel) {
                            item(key = "date_${message.id}") {
                                DateSeparator(dateLabel)
                            }
                            previousDateLabel = dateLabel
                        }
                        item(key = message.id) {
                            val groupPosition = messageGroupPosition(displayedMessages, messageIndex)
                            val spacing = messageItemSpacing(displayedMessages, messageIndex)
                            Spacer(modifier = Modifier.height(spacing.dp))
                            if (isChannel && !uiState.isOfficialChannel) {
                                LaunchedEffect(message.id) { viewModel.registerPostView(message.id) }
                            }
                            SwipeableMessageBubble(
                                message = message,
                                isOwnMessage = message.senderId == viewModel.currentUserId,
                                currentUserId = viewModel.currentUserId,
                                autoDownloadImages = autoDownloadImages,
                                colorTheme = colorTheme,
                                isChannel = isChannel,
                                isOfficialChannel = uiState.isOfficialChannel,
                                isSelectionMode = isSelectionMode,
                                isSelected = message.id in selectedMessageIds.value,
                                groupPosition = groupPosition,
                                showAvatar = !message.senderId.equals(viewModel.currentUserId) &&
                                    (groupPosition == MessageGroupPosition.SINGLE || groupPosition == MessageGroupPosition.LAST),
                                avatarName = uiState.chatTitle,
                                avatarPhotoUrl = uiState.otherUserPhotoUrl,
                                avatarBase64 = uiState.otherUserAvatarBase64,
                                authorName = if (uiState.chatType == "GROUP" || uiState.chatType == "CHANNEL") uiState.authorNames[message.senderId] else null,
                                onSelectionToggle = { selected ->
                                    selectedMessageIds.value = selectedMessageIds.value.toMutableSet().let { ids ->
                                        if (!ids.add(selected.id)) ids.remove(selected.id)
                                        ids
                                    }
                                },
                                onTripleTap = { selected -> viewModel.toggleReaction(selected.id, quickReaction) },
                                onCommentsClick = { onOpenComments(chatId, message.id) },
                                onInfoClick = { infoMessage = it },
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
                                onImageClick = { base64 -> onOpenImageViewer(base64, uiState.chatTitle, message.timestamp) },
                                onViewOnceClick = { msg -> if (msg.imageBase64 != null) viewOnceOverlayMessage = msg },
                                onReplyQuoteClick = { targetMessageId ->
                                    val targetIndex = displayedMessages.indexOfFirst { it.id == targetMessageId }
                                    if (targetIndex >= 0) coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
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

    infoMessage?.let { message ->
        MessageInfoDialog(
            message = message,
            authorName = uiState.authorNames[message.senderId],
            onDismiss = { infoMessage = null }
        )
    }

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
    // ни другого. Как только пользователь открыл экран, сразу п��мечаем сообщение как
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
        // Диалог Compose открывает СВОЁ окно поверх ок��а Activity — FLAG_SECURE, выставленный
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

// НОВОЕ (несколько фото): сетка-коллаж для нескольких фото в одном сообщении.
// Фото показываются по 2 в ряд (при нечётном количестве последнее занимает всю ширин��).
@Composable
private fun ImageAlbumGrid(
    images: List<String>,
    onImageClick: (Int) -> Unit
) {
    // ПЕРЕРАБОТАНО (U): коллаж больше не всегда квадратный и учитывает размеры фото:
    // • 1 фото — в натуральном соотношении сторон;
    // • 2 фото — два столбца;
    // • 3 фото — одно большое + два малых;
    // • 4 и больше — сетка 2x2, на последней плитке показываем "+N".
    val decoded = images.map { base64 ->
        base64 to remember(base64) { ImageUtils.decodeBase64ToBitmap(base64) }
    }
    Column(
        modifier = Modifier
            .padding(4.dp)
            .widthIn(min = 180.dp, max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (decoded.size) {
            1 -> {
                val (_, bmp) = decoded[0]
                if (bmp != null) {
                    val ar = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.6f, 1.9f)
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Фото",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(ar)
                            .clip(RoundedCornerShape(12.dp)).clickable { onImageClick(0) }
                    )
                }
            }
            2 -> {
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    decoded.forEachIndexed { i, (_, bmp) -> AlbumTile(bmp, Modifier.weight(1f).fillMaxHeight()) { onImageClick(i) } }
                }
            }
            3 -> {
                Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AlbumTile(decoded[0].second, Modifier.weight(2f).fillMaxHeight()) { onImageClick(0) }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AlbumTile(decoded[1].second, Modifier.fillMaxWidth().weight(1f)) { onImageClick(1) }
                        AlbumTile(decoded[2].second, Modifier.fillMaxWidth().weight(1f)) { onImageClick(2) }
                    }
                }
            }
            else -> {
                // Показываем первые 4 плитки; на 4-й — "+N", если фото больше.
                val extra = decoded.size - 4
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to 1, 2 to 3).forEach { (a, b) ->
                        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AlbumTile(decoded[a].second, Modifier.weight(1f).fillMaxHeight()) { onImageClick(a) }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                AlbumTile(decoded[b].second, Modifier.fillMaxSize()) { onImageClick(b) }
                                if (b == 3 && extra > 0) {
                                    Box(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .clickable { onImageClick(b) },
                                        contentAlignment = Alignment.Center
                                    ) { Text("+$extra", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(bmp: android.graphics.Bitmap?, modifier: Modifier, onClick: () -> Unit) {
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Фото",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
        )
    } else {
        Spacer(modifier = modifier)
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
    isOfficialChannel: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
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
    onSelectionToggle: (Message) -> Unit = {},
    onTripleTap: (Message) -> Unit = {},
    onInfoClick: (Message) -> Unit = {},
    onSwipeBack: () -> Unit,
    groupPosition: MessageGroupPosition = MessageGroupPosition.SINGLE,
    showAvatar: Boolean = false,
    avatarName: String = "",
    avatarPhotoUrl: String? = null,
    avatarBase64: String? = null,
    authorName: String? = null
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val dpPerCm = 160f / 2.54f
    val replyThresholdDp = 1.5f * dpPerCm
    val forwardZoneEndDp = 2f * dpPerCm
    val maxDragDp = 220f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // НОВОЕ (выделение сообщений): полоса-подсветка на всю ширину строки,
            // цвет берётся из применённой цветовой темы (colorTheme.primary), как в
            // референсе — на манер выделения элементов в списке чатов WhatsApp.
            .background(if (isSelected) colorTheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .padding(vertical = 1.dp)
            .then(
                if (isSelectionMode) Modifier else Modifier.pointerInput(message.id) {
                    val replyThresholdPx = replyThresholdDp * density
                    val forwardZoneEndPx = forwardZoneEndDp * density
                    val maxDragPx = maxDragDp * density
                    detectHorizontalDragGestures(
                        onDragStart = { offsetX = 0f },
                        onDragEnd = {
                            when {
                                offsetX > replyThresholdPx -> onReply()
                                // Свайп влево — переслать любое сообщение (в т.ч. чуж��е в личном
                                // чате). Раньше длинный свайп влево срабатывал как "назад" и мешал
                                // пересылке; теперь за "назад" отвечает только краевой свайп (swipeToGoBack).
                                // view-once сообщения пересылать нельзя.
                                offsetX < -replyThresholdPx && !message.isViewOnce -> onForward()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                        }
                    )
                }
            )
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
                isChannel = isChannel, isOfficialChannel = isOfficialChannel,
                isSelectionMode = isSelectionMode, isSelected = isSelected,
                onCommentsClick = onCommentsClick,
                onReply = onReply, onEdit = onEdit, onDelete = onDelete,
                onForward = onForward, onReact = onReact, onPin = onPin,
                onReport = onReport,
                onSaveToFavorite = onSaveToFavorite,
                onVotePoll = onVotePoll,
                onClosePoll = onClosePoll,
                onImageClick = onImageClick, onViewOnceClick = onViewOnceClick,
                onReplyQuoteClick = onReplyQuoteClick,
                onForwardedSenderClick = onForwardedSenderClick,
                onSelectionToggle = onSelectionToggle,
                onTripleTap = onTripleTap,
                onInfoClick = onInfoClick,
                groupPosition = groupPosition,
                showAvatar = showAvatar,
                avatarName = avatarName,
                avatarPhotoUrl = avatarPhotoUrl,
                avatarBase64 = avatarBase64,
                authorName = authorName
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
    isOfficialChannel: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    groupPosition: MessageGroupPosition = MessageGroupPosition.SINGLE,
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
    onSelectionToggle: (Message) -> Unit = {},
    onTripleTap: (Message) -> Unit = {},
    onInfoClick: (Message) -> Unit = {},
    showAvatar: Boolean = false,
    avatarName: String = "",
    avatarPhotoUrl: String? = null,
    avatarBase64: String? = null,
    authorName: String? = null
) {
    val bubbleColor = if (isOwnMessage) {
        if (isSystemInDarkTheme()) TelegramColors.darkOutgoing else TelegramColors.lightOutgoing
    } else {
        if (isSystemInDarkTheme()) TelegramColors.darkIncoming else TelegramColors.lightIncoming
    }
    val textColor = if (isSystemInDarkTheme()) Color.White else Color(0xFF17212B)
    val timeColor = if (isOwnMessage) {
        if (isSystemInDarkTheme()) TelegramColors.darkOutgoingTime else TelegramColors.lightOutgoingTime
    } else {
        if (isSystemInDarkTheme()) TelegramColors.darkIncomingTime else TelegramColors.lightIncomingTime
    }
    val alignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = telegramBubbleShape(isOwnMessage, groupPosition)
    val clipboardManager = LocalClipboardManager.current
    val sendAppearance = remember(message.id) { Animatable(if (isOwnMessage) 0.86f else 1f) }
    LaunchedEffect(message.id) {
        if (isOwnMessage) sendAppearance.animateTo(1f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f))
    }
    var showMenu by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            kotlinx.coroutines.delay(450)
            tapCount = 0
        }
    }
    var showReactionPicker by remember { mutableStateOf(false) }
    var revealImage by remember { mutableStateOf(autoDownloadImages) }
    // НОВОЕ (копирование фрагмента): отдельный режим выделения текста сообщения,
    // открывается кнопкой "Копировать фрагмент" из контекстного меню.
    var showTextSelectionDialog by remember { mutableStateOf(false) }
    if (message.isDeleted) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
            Text("Сообщение удалено", style = MaterialTheme.typography.bodyMedium, color = Color.Gray, modifier = Modifier.padding(8.dp))
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // НОВОЕ (выделение сообщений): чекбокс режима выбора слева от строки сообщения,
        // цвет галочки/рамки — colorTheme.primary (меняется вместе с применённой темой).
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionToggle(message) },
                colors = CheckboxDefaults.colors(
                    checkedColor = colorTheme.primary,
                    uncheckedColor = colorTheme.primary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.padding(start = 4.dp, end = 2.dp)
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = alignment) {
        Row(
            modifier = Modifier.graphicsLayer {
                scaleX = sendAppearance.value
                scaleY = sendAppearance.value
                alpha = sendAppearance.value
            },
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isOwnMessage) {
                if (showAvatar) {
                    UserAvatar(
                        displayName = avatarName,
                        photoUrl = avatarPhotoUrl,
                        avatarBase64 = avatarBase64,
                        size = 40.dp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(46.dp))
                }
            }
            Column(horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start) {
            if (message.isPinned) {
                Row(modifier = Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Закреплено", tint = colorTheme.primary, modifier = Modifier.size(12.dp))
                    Text("Закреплено", style = MaterialTheme.typography.labelSmall, color = colorTheme.primary)
                }
            }
            BoxWithConstraints {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxWidth * 0.78f)
                        .shadow(
                            elevation = 1.dp,
                            shape = bubbleShape,
                            ambientColor = Color.Black.copy(alpha = 0.06f),
                            spotColor = Color.Black.copy(alpha = 0.06f)
                        )
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .then(if (groupPosition == MessageGroupPosition.SINGLE || groupPosition == MessageGroupPosition.LAST) {
                            Modifier.drawBehind { drawTelegramTail(bubbleColor, isOwnMessage) }
                        } else Modifier)
                        .pointerInput(message.id, isSelectionMode) {
                            detectTapGestures(
                                onLongPress = { if (!isSelectionMode) showMenu = true },
                                onTap = {
                                    // НОВОЕ (выделение сообщений): если уже включён режим
                                    // множественного выбора (есть хотя бы одно выделенное
                                    // сообщение), обычный тап по любому сообщению добавляет
                                    // или снимает его из выделения — как в WhatsApp/Telegram.
                                    if (isSelectionMode) {
                                        onSelectionToggle(message)
                                    } else {
                                        tapCount += 1
                                        if (tapCount == 3) {
                                            onTripleTap(message)
                                            tapCount = 0
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    if (!authorName.isNullOrBlank() && !isOwnMessage) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = stableAuthorColor(message.senderId),
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp)
                        )
                    }
                    message.forwardedFromSenderName?.let {
                        val senderId = message.forwardedFromSenderId
                        Row(
                            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                displayName = it,
                                photoUrl = message.forwardedFromSenderPhotoUrl,
                                avatarBase64 = message.forwardedFromSenderAvatarBase64,
                                size = 24.dp,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                "Переслано от $it", style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.75f), fontWeight = FontWeight.Bold,
                                modifier = Modifier.then(
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
                    }
                    message.replyToText?.let { replyText ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .background(textColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .drawBehind {
                                    drawRect(
                                        color = if (message.replyToSenderName == "Вы") TelegramColors.lightOutgoingLink else TelegramColors.lightIncomingLink,
                                        size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)
                                    )
                                }
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
                    } else if (message.imagesBase64.isNotEmpty()) {
                        // НОВОЕ (несколько фото): альбом — несколько фото сеткой в одном пузыре.
                        if (revealImage) {
                            // НОВОЕ (V): открываем альбом с возможностью листать, начиная с выбранного фото.
                            ImageAlbumGrid(images = message.imagesBase64, onImageClick = { idx ->
                                ImageViewerHolder.images = message.imagesBase64
                                ImageViewerHolder.initialIndex = idx
                                onImageClick(message.imagesBase64[idx])
                            })
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textColor.copy(alpha = 0.12f))
                                    .clickable { revealImage = true },
                                contentAlignment = Alignment.Center
                            ) { Text("Тап, чтобы загрузить фото (${message.imagesBase64.size})", color = textColor) }
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
                                ) { Text("Тап, чтобы загру��ить фото", color = textColor) }
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
                        // НОВОЕ (лента новостей): в оф.канале показываем плитку темы, тело новости и кнопку «на весь экран».
                        val (newsTopic, newsBody) = if (isOfficialChannel)
                            app.yodo.messenger.domain.model.NewsTopic.decode(message.text)
                        else null to message.text
                        var showNewsFullscreen by remember(message.id) { mutableStateOf(false) }
                        if (isOfficialChannel && newsTopic != null) {
                            Text(
                                newsTopic,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorTheme.primary)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        // НОВОЕ (усечение длинных постов): критерий длины общий для всех чатов —
                        // 220 симв. ИЛИ 4+ переноса строки. Работает в личных чатах, группах и каналах.
                        val isLongPost = newsBody.length >= 220 || newsBody.count { it == '\n' } >= 4
                        val displayBody = if (isLongPost) truncatePostPreview(newsBody) else newsBody
                        // ИЗМЕНЕНО (копирование фрагмента): текст сообщения больше не оборачиваем
                        // в SelectionContainer — раньше это включало выделение текста по долгому
                        // нажатию и конфликтовало с контекстным меню (тоже открывающимся по
                        // долгому нажатию). Теперь долгое нажатие всегда открывает меню, а режим
                        // выделения текста запускается отдельно — кнопкой "Копировать фрагмент"
                        // в этом меню (см. showTextSelectionDialog ниже).
                        run {
                            val inlineText = buildAnnotatedString {
                                append(displayBody)
                                append("  ")
                                pushStyle(SpanStyle(color = timeColor, fontSize = 11.sp))
                                append(formatMessageTime(message.timestamp))
                                if (isOwnMessage) {
                                    append("  ")
                                    append(if (message.status == MessageStatus.READ) "✓✓" else "✓")
                                }
                                pop()
                            }
                            Text(
                                text = inlineText,
                                color = textColor,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp),
                                modifier = Modifier.padding(horizontal = 12.dp)
                                    .padding(top = if (message.replyToText != null || message.imageBase64 != null) 4.dp else 8.dp)
                            )
                        }
                        if (!isLongPost) {
                            LinkPreviewSection(
                                messageText = newsBody,
                                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp)
                            )
                        }
                        // ИСПРАВЛЕНО (AL): кнопка «Открыть на весь экран» показывается только у длинных
                        // постов. У короткого сообщения она была бессмысленной и только мешала.
                        if (isLongPost) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showNewsFullscreen = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = "На весь экран", tint = colorTheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Открыть полностью", style = MaterialTheme.typography.labelMedium, color = colorTheme.primary)
                            }
                        }
                        if (showNewsFullscreen) {
                            // Для официального канала YodoMessenger оставляем прежний диалог новостей,
                            // для всех остальных чатов — новый универсальный PostFullscreenDialog.
                            if (isOfficialChannel) {
                                NewsFullscreenDialog(
                                    topic = newsTopic,
                                    body = newsBody,
                                    timestamp = message.timestamp,
                                    colorTheme = colorTheme,
                                    onDismiss = { showNewsFullscreen = false }
                                )
                            } else {
                                PostFullscreenDialog(
                                    body = newsBody,
                                    timestamp = message.timestamp,
                                    colorTheme = colorTheme,
                                    clipboardManager = clipboardManager,
                                    onDismiss = { showNewsFullscreen = false }
                                )
                            }
                        }
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
                        if (message.text.isBlank()) {
                            Text(
                                formatMessageTime(message.timestamp),
                                color = timeColor,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        // НОВОЕ: число просмотров канала теперь рядом со временем.
                        if (isChannel && !isOfficialChannel) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Visibility, contentDescription = "Просмотры",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp).padding(end = 3.dp)
                            )
                            Text("${message.viewCount}", color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        }
                        if (isOwnMessage && message.text.isBlank()) {
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
            }
        }
        }
        if (showReactionPicker && !isOfficialChannel) {
            Surface(
                modifier = Modifier.align(alignment),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp
            ) {
                ReactionPickerPanel(
                    reactions = QUICK_REACTIONS,
                    selectedReactions = message.reactions.filterValues { currentUserId in it }.keys,
                    onReactionSelected = { emoji ->
                        onReact(emoji)
                        showReactionPicker = false
                    },
                    onDismiss = { showReactionPicker = false }
                )
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!isOfficialChannel) {
                        DropdownMenuItem(
                            text = { Text("Добавить реакцию") },
                            leadingIcon = { Icon(Icons.Filled.EmojiEmotions, contentDescription = null) },
                            onClick = { showMenu = false; showReactionPicker = true }
                        )
                    }
                    if (!isOfficialChannel) {
                        DropdownMenuItem(
                            text = { Text("Выбрать") },
                            leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                            onClick = { showMenu = false; onSelectionToggle(message) }
                        )
                    }
                    if (!isOfficialChannel) {
                        DropdownMenuItem(
                            text = { Text("Информация о сообщении") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { showMenu = false; onInfoClick(message) }
                        )
                    }
                    DropdownMenuItem(text = { Text("Ответить") },

 leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) }, onClick = { showMenu = false; onReply() })
                    if (isChannel && !isOfficialChannel) {
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
                        // НОВОЕ (копирование фрагмента): открывает отдельный режим выделения
                        // части текста сообщения (см. TextSelectionDialog ниже).
                        DropdownMenuItem(
                            text = { Text("Копировать фрагмент") },
                            leadingIcon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
                            onClick = { showMenu = false; showTextSelectionDialog = true }
                        )
                    }
                    // НОВОЕ (одноразовые медиа): фото "на один просмотр" нельзя пересылать.
                    if (!message.isViewOnce) {
                        DropdownMenuItem(text = { Text("Переслать") }, leadingIcon = { Icon(Icons.Filled.Forward, contentDescription = null) }, onClick = { showMenu = false; onForward() })
                    }
                    if (isOwnMessage) {
                        DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }, onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Удалит��") }, leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) }, onClick = { showMenu = false; onDelete() })
                    } else {
                        // НОВОЕ (система жалоб, п.5 ТЗ): жалоба на чужое сообщение.
                        DropdownMenuItem(
                            text = { Text("Пожаловаться") },
                            leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                            onClick = { showMenu = false; onReport() }
                        )
                    }
                }

        // НОВОЕ (копирование фрагмента): отдельный диалог с режимом выделения текста —
        // открывается только по кнопке "Копировать фрагмент" из меню, не по долгому нажатию.
        if (showTextSelectionDialog) {
            TextSelectionDialog(
                text = message.text,
                clipboardManager = clipboardManager,
                onDismiss = { showTextSelectionDialog = false }
            )
        }

            // ═══════════════ ЭТАП 14: АНИМИРОВАННЫЕ РЕАКЦИИ ═══════════════
            if (message.reactions.isNotEmpty() && !isOfficialChannel) {
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
                                    .border(
                                        width = if (reactedByMe) 1.dp else 0.dp,
                                        color = if (reactedByMe) colorTheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
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

            if (isChannel && !isOfficialChannel) {
                // ИСПРАВЛЕНО (кнопка комментариев и текст одной ширины): раньше кнопка
                // оборачивалась по содержимому и была узкой, текст переносился. Теперь кнопка
                // занимает ту же максимальную ширину, что и пузырь сообщения (280dp),
                // а текст не сжимает��я (в одну строку, по центру).
                // ИСПРАВЛЕНО (AI): без fillMaxWidth — кнопка по ширине содержимого,
                // чтобы не выпирать за короткое сообщение (верхний предел 280dp).
                Row(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onCommentsClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment, contentDescription = null,
                        tint = colorTheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Текст кнопки такого же размера, что и текст сообщения, без сжатия.
                    Text(
                        if (message.commentsCount > 0) "Комментарии · ${message.commentsCount}" else "Комментировать",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colorTheme.primary,
                        maxLines = 1
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

// НОВОЕ (усечение длинных постов): обрезка текста для превью в списке сообщений.
// Отрезаем по тому же критерию (220 симв. / 4 переноса) и добавляем "…".
private fun truncatePostPreview(text: String): String {
    val byNewlines = text.lineSequence().take(4).joinToString("\n")
    val byLength = if (byNewlines.length > 220) byNewlines.take(220) else byNewlines
    val trimmed = byLength.trimEnd()
    return if (trimmed.length < text.length) "$trimmed…" else trimmed
}

// НОВОЕ (усечение длинных постов): универсальный полноэкранный просмотр для личных чатов и
// обычных (не официальных) каналов/групп. Поддерживает выделение текста и копирование целиком.
@Composable
private fun PostFullscreenDialog(
    body: String,
    timestamp: Long,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onDismiss: () -> Unit
) {
    var justCopied by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = null, tint = colorTheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Пост целиком",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // НОВОЕ: кнопка «Скопировать» — копирует весь пост в буфер обмена.
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(body))
                    justCopied = true
                }) {
                    Icon(
                        if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Скопировать",
                        tint = colorTheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (justCopied) "Скопировано" else "Скопировать", color = colorTheme.primary)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // НОВОЕ: выделение произвольной части текста для копирования отрывка.
                SelectionContainer {
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatMessageTime(timestamp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // НОВОЕ: счётчик символов/слов поста — удобно для длинных текстов.
                    Text(
                        "${body.length} симв. · ${body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size} слов",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// НОВОЕ (копирование фрагмента): отдельный полноэкранный диалог "режима выделения" для
// сообщений в обычном чате (не только длинных постов). Открывается только по кнопке
// "Копировать фрагмент" из контекстного меню сообщения — долгое нажатие на сообщении
// теперь всегда открывает меню, а не запускает выделение текста напрямую. Внутри диалога
// текст обёрнут в SelectionContainer, поэтому стандартное выделение (потяг за маркеры,
// системная плашка "Копировать") работает как обычно, плюс дублирующая кнопка "Копировать
// выделенное" в шапке — на случай, если системная плашка не появилась (некоторые прошивки).
@Composable
private fun TextSelectionDialog(
    text: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onDismiss: () -> Unit
) {
    var justCopied by remember { mutableStateOf(false) }
    // Текущее выделение внутри SelectionContainer — обновляется автоматически при
    // перетаскивании маркеров выделения. Пусто, пока пользователь ничего не выделил.
    val selection = remember { mutableStateOf<androidx.compose.foundation.text.selection.Selection?>(null) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Копирование отрывка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") }
            }
            HorizontalDivider()
            Text(
                "Выделите часть текста — потяните за маркеры, затем нажмите «Копировать выделенное» или используйте системную плашку.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer(
                    selection = selection.value,
                    onSelectionChange = { selection.value = it }
                ) {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSelection = selection.value != null
                Button(
                    enabled = hasSelection,
                    onClick = {
                        val sel = selection.value ?: return@Button
                        val start = minOf(sel.start.offset, sel.end.offset)
                        val end = maxOf(sel.start.offset, sel.end.offset)
                        val fragment = text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
                        if (fragment.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(fragment))
                            justCopied = true
                        }
                    }
                ) {
                    Icon(
                        if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (justCopied) "Скопировано" else "Копировать выделенное")
                }
            }
        }
    }
}

// НОВОЕ (лента новостей): полноэкранный просмотр новости официального канала.
@Composable
private fun NewsFullscreenDialog(
    topic: String?,
    body: String,
    timestamp: Long,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var justCopied by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Campaign, contentDescription = null, tint = colorTheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Новость YodoMessenger",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // НОВОЕ: кнопка «Скопировать» — копирует весь пост в буфер обмена.
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(body))
                    justCopied = true
                }) {
                    Icon(
                        if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Скопировать",
                        tint = colorTheme.primary
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (topic != null) {
                Text(
                    topic,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorTheme.primary)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            // НОВОЕ: выделение произвольной части текста для копирования отрывка.
            SelectionContainer {
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                formatMessageTime(timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun stableAuthorColor(senderId: String): Color {
    val colors = listOf(Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC))
    return colors[(senderId.hashCode() and Int.MAX_VALUE) % colors.size]
}

@Composable
private fun MessageInfoDialog(message: Message, authorName: String?, onDismiss: () -> Unit) {
    val rows = buildList {
        add("ID сообщения" to message.id)
        add("ID чата" to message.chatId)
        add("Автор" to (authorName ?: message.senderId))
        add("Время" to formatMessageTime(message.timestamp))
        add("Статус" to message.status.name)
        if (message.isEdited) add("Изменено" to "Да")
        if (message.isPinned) add("Закреплено" to "Да")
        if (message.replyToMessageId != null) add("Ответ на" to message.replyToMessageId)
        if (message.forwardedFromSenderName != null) add("Переслано от" to message.forwardedFromSenderName)
        if (message.imageBase64 != null || message.imagesBase64.isNotEmpty()) add("Тип" to "Изображение")
        if (message.voiceBase64 != null) add("Тип" to "Голосовое сообщение")
        if (message.fileName != null) add("Файл" to message.fileName)
        if (message.fileSizeBytes != null) add("Размер файла" to FileUtils.formatSize(message.fileSizeBytes))
        if (message.fileMimeType != null) add("MIME" to message.fileMimeType)
        if (message.locationLat != null) add("Геопозиция" to "${message.locationLat}, ${message.locationLng}")
        if (message.commentsCount > 0) add("Комментарии" to message.commentsCount.toString())
        if (message.viewCount > 0) add("Просмотры" to message.viewCount.toString())
        if (message.reactions.isNotEmpty()) add("Реакции" to message.reactions.entries.joinToString { "${it.key}: ${it.value.size}" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Информация о сообщении") },
        text = { LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) { items(rows) { (key, value) -> Text("$key: $value", modifier = Modifier.padding(vertical = 4.dp)) } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
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
private fun ReactionPickerPanel(
    reactions: List<String>,
    selectedReactions: Set<String>,
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Реакции", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            reactions.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (emoji in selectedReactions) LocalColorTheme.current.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (emoji in selectedReactions) 1.dp else 0.dp,
                            color = if (emoji in selectedReactions) LocalColorTheme.current.primary else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onReactionSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
        Text(
            "Нажмите выбранную реакцию ещё раз, чтобы убрать её",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
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

// НОВОЕ (реальная блокировка): плашка вместо поля ввода.
// Если собеседник заблокировал меня �� пишем об этом и не даём писать.
// Если я заблокировал собеседника — предлагаем разблокировать.
@Composable
private fun BlockedInputBanner(
    theyBlockedMe: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onUnblock: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (theyBlockedMe) {
                Text(
                    "Пользователь заблокировал вас",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Вы заблокировали этого пользователя",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onUnblock) { Text("Разблокировать", color = colorTheme.primary) }
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
    onScheduleClick: () -> Unit = {},
    // НОВОЕ: пункт «Быстрые ответы» в меню долгого нажатия на кнопку отправки.
    onQuickReplyClick: () -> Unit = {},
    // ПЕРЕНЕСЕНО (X): тумблер обычная/тихая публикация в долгом нажатии кнопки отправки.
    isChannel: Boolean = false,
    silentMode: Boolean = false,
    onToggleSilent: () -> Unit = {}
) {
    val canSend = !isSending && text.isNotBlank()
    var showEmojiPicker by remember { mutableStateOf(false) }
    Surface(
        color = Color.Transparent,
        shadowElevation = 0.dp
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
                    color = if (isSystemInDarkTheme()) TelegramColors.darkIncoming else Color(0xFFF0F0F0),
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
                        Icon(Icons.Filled.Mic, contentDescription = "Удерживай��е для записи голосового сообщения", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    // ИСПРАВЛЕНО (таймер и запланированные сообщения): убрали отдельные кнопки
                    // рядом с отправкой. Теперь при ЗАЖАТИИ к��опки отправки ����ткрывается меню
                    // с таймером (исчезающие) и запланированной отправкой.
                    var showSendOptions by remember { mutableStateOf(false) }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (canSend) colorTheme.primary else colorTheme.primary.copy(alpha = 0.3f))
                                .combinedClickable(
                                    enabled = canSend,
                                    onClick = onSendClick,
                                    onLongClick = { showSendOptions = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSending) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить (зажмите для таймера и планирования)", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showSendOptions,
                            onDismissRequest = { showSendOptions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isTtlExplicitlySet) "Таймер: ${disappearingTtlLabel(pendingTtlSeconds)}" else "Таймер (исчезающее)") },
                                leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null, tint = if (isTtlExplicitlySet) colorTheme.primary else Color.Gray) },
                                onClick = { showSendOptions = false; onTtlIconClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Запланировать отправку") },
                                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, tint = colorTheme.primary) },
                                onClick = { showSendOptions = false; onScheduleClick() }
                            )
                            // НОВОЕ: быстрые шаблоны ответов (общий список с «Фишки и инструменты»).
                            DropdownMenuItem(
                                text = { Text("Быстрые ответы") },
                                leadingIcon = { Icon(Icons.Filled.Bolt, contentDescription = null, tint = colorTheme.primary) },
                                onClick = { showSendOptions = false; onQuickReplyClick() }
                            )
                            // ПЕРЕНЕ��ЕНО (X): выбор обычной/тихой публикации для каналов.
                            if (isChannel) {
                                DropdownMenuItem(
                                    text = { Text(if (silentMode) "Тихая публикация: без уведомления" else "Обычная публикация") },
                                    leadingIcon = {
                                        Icon(
                                            if (silentMode) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                                            contentDescription = null,
                                            tint = if (silentMode) colorTheme.accent else colorTheme.primary
                                        )
                                    },
                                    onClick = { showSendOptions = false; onToggleSilent() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// НОВОЕ (FAQ-бот поддержки): кнопочная панель бота поддержки — заменяет обычное поле
// ввода в чате поддержки, пока пользователь не свернёт её. Три экрана:
// 1) SectionList — разделы FAQ (плюс "Не нашли нужный вопрос?" внизу);
// 2) QuestionList — список вопросов внутри раздела, с кнопкой "Назад";
// 3) Answer — вопрос и ответ, с кнопками "Назад" и "Связаться с поддержкой".
@Composable
private fun SupportFaqPanel(
    screen: SupportFaqScreen,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onSelectSection: (String) -> Unit,
    onSelectQuestion: (String, String) -> Unit,
    onBackToSections: () -> Unit,
    onBackToQuestions: (String) -> Unit,
    onContactOperator: () -> Unit,
    onCollapse: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
        ) {
            // Заголовок панели
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (screen !is SupportFaqScreen.SectionList) {
                    IconButton(
                        onClick = {
                            when (screen) {
                                is SupportFaqScreen.QuestionList -> onBackToSections()
                                is SupportFaqScreen.Answer -> onBackToQuestions(screen.sectionId)
                                else -> {}
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorTheme.primary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    Icons.Filled.SupportAgent, contentDescription = null,
                    tint = colorTheme.primary, modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when (screen) {
                        is SupportFaqScreen.SectionList -> "Чем помочь?"
                        is SupportFaqScreen.QuestionList ->
                            SupportFaqData.findSection(screen.sectionId)?.title ?: "Вопросы"
                        is SupportFaqScreen.Answer ->
                            SupportFaqData.findSection(screen.sectionId)?.title ?: "Ответ"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                TextButton(onClick = onCollapse) {
                    Text("Свернуть", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (screen) {
                is SupportFaqScreen.SectionList -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(SupportFaqData.sections, key = { it.id }) { section ->
                            FaqRow(
                                emoji = section.emoji,
                                title = section.title,
                                onClick = { onSelectSection(section.id) }
                            )
                        }
                        item(key = "other_section") {
                            FaqRow(
                                emoji = "🙋",
                                title = "Нет нужного вопроса?",
                                titleColor = colorTheme.primary,
                                onClick = { onSelectSection(SupportFaqData.OTHER_SECTION_ID) }
                            )
                        }
                    }
                }
                is SupportFaqScreen.QuestionList -> {
                    if (screen.sectionId == SupportFaqData.OTHER_SECTION_ID) {
                        OtherQuestionBlock(colorTheme = colorTheme, onContactOperator = onContactOperator)
                    } else {
                        val section = SupportFaqData.findSection(screen.sectionId)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            section?.questions?.forEach { q ->
                                item(key = q.id) {
                                    FaqRow(
                                        emoji = null,
                                        title = q.question,
                                        onClick = { onSelectQuestion(screen.sectionId, q.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                is SupportFaqScreen.Answer -> {
                    val question = SupportFaqData.findQuestion(screen.sectionId, screen.questionId)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = question?.question ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = question?.answer ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onBackToQuestions(screen.sectionId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Другие вопросы", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = onContactOperator,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary)
                            ) {
                                Text("Это не помогло", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherQuestionBlock(
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onContactOperator: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(
            text = "Не нашли ответ среди готовых вопросов?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Опишите вашу проблему своими словами — оператор поддержки ответит вам в этом же чате.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onContactOperator,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary)
        ) {
            Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Связаться с поддержкой", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FaqRow(
    emoji: String?,
    title: String,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji != null) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(18.dp)
        )
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
// для ��бхода бага компилятора Compose с выводом типов.
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
            
            // Пульсирующая точка-индикатор записи (исправлено чер��з Animatable)
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
                                Icon(Icons.Filled.Delete, contentDescription = "Отменить отправк��")
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

// НОВОЕ: диалог «Быстрые ответы» — список шаблонов из «Фишки и инструменты»
// (SharedPreferences "yodo_tools", ключ "templates"), с поиском по подстроке.
// Выбор шаблона подставляет текст в поле ввода (без немедленной отправки).
@Composable
private fun QuickRepliesDialog(
    templates: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, templates) {
        if (query.isBlank()) templates
        else templates.filter { it.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Быстрые ответы") },
        text = {
            if (templates.isEmpty()) {
                Text(
                    "У вас пока нет шаблонов.\nДобавьте их: Фишки и инструменты → Быстрые шаблоны ответов.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск по шаблонам") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filtered.isEmpty()) {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            filtered.forEach { template ->
                                Text(
                                    template,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(template) }
                                        .padding(vertical = 10.dp),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
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
        else -> pluralizeRu(ttlSeconds, "��екунда", "секунды", "секунд")
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
        title = { Text(if (perMessage) "Т��ймер для этого сообщения" else "Исчезающие сообщения") },
        text = {
            Column {
                Text(
                    if (perMessage)
                        "Сообщение будет автоматически удалено �� всех участников чата через выбранное время после отправки."
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
            TextButton(onClick = onDismiss) { Text("За��рыть") }
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
    selectedImageQuality: ChatImageQuality,
    onImageQualitySelected: (ChatImageQuality) -> Unit,
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
                AttachMenuRow(icon = Icons.Filled.Photo, label = "Фото (можно несколько)", onClick = onPickPhoto)
                Text(
                    stringResource(R.string.chat_photo_quality),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                ChatImageQuality.entries.forEach { quality ->
                    val label = when (quality) {
                        ChatImageQuality.DATA_SAVER -> stringResource(R.string.chat_photo_quality_data_saver)
                        ChatImageQuality.STANDARD -> stringResource(R.string.chat_photo_quality_standard)
                        ChatImageQuality.HIGH -> stringResource(R.string.chat_photo_quality_high)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onImageQualitySelected(quality) }
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedImageQuality == quality,
                            onClick = { onImageQualitySelected(quality) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // НОВОЕ (картинки ��з буфера): вставить изображение из буфера обмена (с подписью).
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
private fun ImageAlbumCaptionDialog(
    imagesBase64: List<String>,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Отправить альбом (${imagesBase64.size})", style = MaterialTheme.typography.titleMedium)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gridItems(imagesBase64) { base64 ->
                        Image(
                            bitmap = ImageUtils.decodeBase64ToBitmap(base64)?.asImageBitmap() ?: return@gridItems,
                            contentDescription = "Превью",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Подпись (необязательно)…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    maxLines = 3
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
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
                                "Определяем ваш�� местоположение…",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Text(
                    "��ереместите карту, чтобы выбрать точку — она отправится как отметка в центре экран��",
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
                            "Голоса не ��ривяз��ваются к именам в интерфейсе",
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
                            // Индекс правильного ответа нужн�� пересчитать относительно
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
