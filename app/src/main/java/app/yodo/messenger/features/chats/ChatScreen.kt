package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.components.swipeToGoBack
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.util.FileUtils
import app.yodo.messenger.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
// НОВОЕ: вложения "Файл" и "Геопозиция"
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import app.yodo.messenger.util.ChatScreenshotUtils

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
    // НОВОЕ (переработка каналов): тап по шапке канала → профиль канала;
    // кнопка "Комментарии" под постом → экран комментариев.
    onOpenChannelProfile: (String) -> Unit,
    onOpenComments: (chatId: String, messageId: String) -> Unit,
    // НОВОЕ: "Пригласить в канал" в меню чата-канала → экран выбора контактов.
    onInviteToChannel: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    // НОВОЕ (расширенные опросы): включает доп. параметры в диалоге создания опроса.
    val advancedPollsEnabled by viewModel.advancedPollsEnabled.collectAsState()
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

    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
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

    // НОВОЕ: выбор произвольного файла из системного файлового пикера (OpenDocument
    // сохраняет права на чтение Uri дольше, чем GetContent, что удобнее для больших файлов).
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

    // НОВОЕ: меню вложений — фото / файл / геопозиция / опрос (открывается по кнопке "+").
    var showAttachMenu by remember { mutableStateOf(false) }
    // НОВОЕ: диалог выбора точки на карте для отправки геопозиции.
    var showLocationPicker by remember { mutableStateOf(false) }
    // НОВОЕ (расширенные опросы): диалог создания опроса.
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

    // НОВОЕ (переработка исчезающих сообщений "как в Telegram"): таймер выбирается
    // ОТДЕЛЬНО ДЛЯ КАЖДОГО сообщения через иконку часов у поля ввода. null означает
    // "не выбран явно" — тогда используется TTL по умолчанию для чата (disappearingTtlSeconds).
    // При выборе значения в диалоге оно применяется к следующему отправляемому сообщению,
    // после отправки сбрасывается на "по умолчанию" (как и в Telegram — выбор одноразовый).
    var pendingMessageTtlSeconds by remember { mutableStateOf<Long?>(null) }
    var pendingMessageTtlExplicitlySet by remember { mutableStateOf(false) }
    var showPerMessageTtlDialog by remember { mutableStateOf(false) }

    // НОВОЕ: границы области сообщений (без шапки, без поля ввода/клавиатуры) в координатах
    // окна — нужны для скриншота чата ("Сделать скриншот" в меню из 3 точек). Объявлено на
    // уровне всего экрана (а не внутри content-лямбды Scaffold), т.к. читается из meню в topBar.
    var messagesAreaWindowBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    fun trySend() {
        if (inputText.isNotBlank()) {
            viewModel.sendMessage(
                inputText,
                explicitTtlSeconds = pendingMessageTtlSeconds,
                hasExplicitTtl = pendingMessageTtlExplicitlySet
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
        // НОВОЕ (п.6): раньше нижняя панель ввода уходила ПОД клавиатуру (Scaffold сам
        // по себе не учитывает IME-инсеты), теперь весь Scaffold поднимается над
        // клавиатурой при фокусе на поле ввода — как и должно быть.
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
                                // НОВОЕ (переработка каналов): тап по шапке канала открывает его профиль
                                uiState.chatType == "CHANNEL" -> Modifier.clickable { onOpenChannelProfile(chatId) }
                                else -> Modifier
                            }
                            Row(
                                modifier = headerModifier,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isChannel) {
                                    // НОВОЕ (переработка каналов): реальная аватарка канала, если она
                                    // загружена (поле avatarBase64 в документе чата); иначе логотип "Y".
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
                                        // НОВОЕ (индикатор набора текста в группах)
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
                                    // НОВОЕ: приглашение в канал — доступно владельцу/админу канала.
                                    if (isChannel && uiState.isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text("Пригласить в канал") },
                                            leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                                            onClick = { showChatMenu = false; onInviteToChannel(chatId) }
                                        )
                                    }
                                    // НОВОЕ: подписка/отписка от канала — недоступна владельцу
                                    // (он не может отписаться от собственного канала; чтобы
                                    // избавиться от него, ему доступно удаление канала целиком).
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
                                    // НОВОЕ (п.38): пункт меню для настройки исчезающих сообщений
                                    if (!isChannel) {
                                        DropdownMenuItem(
                                            text = { Text("Исчезающие сообщения" + if (uiState.disappearingTtlSeconds != null) " (${disappearingTtlLabel(uiState.disappearingTtlSeconds)})" else "") },
                                            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                                            onClick = { showChatMenu = false; showDisappearingDialog = true }
                                        )
                                    }
                                    DropdownMenuItem(text = { Text("Очистить историю") }, onClick = { showChatMenu = false; viewModel.clearChatHistory() })
                                    DropdownMenuItem(text = { Text("Статистика чата") }, onClick = { showChatMenu = false; onOpenChatStats(chatId) })
                                    DropdownMenuItem(text = { Text("Экспорт чата") }, onClick = { showChatMenu = false; viewModel.exportChat(context) })
                                    // НОВОЕ: "Сделать скриншот" — снимает только область сообщений
                                    // (без клавиатуры/поля ввода), накладывает логотип мессенджера
                                    // в правом верхнем углу и сохраняет результат в галерею.
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
                                                        if (success) "Скриншот сохранён в галерею" else "Не удалось сделать скриншот"
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    // НОВОЕ: у канала-владельца пункт "Удалить чат" заменяется на
                                    // "Удалить канал" — удаляет канал целиком, а не только для себя.
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
                            // НОВОЕ: подтверждение удаления канала владельцем — необратимое действие,
                            // удаляет канал у всех подписчиков, поэтому требуем явного подтверждения.
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
                // НОВОЕ (п.38): пока включены исчезающие сообщения — тонкая подсказка под шапкой,
                // чтобы собеседники не удивлялись пропаже переписки.
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
                // п.34: закреплённое сообщение ВСЕГДА сверху, под шапкой
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
                        // Крестик в верхнем баннере — открепление требует подтверждения.
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
                            title = { Text("Открепить сообщение?") },
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
                // НОВОЕ: если в чате есть отложенные сообщения — компактная плашка-счётчик,
                // тап открывает список отложенных сообщений этого чата.
                var showScheduledList by remember { mutableStateOf(false) }
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
                            "Отложенные сообщения: ${uiState.scheduledMessages.size}",
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
                // п.2: плашка "Сообщение переслано" с окном отмены — над панелью ввода,
                // видна 5 секунд после того, как сюда переслали сообщение.
                if (uiState.justForwardedMessageId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Forward, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
                        // НОВОЕ (п.1): "Сообщение переслано пользователю @.../Имя" — имя кликабельно,
                        // ведёт в профиль получателя (если известен его userId).
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
                        // НОВОЕ (п.1): отсчёт 5,4,3,2,1 до автоскрытия плашки — рядом с кнопкой.
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
                // п.41: логика поля ввода для канала
                if (isChannel && !uiState.isAdmin) {
                    // НОВОЕ (переработка каналов): вместо серой заглушки — заметная CTA-панель:
                    // не подписан — градиентная кнопка «Подписаться на канал»;
                    // подписан / официальный канал — спокойная информационная строка.
                    ChannelBottomBar(
                        isSubscribed = uiState.isSubscribed,
                        isOfficial = uiState.isVerified,
                        colorTheme = colorTheme,
                        onSubscribe = { viewModel.toggleChannelSubscription() }
                    )
                } else {
                    // НОВОЕ (п.2): диалог планирования открывается при долгом нажатии на
                    // кнопку отправки — индивидуально для текста, который сейчас в поле ввода.
                    var showScheduleDialog by remember { mutableStateOf(false) }
                    // НОВОЕ (п.5): состояние записи голосового сообщения.
                    // Запись идёт, только пока кнопка микрофона удерживается нажатой;
                    // после отпускания — не отправляем сразу, а показываем окно предпрослушивания
                    // ("Вы записали голосовое сообщение") с кнопками "Отправить"/"Отменить".
                    var isRecording by remember { mutableStateOf(false) }
                    var recordingElapsedMs by remember { mutableStateOf(0L) }
                    var activeRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
                    var activeRecordingFile by remember { mutableStateOf<java.io.File?>(null) }
                    // Файл + длительность готовой записи, ожидающей решения пользователя (превью).
                    var recordedVoiceFile by remember { mutableStateOf<java.io.File?>(null) }
                    var recordedVoiceDurationMs by remember { mutableStateOf(0L) }
                    // ИСПРАВЛЕНО: если разрешение на микрофон запрашивается впервые, системный
                    // диалог показывается асинхронно — палец пользователя может быть уже отпущен
                    // (onMicPressEnd уже вызван) к моменту, когда придёт granted=true. Без этого
                    // флага запись в таком случае стартовала бы и никогда не останавливалась сама,
                    // потому что "отпускание" уже произошло раньше, чем начало записи.
                    var micPressHeld by remember { mutableStateOf(false) }

                    fun beginRecordingInternal() {
                        val result = app.yodo.messenger.util.AudioUtils.startRecording(context)
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
                                // Палец всё ещё держит кнопку — можно спокойно начинать запись.
                                beginRecordingInternal()
                            }
                            // Если палец уже отпущен — просто ничего не начинаем, чтобы не зависнуть
                            // в состоянии "isRecording=true" без парного onMicPressEnd.
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Нужен доступ к микрофону") }
                        }
                    }

                    // Вызывается при нажатии (press) на кнопку микрофона — начинает запись.
                    fun startVoiceRecording() {
                        micPressHeld = true
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            beginRecordingInternal()
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    fun discardRecordingInternal() {
                        val recorder = activeRecorder
                        val file = activeRecordingFile
                        if (recorder != null && file != null) {
                            app.yodo.messenger.util.AudioUtils.cancelRecording(recorder, file)
                        }
                        activeRecorder = null
                        activeRecordingFile = null
                        isRecording = false
                        recordingElapsedMs = 0L
                    }

                    // Явная отмена записи (кнопка "Отменить" на панели записи, п.5).
                    fun cancelVoiceRecording() {
                        discardRecordingInternal()
                    }

                    // Вызывается при отпускании кнопки микрофона — останавливает запись
                    // и, если она достаточно длинная, открывает окно предпрослушивания.
                    fun stopVoiceRecordingToPreview() {
                        micPressHeld = false
                        val recorder = activeRecorder
                        val file = activeRecordingFile
                        isRecording = false
                        if (recorder == null || file == null) return
                        val stopped = app.yodo.messenger.util.AudioUtils.stopRecording(recorder)
                        activeRecorder = null
                        activeRecordingFile = null
                        if (!stopped) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Не удалось сохранить запись") }
                            return
                        }
                        // Слишком короткая запись (случайный тап) — просто отбрасываем.
                        if (recordingElapsedMs < 700L) {
                            file.delete()
                            recordingElapsedMs = 0L
                            return
                        }
                        recordedVoiceFile = file
                        recordedVoiceDurationMs = recordingElapsedMs
                        recordingElapsedMs = 0L
                    }

                    // Пользователь нажал "Отправить" в окне предпрослушивания.
                    fun sendRecordedVoice() {
                        val file = recordedVoiceFile ?: return
                        recordedVoiceFile = null
                        coroutineScope.launch {
                            val encoded = withContext(Dispatchers.Default) {
                                app.yodo.messenger.util.AudioUtils.fileToBase64(file)
                            }
                            if (encoded != null) {
                                viewModel.sendVoice(encoded.first, encoded.second)
                            } else {
                                snackbarHostState.showSnackbar("Запись слишком длинная, попробуйте короче")
                            }
                        }
                    }

                    // Пользователь нажал "Отменить" в окне предпрослушивания — просто удаляем файл.
                    fun discardRecordedVoice() {
                        recordedVoiceFile?.delete()
                        recordedVoiceFile = null
                        recordedVoiceDurationMs = 0L
                    }

                    // Таймер записи + автостоп по достижении максимальной длительности
                    LaunchedEffect(isRecording) {
                        if (isRecording) {
                            val startedAt = System.currentTimeMillis()
                            while (isRecording) {
                                recordingElapsedMs = System.currentTimeMillis() - startedAt
                                if (recordingElapsedMs >= app.yodo.messenger.util.AudioUtils.MAX_RECORDING_MS) {
                                    stopVoiceRecordingToPreview()
                                    break
                                }
                                kotlinx.coroutines.delay(200L)
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
                            // НОВОЕ (п.5): "Вы записали голосовое сообщение" — прослушать перед отправкой.
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
                                // НОВОЕ (п.5): микрофон теперь работает по принципу "нажал-держи-отпустил" —
                                // запись идёт, пока палец на кнопке; отпускание — как жест "поднять палец",
                                // а не как обычный тап, поэтому в MessageInputBar это pointerInput, а не clickable.
                                onMicPressStart = { startVoiceRecording() },
                                onMicPressEnd = { stopVoiceRecordingToPreview() },
                                colorTheme = colorTheme,
                                placeholder = if (isChannel && uiState.isAdmin) "Вы админ, вам можно писать" else "Сообщение...",
                                pendingTtlSeconds = pendingMessageTtlSeconds,
                                isTtlExplicitlySet = pendingMessageTtlExplicitlySet,
                                onTtlIconClick = { showPerMessageTtlDialog = true }
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
                            // НОВОЕ (расширенные опросы): пункт "Опрос" открывает диалог создания.
                            onPickPoll = {
                                showAttachMenu = false
                                showPollCreation = true
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

                    // НОВОЕ (расширенные опросы): диалог создания опроса. advancedPollsEnabled
                    // управляет видимостью доп. параметров (множественный выбор, авто-закрытие).
                    if (showPollCreation) {
                        PollCreationDialog(
                            advancedPollsEnabled = advancedPollsEnabled,
                            colorTheme = colorTheme,
                            onDismiss = { showPollCreation = false },
                            onConfirm = { question, options, isAnonymous, allowMultiple, closesAtMillis ->
                                viewModel.sendPoll(question, options, isAnonymous, allowMultiple, closesAtMillis)
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
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            colorTheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .swipeToGoBack(onBack = onBackClick)
        ) {
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
                    // НОВОЕ (п.1): более информативная лента — разделители "Сегодня/Вчера/дата"
                    // между сообщениями разных дней, чтобы легче ориентироваться в истории.
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
                            SwipeableMessageBubble(
                                message = message,
                                isOwnMessage = message.senderId == viewModel.currentUserId,
                                currentUserId = viewModel.currentUserId,
                                autoDownloadImages = autoDownloadImages,
                                colorTheme = colorTheme,
                                // НОВОЕ (переработка каналов): для постов канала показываем
                                // кнопку комментариев и пробрасываем переход в экран комментариев.
                                isChannel = isChannel,
                                onCommentsClick = { onOpenComments(chatId, message.id) },
                                onReply = { viewModel.setReplyingTo(message) },
                                onEdit = { viewModel.setEditingMessage(message) },
                                onDelete = { viewModel.deleteMessage(message) },
                                onForward = { viewModel.prepareForward(message); onForwardMessage() },
                                onReact = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                                onPin = { viewModel.togglePinMessage(message.id) },
                                onSaveToFavorite = { viewModel.saveToFavorite(message) },
                                // НОВОЕ (расширенные опросы): голос/переголосование по варианту.
                                onVotePoll = { optionIndex -> viewModel.voteOnPoll(message.id, optionIndex) },
                                onClosePoll = { viewModel.closePoll(message.id) },
                                onImageClick = { base64 ->
                                    onOpenImageViewer(base64, uiState.chatTitle, message.timestamp)
                                },
                                onReplyQuoteClick = { targetMessageId ->
                                    val targetIndex = displayedMessages.indexOfFirst { it.id == targetMessageId }
                                    if (targetIndex >= 0) {
                                        coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                                    }
                                },
                                // НОВОЕ (п.28): клик по имени пересланного отправителя — переход к его профилю.
                                onForwardedSenderClick = { senderId -> onOpenUserProfile(senderId) },
                                // НОВОЕ (п.3): длинный свайп влево (от 2см и дальше) — закрыть чат.
                                onSwipeBack = onBackClick
                            )
                        }
                    }
                }
            }
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
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onSaveToFavorite: () -> Unit,
    // НОВОЕ (расширенные опросы): голос по варианту опроса (индекс варианта).
    onVotePoll: (Int) -> Unit,
    // НОВОЕ (расширенные опросы): досрочное закрытие опроса автором.
    onClosePoll: () -> Unit,
    onImageClick: (String) -> Unit,
    onReplyQuoteClick: (String) -> Unit,
    onForwardedSenderClick: (String) -> Unit,
    onSwipeBack: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    // НОВОЕ (п.3): пороги свайпа заданы в "см", а не в условных dp, как просили —
    // 1см ≈ 63dp при стандартной плотности (160dpi базовая единица Android).
    // Свайп ВПРАВО (положительный offsetX) — всегда "Ответить", порог 1-2см.
    // Свайп ВЛЕВО (отрицательный offsetX): первые 0-2см — открывает пересылку;
    // дальше, от 2см и до края экрана — это жест "назад" (закрыть чат).
    val dpPerCm = 160f / 2.54f
    val replyThresholdDp = 1.5f * dpPerCm     // ~1.5см вправо — порог "Ответить"
    val forwardZoneEndDp = 2f * dpPerCm       // 0-2см влево — зона "Переслать"
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
                            // 0-2см влево — пересылка; дальше (2см и до края экрана) — жест "назад".
                            offsetX < -forwardZoneEndPx -> onSwipeBack()
                            offsetX < 0f -> onForward()
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
                onSaveToFavorite = onSaveToFavorite,
                onVotePoll = onVotePoll,
                onClosePoll = onClosePoll,
                onImageClick = onImageClick, onReplyQuoteClick = onReplyQuoteClick,
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
    onReact: (String) -> Unit,
    onPin: () -> Unit,
    onSaveToFavorite: () -> Unit,
    // НОВОЕ (расширенные опросы): голос по варианту опроса (индекс варианта).
    onVotePoll: (Int) -> Unit,
    // НОВОЕ (расширенные опросы): досрочное закрытие опроса автором.
    onClosePoll: () -> Unit,
    onImageClick: (String) -> Unit,
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
                    // Крестик убран отсюда — открепление теперь только через крестик
                    // в верхнем баннере "Закреплённое сообщение" (с подтверждением) или через меню сообщения.
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
                    // НОВОЕ (п.37): проигрыватель голосового сообщения — прослушивание,
                    // перемотка ползунком и выбор скорости воспроизведения.
                    message.voiceBase64?.let { voiceBase64 ->
                        VoicePlayerBubble(
                            messageId = message.id,
                            voiceBase64 = voiceBase64,
                            durationMs = message.voiceDurationMs ?: 0L,
                            textColor = textColor,
                            accentColor = colorTheme.primary
                        )
                    }
                    // НОВОЕ: файловое вложение — плашка с иконкой, именем и размером,
                    // тап открывает файл через системный интент (или предлагает поделиться).
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
                    // НОВОЕ: геолокация — мини-превью карты, тап открывает точку в приложении карт.
                    if (message.locationLat != null && message.locationLng != null) {
                        LocationMessageBubble(
                            lat = message.locationLat,
                            lng = message.locationLng,
                            textColor = textColor
                        )
                    }
                    // НОВОЕ (расширенные опросы): карточка опроса — вопрос, варианты с барами
                    // результатов, тап по варианту голосует/переголосует. Автору доступна
                    // кнопка досрочного закрытия голосования.
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
                        // НОВОЕ: превью ссылки — если в тексте есть URL, под сообщением
                        // подтягивается карточка с og:title/og:image (как в Telegram).
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
                        // НОВОЕ (п.38): значок таймера у исчезающих сообщений
                        if (message.expiresAt != null) {
                            Icon(
                                Icons.Filled.Timer, contentDescription = "Исчезающее сообщение",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp).padding(end = 3.dp)
                            )
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
                    // НОВОЕ (переработка каналов): комментарии к посту — только для каналов.
                    if (isChannel) {
                        DropdownMenuItem(
                            text = { Text("Комментарии") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null) },
                            onClick = { showMenu = false; onCommentsClick() }
                        )
                    }
                    DropdownMenuItem(text = { Text(if (message.isPinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) }, onClick = { showMenu = false; onPin() })
                    DropdownMenuItem(text = { Text("В избранное") }, leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null) }, onClick = { showMenu = false; onSaveToFavorite() })
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
            // НОВОЕ (переработка каналов): кнопка комментариев под каждым постом канала.
            // Счётчик берётся из Message.commentsCount (инкрементируется при добавлении
            // комментария в MessageRepositoryImpl.addComment). Тап открывает экран комментариев.
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
                        tint = colorTheme.primary, modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (message.commentsCount > 0) "Комментарии · ${message.commentsCount}" else "Комментировать",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorTheme.primary
                    )
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

// НОВОЕ: панель смайликов, открывается кнопкой слева от поля ввода (как в
// оригинальном мессенджере — самая левая кнопка нижней панели чата).
// Простая сетка часто используемых эмодзи; тап вставляет эмодзи в текст.
private val COMMON_EMOJIS = listOf(
    "😀", "😂", "😍", "🥰", "😊", "😉", "😎", "🤔",
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

// НОВОЕ (п.1): нижняя панель ввода переработана — круглая кнопка "+" для прикрепления,
// поле ввода в закруглённой карточке-"пилюле" с тенью, отдельная круглая кнопка отправки
// с акцентным цветом. Долгое нажатие на кнопку отправки открывает диалог планирования
// (п.2) — индивидуально для текста, который сейчас в поле ввода.
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
    // НОВОЕ: таймер исчезновения для СЛЕДУЮЩЕГО отправляемого сообщения (per-message,
    // как в Telegram). null + isTtlExplicitlySet=false — используется TTL чата по умолчанию.
    pendingTtlSeconds: Long? = null,
    isTtlExplicitlySet: Boolean = false,
    onTtlIconClick: () -> Unit = {}
) {
    val canSend = !isSending && text.isNotBlank()
    var showEmojiPicker by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column {
            // НОВОЕ: панель смайликов открывается по кнопке слева от поля ввода —
            // как в первоисточнике, самая левая кнопка нижнего меню чата.
            if (showEmojiPicker) {
                EmojiPickerPanel(onEmojiSelected = { emoji -> onTextChange(text + emoji) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                // ИСПРАВЛЕНО (п.1): кнопки и поле ввода должны быть на одном уровне —
                // раньше было Alignment.Bottom, из-за чего при росте многострочного поля
                // кнопки "уезжали" вниз относительно первой строки текста.
                verticalAlignment = Alignment.CenterVertically
            ) {
                // НОВОЕ: кнопка смайликов — самая левая кнопка панели ввода.
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
                // НОВОЕ (п.4): диаметр кнопки "прикрепить" увеличен на ~20% (42dp -> 50dp).
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
                Spacer(modifier = Modifier.width(4.dp))
                // НОВОЕ (как в Telegram): иконка часов рядом с полем ввода — выбор таймера
                // исчезновения для СЛЕДУЮЩЕГО сообщения. Подсвечена цветом темы, если таймер
                // выбран явно (переопределяет TTL по умолчанию для чата на одно сообщение).
                Box(
                    modifier = Modifier
                        .size(34.dp)
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
                Spacer(modifier = Modifier.width(2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp),
                    // ИСПРАВЛЕНО (п.1): минимальная высота поля равна высоте кнопок (42.dp),
                    // чтобы при пустом/однострочном тексте всё было выровнено по центру в ряд.
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
                // НОВОЕ (п.5+п.4): кнопка микрофона теперь работает по принципу "нажал-держи-
                // отпустил" — запись идёт, пока палец на кнопке (onMicPressStart/onMicPressEnd),
                // а не как обычный тап. Диаметр увеличен на ~20% (42dp -> 50dp), как и у "прикрепить".
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
                                        // Срабатывает и при обычном отпускании, и если жест был
                                        // прерван (например, палец увели с кнопки) — запись всё
                                        // равно должна корректно остановиться в обоих случаях.
                                        onMicPressEnd()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Удерживайте для записи голосового сообщения", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                } else {
                    // ИСПРАВЛЕНО (п.1): раньше использовался combinedClickable(enabled = canSend, ...) —
                    // когда поле пустое (canSend = false), enabled=false ПОЛНОСТЬЮ отключал жест,
                    // включая onLongClick, поэтому меню планирования не могло открыться, пока не
                    // введён текст, а на реальных устройствах combinedClickable иногда вообще не
                    // распознавал долгое нажатие рядом с текстовым полем (конфликт жестов).
                    // Заменено на pointerInput с detectTapGestures — так onLongPress надёжно
                    // срабатывает независимо от того, пуст текст или нет; проверку "есть ли текст"
                    // и переключение экрана планирования делает уже сам onSendLongPress в ChatScreen.
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (canSend) colorTheme.primary else colorTheme.primary.copy(alpha = 0.3f))
                            .pointerInput(canSend) {
                                detectTapGestures(
                                    onTap = { if (canSend) onSendClick() },
                                    onLongPress = { if (canSend) onSendLongPress() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSending) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить (удержите — запланировать)", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// НОВОЕ (переработка каналов): нижняя панель в канале для не-админов.
// Не подписан — заметная градиентная CTA «Подписаться на канал» (primary → accent)
// с иконкой-колокольчиком; подписан или официальный канал — спокойная строка о том,
// что публиковать посты могут только админы, а комментировать — все подписчики.
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

// НОВОЕ (п.37): панель записи голосового сообщения — заменяет обычную панель ввода,
// пока идёт запись. Показывает таймер, кнопку отмены (корзина) и кнопку отправки (галочка).
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
            // Пульсирующая точка-индикатор записи
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rec")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.2f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(700),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ), label = "recAlpha"
            )
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
            )
            Text(
                app.yodo.messenger.util.AudioUtils.formatDuration(elapsedMs),
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

// НОВОЕ (п.5): окно предпрослушивания после отпускания кнопки записи — "Вы записали
// голосовое сообщение". Позволяет прослушать запись (play/pause) перед отправкой,
// с кнопками "Отправить" и "Отменить". Файл ещё лежит на диске (не в base64/Firestore),
// поэтому воспроизведение идёт напрямую через MediaPlayer.setDataSource(path).
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
                // Если предыдущее прослушивание доиграло до конца — начинаем заново с начала.
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
                    app.yodo.messenger.util.AudioUtils.formatDuration(durationMs),
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

// НОВОЕ (п.2): диалог выбора даты/времени для отложенной отправки конкретного сообщения.
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

// НОВОЕ (п.2): список отложенных сообщений текущего чата с возможностью отменить каждое.
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

// НОВОЕ (п.38, переработка "как в Telegram"): доступные варианты таймера исчезающих
// сообщений (в секундах). null в списке вариантов означает "Выключено".
// Набор расширен до телеграм-подобного: 30 сек / 1 мин / 1 час / 1 день / 1 неделя / 1 месяц,
// плюс отдельный пункт "Свой вариант" открывает CustomDisappearingDurationDialog.
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
    // Значение не совпадает со стандартными пунктами — значит это "свой вариант",
    // подбираем самую крупную подходящую единицу для компактной подписи.
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

// НОВОЕ: русское склонение "подписчик/подписчика/подписчиков" для шапки канала.
private fun subscriberCountLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "подписчиков"
        mod10 == 1 -> "подписчик"
        mod10 in 2..4 -> "подписчика"
        else -> "подписчиков"
    }
}

// ПЕРЕРАБОТАНО (как в Telegram): диалог теперь используется в двух местах —
// как настройка TTL по умолчанию для чата (заголовок/описание про чат) и как выбор
// таймера для КОНКРЕТНОГО следующего отправляемого сообщения (perMessage = true,
// другой заголовок/описание, как в Telegram при выборе через иконку часов у поля ввода).
// В обоих случаях добавлен пункт "Свой вариант…", открывающий отдельный ввод длительности.
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
                        "Новые сообщения в этом чате будут по умолчанию автоматически удаляться через выбранное время после отправки. Таймер для отдельного сообщения можно изменить перед его отправкой (иконка часов рядом с полем ввода).",
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
                // НОВОЕ: "Свой вариант" — как в Telegram, произвольная длительность.
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

// НОВОЕ: ввод произвольной длительности таймера исчезающих сообщений — число + единица
// измерения (секунды/минуты/часы/дни), как аналог кастомного варианта в Telegram.
@Composable
private fun CustomDisappearingDurationDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("1") }
    val units = listOf("секунды" to 1L, "минуты" to 60L, "часы" to 3600L, "дни" to 86400L)
    var selectedUnitIndex by remember { mutableStateOf(1) } // по умолчанию — минуты
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

// НОВОЕ (п.37): проигрыватель голосового сообщения — play/pause, перемотка (Slider),
// выбор скорости воспроизведения (0.5x / 1x / 1.5x / 2x, по кругу тапом на бейдж скорости).
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

    // Освобождаем MediaPlayer при выходе бабла из композиции (скролл, размонтирование чата).
    DisposableEffect(messageId) {
        onDispose {
            mediaPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
            mediaPlayer = null
        }
    }

    // Пока идёт воспроизведение — обновляем позицию слайдера каждые 200мс.
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
        } catch (e: Exception) { /* На некоторых устройствах смена скорости на паузе может падать — игнорируем */ }
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
        val file = app.yodo.messenger.util.AudioUtils.base64ToTempFile(context, voiceBase64, messageId) ?: return
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
                app.yodo.messenger.util.AudioUtils.formatDuration(
                    if (isPlaying || currentPositionMs > 0) currentPositionMs.toLong() else durationMs
                ),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.75f)
            )
        }
        // НОВОЕ (п.37): бейдж выбора скорости — тап переключает по кругу 1x → 1.5x → 2x → 0.5x → 1x.
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

// НОВОЕ (п.1): метка-разделитель дня для сообщения — "Сегодня" / "Вчера" / "12 июля" / "12 июля 2025".
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

// НОВОЕ: меню вложений, открывается по кнопке "+" в панели ввода — фото / файл / геопозиция / опрос.
@Composable
private fun AttachMenuDialog(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onPickLocation: () -> Unit,
    onPickPoll: () -> Unit
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
                AttachMenuRow(icon = Icons.Filled.InsertDriveFile, label = "Файл", onClick = onPickFile)
                AttachMenuRow(icon = Icons.Filled.LocationOn, label = "Геопозиция", onClick = onPickLocation)
                AttachMenuRow(icon = Icons.Filled.Poll, label = "Опрос", onClick = onPickPoll)
                Spacer(modifier = Modifier.height(4.dp))
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

// НОВОЕ: диалог выбора точки на карте для отправки геопозиции. Переиспользует тот же
// стек (osmdroid + FusedLocationProviderClient), что и экран "Кто рядом" (NearbyPeopleScreen),
// чтобы не тянуть Google Maps SDK (он требует привязку карты оплаты — тот же блокер,
// что был с Firebase Storage/Blaze).
@Composable
private fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current
    // centerLat/centerLng больше не являются Compose state — их изменение (при скролле карты)
    // раньше вызывало recomposition AndroidView и пересоздание/перерисовку MapView, что и
    // выглядело как "моргание". Теперь это обычные var, читаемые только в момент подтверждения.
    var centerLat = 55.7558
    var centerLng = 37.6173
    var hasLocatedUser by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        OsmConfiguration.getInstance().osmdroidTileCache = context.cacheDir
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            // Максимальная точность вместо энергосберегающей — важно для точного выбора точки.
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
                                        // Пишем напрямую в переменные (не Compose state), чтобы скролл
                                        // карты не вызывал recomposition/пересоздание AndroidView —
                                        // именно это раньше приводило к "морганию" карты.
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

// НОВОЕ: плашка файлового вложения в бабле сообщения — иконка с расширением, имя файла,
// человекочитаемый размер. Тап открывает файл системным интентом (через FileProvider);
// если подходящего приложения нет — предлагает "Поделиться" вместо падения с ошибкой.
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
                val file = app.yodo.messenger.util.FileUtils.base64ToTempFile(context, fileBase64, messageId, fileName)
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
                app.yodo.messenger.util.FileUtils.extensionLabel(fileName),
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
                app.yodo.messenger.util.FileUtils.formatSize(sizeBytes),
                color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall
            )
        }
    }
}


// ══════════════════════════════════════════════════════════════════
// НОВОЕ (расширенные опросы): карточка опроса в сообщении — вопрос,
// список вариантов с барами результата и голосованием по тапу.
// ══════════════════════════════════════════════════════════════════
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
    // Результаты (проценты/бары) показываются, если голосование закрыто, пользователь
    // уже проголосовал, или опрос анонимный без публичного списка (по умолчанию поведение
    // как в большинстве мессенджеров — свои проценты видно сразу после голоса).
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
                if (isClosed) "Опрос завершён" else "Опрос",
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
                            .background(accentColor.copy(alpha = if (isSelected) 0.35f else 0.18f))
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
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
                // Автор опроса может закрыть голосование досрочно.
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

// ══════════════════════════════════════════════════════════════════
// НОВОЕ (расширенные опросы): диалог создания опроса. Базовые поля —
// вопрос, варианты (2-10), анонимность. Если advancedPollsEnabled = true
// (см. UserSettingsPreferences), также доступны множественный выбор
// и дата/время авто-закрытия голосования.
// ══════════════════════════════════════════════════════════════════
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PollCreationDialog(
    advancedPollsEnabled: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onDismiss: () -> Unit,
    onConfirm: (question: String, options: List<String>, isAnonymous: Boolean, allowMultiple: Boolean, closesAtMillis: Long?) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var isAnonymous by remember { mutableStateOf(true) }
    var allowMultiple by remember { mutableStateOf(false) }
    // НОВОЕ (расширенные опросы): выбор срока действия опроса в часах; null = бессрочно.
    var closesInHours by remember { mutableStateOf<Int?>(null) }

    val validOptionsCount = options.count { it.isNotBlank() }
    val canSubmit = question.isNotBlank() && validOptionsCount >= 2

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

                // Базовый параметр — доступен всегда, независимо от "расширенных опросов".
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

                // НОВОЕ (расширенные опросы): доп. параметры видны только если пользователь
                // включил соответствующую настройку (в Settings или при регистрации).
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
                            onConfirm(
                                question.trim(),
                                options.map { it.trim() }.filter { it.isNotEmpty() },
                                isAnonymous,
                                if (advancedPollsEnabled) allowMultiple else false,
                                if (advancedPollsEnabled) closesAtMillis else null
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

// НОВОЕ: превью геопозиции в бабле сообщения — маленькая статичная карта osmdroid
// с маркером; тап открывает точку в любом установленном картографическом приложении.
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
// НОВОЕ: полноэкранный просмотр геопозиции внутри приложения (без перехода во внешнюю карту).
// Карта заполняет весь экран, без вложенных Column/Box с недетерминированными размерами —
// это и убирает "моргание", которое возникало при перерисовке AndroidView внутри маленького
// превью-контейнера. Кнопка "Открыть в приложении карт" оставлена как дополнительная опция.
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
                    // Без блока update — карта создаётся один раз и больше не пересоздаётся
                    // и не перецентровывается при recomposition, что убирает моргание.
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
