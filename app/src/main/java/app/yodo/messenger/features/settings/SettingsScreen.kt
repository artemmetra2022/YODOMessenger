package app.yodo.messenger.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.data.local.AppLanguage
import app.yodo.messenger.data.local.ChatBackgroundType
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.data.local.PinRequirement
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.allColorThemes
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    onOpenBlockedUsers: () -> Unit = {},
    // НОВОЕ (п.15): настройки приватности «Кто может приглашать в группы / писать / смотреть профиль».
    onOpenPrivacyWho: () -> Unit = {},
    // НОВОЕ (Y): переход на экран смены аккаунта.
    onSwitchAccount: () -> Unit = {},
    // НОВОЕ (батч 7): открыть «Фишки и инструменты».
    onOpenTools: () -> Unit = {},
    // НОВОЕ (батч 7): открыть «Центр безопасности».
    onOpenSecurity: () -> Unit = {},
    // НОВОЕ (AC): открыть раздел «Жалобы» (только админы).
    onOpenReports: () -> Unit = {},
    // НОВОЕ (обучение): повторно открыть экран онбординга из настроек.
    onOpenOnboarding: () -> Unit = {},
    // НОВОЕ (поиск по настройкам): если экран открыт из результата поиска —
    // id пункта, к которому нужно сразу прокрутить список.
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val colorThemeName by viewModel.colorThemeName.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsState()
    val showReadReceipts by viewModel.showReadReceipts.collectAsState()
    val pinRequirement by viewModel.pinRequirement.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
    // НОВОЕ (скрытые чаты): установлен ли ложный PIN.
    val isDecoyPinSet by viewModel.isDecoyPinSet.collectAsState()
    val pinLockDelaySeconds by viewModel.pinLockDelaySeconds.collectAsState()
    val showBirthDate by viewModel.showBirthDate.collectAsState()
    val showAboutMe by viewModel.showAboutMe.collectAsState()
    val showLocation by viewModel.showLocation.collectAsState()
    val showWebsite by viewModel.showWebsite.collectAsState()
    val showPhoneNumber by viewModel.showPhoneNumber.collectAsState()
    val showEmail by viewModel.showEmail.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val hideKeyboardOnSend by viewModel.hideKeyboardOnSend.collectAsState()
    val hideKeyboardOnScroll by viewModel.hideKeyboardOnScroll.collectAsState()
    val quickReaction by viewModel.quickReaction.collectAsState()
    val advancedPollsEnabled by viewModel.advancedPollsEnabled.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val notificationVibration by viewModel.notificationVibration.collectAsState()
    val muteAllNotifications by viewModel.muteAllNotifications.collectAsState()
    // НОВОЕ: тихие часы, пауза и скрытие превью.
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsState()
    val quietHoursStart by viewModel.quietHoursStart.collectAsState()
    val quietHoursEnd by viewModel.quietHoursEnd.collectAsState()
    val hideNotificationPreview by viewModel.hideNotificationPreview.collectAsState()
    val notificationsSnoozedUntil by viewModel.notificationsSnoozedUntil.collectAsState()
    val accountDeleted by viewModel.accountDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val errorMessageResId by viewModel.errorMessageResId.collectAsState()

    // НОВОЕ (п.18): автоудаление аккаунта
    val autoDeleteEnabled by viewModel.autoDeleteEnabled.collectAsState()
    val autoDeleteDays by viewModel.autoDeleteDays.collectAsState()

    // НОВОЕ (п.13): фон чата
    val chatBackgroundType by viewModel.chatBackgroundType.collectAsState()
    val chatBackgroundCustomPath by viewModel.chatBackgroundCustomPath.collectAsState()

    // НОВОЕ (п.4): папки чатов
    val chatFolders by viewModel.chatFolders.collectAsState()

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAutoDeleteDialog by remember { mutableStateOf(false) }
    var showChatBackgroundDialog by remember { mutableStateOf(false) }
    var showChatFoldersDialog by remember { mutableStateOf(false) }
    var showQuickReactionDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val colorTheme = LocalColorTheme.current

    // НОВОЕ (поиск по настройкам): показывать ли настройки в общем поиске.
    val showSettingsInGlobalSearch by viewModel.showSettingsInGlobalSearch.collectAsState()

    // НОВОЕ (поиск по настройкам): строка поиска, найденные пункты и прокрутка к ним.
    var settingsSearchQuery by remember { mutableStateOf("") }
    val settingsSearchResults = remember(settingsSearchQuery) {
        if (settingsSearchQuery.isBlank()) emptyList()
        else SettingsSearchMatcher.search(settingsSearchQuery)
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // Заполняется реальными Y-координатами (относительно LazyColumn) по мере отрисовки
    // секций — anchorId -> текущее смещение на экране. Используется, чтобы прокрутить
    // список к найденному пункту после тапа по результату поиска настроек.
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    // НОВОЕ (поиск по настройкам): id пункта, который сейчас подсвечен после
    // перехода из поиска. Подсветка гаснет сама через HIGHLIGHT_DURATION_MS —
    // см. onSettingsAnchor, который читает это значение и анимирует фон.
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }

    suspend fun scrollAndHighlight(anchorId: String) {
        highlightedAnchor = anchorId
        val targetY = anchorPositions[anchorId]
        if (targetY != null) {
            // targetY уже измерен относительно верхней границы LazyColumn,
            // поэтому просто скроллим на эту величину минус небольшой отступ сверху,
            // чтобы найденный пункт не прилипал к самому краю экрана.
            listState.animateScrollBy(targetY - 24f)
        }
        // Подсветка держится некоторое время, затем гаснет — чтобы пользователь
        // успел заметить, какой именно пункт нашёлся, но она не мешала дальше.
        kotlinx.coroutines.delay(HIGHLIGHT_DURATION_MS)
        if (highlightedAnchor == anchorId) highlightedAnchor = null
    }

    fun jumpToAnchor(anchorId: String) {
        settingsSearchQuery = ""
        coroutineScope.launch {
            // Небольшая задержка — даём LazyColumn перестроиться после закрытия поиска
            // и обновить измеренные позиции секций.
            kotlinx.coroutines.delay(80)
            scrollAndHighlight(anchorId)
        }
    }

    // НОВОЕ (поиск по настройкам): если экран открыт из результата поиска —
    // сразу прокручиваем к найденному пункту и подсвечиваем его, когда его
    // позиция станет известна (после того как LazyColumn измерит секции).
    LaunchedEffect(initialAnchorId) {
        if (initialAnchorId != null) {
            // Ждём, пока LazyColumn отрисуется и измерит позиции секций.
            kotlinx.coroutines.delay(250)
            scrollAndHighlight(initialAnchorId)
        }
    }

    val notAuthorizedText = stringResource(R.string.settings_not_authorized)
    LaunchedEffect(accountDeleted) { if (accountDeleted) onLoggedOut() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeError() }
    }
    LaunchedEffect(errorMessageResId) {
        errorMessageResId?.let { resId ->
            val text = if (resId == R.string.settings_not_authorized) notAuthorizedText else ""
            if (text.isNotEmpty()) snackbarHostState.showSnackbar(text)
            viewModel.consumeError()
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_account_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showDeleteAccountDialog = false; viewModel.deleteAccount() }) {
                    Text(stringResource(R.string.settings_delete_account), color = YodoError)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    // НОВОЕ (п.18): диалог автоудаления аккаунта
    if (showAutoDeleteDialog) {
        AutoDeleteDialog(
            enabled = autoDeleteEnabled,
            days = autoDeleteDays,
            onDismiss = { showAutoDeleteDialog = false },
            onSave = { enabled, days ->
                viewModel.setAutoDeleteEnabled(enabled)
                viewModel.setAutoDeleteDays(days)
                showAutoDeleteDialog = false
            }
        )
    }

    // НОВОЕ (п.13): диалог выбора фона чата
    val context = androidx.compose.ui.platform.LocalContext.current
    val chatBackgroundImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setChatBackgroundCustomPath(uri.toString())
            viewModel.setChatBackgroundType(ChatBackgroundType.CUSTOM_IMAGE)
        }
        showChatBackgroundDialog = false
    }
    if (showChatBackgroundDialog) {
        ChatBackgroundDialog(
            currentType = chatBackgroundType,
            customPath = chatBackgroundCustomPath,
            onDismiss = { showChatBackgroundDialog = false },
            onSelect = { type ->
                if (type == ChatBackgroundType.CUSTOM_IMAGE) {
                    chatBackgroundImagePicker.launch("image/*")
                } else {
                    viewModel.setChatBackgroundType(type)
                    showChatBackgroundDialog = false
                }
            }
        )
    }

    // НОВОЕ (п.4): диалог управления папками чатов
    if (showQuickReactionDialog) {
        QuickReactionDialog(
            selected = quickReaction,
            onSelect = { viewModel.setQuickReaction(it) },
            onDismiss = { showQuickReactionDialog = false },
            colorTheme = colorTheme
        )
    }
    if (showChatFoldersDialog) {
        ChatFoldersDialog(
            folders = chatFolders,
            onDismiss = { showChatFoldersDialog = false },
            onAddFolder = { name -> viewModel.addChatFolder(name) },
            onDeleteFolder = { folderId -> viewModel.deleteChatFolder(folderId) },
            onRenameFolder = { folder, newName -> viewModel.updateChatFolder(folder.copy(name = newName)) },
            onReorderFolder = { folder, direction ->
                val sorted = chatFolders.sortedBy { it.order }.toMutableList()
                val currentIndex = sorted.indexOfFirst { it.id == folder.id }
                val targetIndex = currentIndex + direction
                if (currentIndex >= 0 && targetIndex in sorted.indices) {
                    val a = sorted[currentIndex]
                    val b = sorted[targetIndex]
                    viewModel.updateChatFolder(a.copy(order = b.order))
                    viewModel.updateChatFolder(b.copy(order = a.order))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.settings_profile))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorTheme.primary.copy(alpha = 0.10f), Color.Transparent),
                        endY = 400f
                    )
                )
                .padding(padding)
        ) {
            // ════════════════════════════════════════
            // ПОИСК ПО НАСТРОЙКАМ
            // ════════════════════════════════════════
            item {
                SettingsSearchBar(
                    query = settingsSearchQuery,
                    onQueryChanged = { settingsSearchQuery = it },
                    colorTheme = colorTheme
                )
            }
            if (settingsSearchQuery.isNotBlank()) {
                item {
                    SettingsSearchResultsList(
                        results = settingsSearchResults,
                        colorTheme = colorTheme,
                        onResultClick = { jumpToAnchor(it.anchorId) }
                    )
                }
            }

            // ════════════════════════════════════════
            // ОФОРМЛЕНИЕ
            // ════════════════════════════════════════
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.ColorLens,
                    title = stringResource(R.string.settings_section_appearance),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_APPEARANCE, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Brightness6,
                        title = stringResource(R.string.settings_dark_theme),
                        subtitle = stringResource(R.string.settings_dark_theme_subtitle),
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) },
                        colorTheme = colorTheme
                    )
                }
            }

            // ══════════���═════════════════════════════
            // КАСТОМИЗАЦ��Я
            // ══════════��═════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.BubbleChart,
                    title = stringResource(R.string.settings_section_customization),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_CUSTOMIZATION, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.ColorLens,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_color_theme), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            allColorThemes.forEach { theme ->
                                val isSelected = colorThemeName == theme.name.name
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { viewModel.setColorTheme(theme.name.name) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape
                                                ) else Modifier
                                            )
                                    )
                                    Text(
                                        theme.name.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 3.dp),
                                        color = if (isSelected) colorTheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Размер шрифта
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.FormatSize,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        var sliderPosition by remember(fontSize) { mutableFloatStateOf(fontSize.ordinal.toFloat()) }
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                viewModel.setFontSize(FontSize.entries[sliderPosition.roundToInt()])
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FontSize.entries.forEach { size ->
                                Text(
                                    text = size.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (fontSize == size) colorTheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            // ЯЗЫК
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_section_language),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_LANGUAGE, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Language,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppLanguage.entries.forEach { language ->
                                val isSelected = currentLanguage == language
                                Text(
                                    text = stringResource(language.labelResId),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colorTheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.setLanguage(language) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            // ЧАТЫ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Chat,
                    title = stringResource(R.string.settings_section_chats),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_CHATS, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Keyboard,
                        title = stringResource(R.string.settings_send_on_enter),
                        subtitle = stringResource(R.string.settings_send_on_enter_subtitle),
                        checked = sendOnEnter,
                        onCheckedChange = { viewModel.setSendOnEnter(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Keyboard,
                        title = stringResource(R.string.settings_hide_keyboard),
                        subtitle = stringResource(R.string.settings_hide_keyboard_subtitle),
                        checked = hideKeyboardOnSend,
                        onCheckedChange = { viewModel.setHideKeyboardOnSend(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Keyboard,
                        title = "Скрывать клавиатуру при прокрутке",
                        subtitle = "Закрывать клавиатуру при прокрутке сообщений",
                        checked = hideKeyboardOnScroll,
                        onCheckedChange = { viewModel.setHideKeyboardOnScroll(it) },
                        colorTheme = colorTheme
                    )
                    SettingsClickableRow(
                        icon = Icons.Filled.EmojiEmotions,
                        title = "Быстрая реакция",
                        subtitle = "Тройное нажатие: $quickReaction",
                        onClick = { showQuickReactionDialog = true },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Image,
                        title = stringResource(R.string.settings_auto_download),
                        subtitle = stringResource(R.string.settings_auto_download_subtitle),
                        checked = autoDownloadImages,
                        onCheckedChange = { viewModel.setAutoDownloadImages(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Poll,
                        title = stringResource(R.string.register_advanced_polls_title),
                        subtitle = stringResource(R.string.register_advanced_polls_subtitle),
                        checked = advancedPollsEnabled,
                        onCheckedChange = { viewModel.setAdvancedPollsEnabled(it) },
                        colorTheme = colorTheme
                    )
                }
            }

            // НОВОЕ (п.13): фон чата
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_CHAT_BACKGROUND, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Image,
                        title = "Фон чата",
                        subtitle = chatBackgroundType.displayName,
                        colorTheme = colorTheme,
                        onClick = { showChatBackgroundDialog = true }
                    )
                }
            }

            // НОВОЕ (п.4): папки чатов
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_CHAT_FOLDERS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Folder,
                        title = "Папки чатов",
                        subtitle = if (chatFolders.isEmpty()) "Нет папок" else "${chatFolders.size} папок",
                        colorTheme = colorTheme,
                        onClick = { showChatFoldersDialog = true }
                    )
                }
            }

            // ════════════════════════════════════════
            // КОНФИДЕНЦИАЛЬНОСТЬ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.PrivacyTip,
                    title = stringResource(R.string.settings_section_privacy),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_PRIVACY, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = stringResource(R.string.settings_online_status),
                        subtitle = stringResource(R.string.settings_online_status_subtitle),
                        checked = showOnlineStatus,
                        onCheckedChange = { viewModel.setShowOnlineStatus(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = stringResource(R.string.settings_read_receipts),
                        subtitle = stringResource(R.string.settings_read_receipts_subtitle),
                        checked = showReadReceipts,
                        onCheckedChange = { viewModel.setShowReadReceipts(it) },
                        colorTheme = colorTheme
                    )
                }
            }

            // НОВОЕ (п.18): автоудаление аккаунта
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_AUTO_DELETE, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Timer,
                        title = "Автоудаление аккаунта",
                        subtitle = if (autoDeleteEnabled) "Через $autoDeleteDays дней неактивности" else "Выключено",
                        colorTheme = colorTheme,
                        onClick = { showAutoDeleteDialog = true }
                    )
                }
            }

            // PIN
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                var showPinDialog by remember { mutableStateOf(false) }
                val pinSetTitle = stringResource(R.string.settings_pin_set)
                val pinSetUpTitle = stringResource(R.string.settings_pin_set_up)
                val pinNeverSubtitle = stringResource(R.string.settings_pin_never)
                val pinOnCloseSubtitle = stringResource(R.string.settings_pin_on_close)
                val pinOnBackgroundSubtitle = stringResource(R.string.settings_pin_on_background)
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_PIN, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Lock,
                        title = if (isPinSet) pinSetTitle else pinSetUpTitle,
                        subtitle = when (pinRequirement) {
                            PinRequirement.NEVER -> pinNeverSubtitle
                            PinRequirement.ON_CLOSE -> pinOnCloseSubtitle
                            PinRequirement.ON_BACKGROUND -> pinOnBackgroundSubtitle
                        },
                        colorTheme = colorTheme,
                        onClick = { showPinDialog = true }
                    )
                }
                if (showPinDialog) {
                    PinSetupDialog(
                        isPinSet = isPinSet,
                        currentRequirement = pinRequirement,
                        currentLockDelaySeconds = pinLockDelaySeconds,
                        onDismiss = { showPinDialog = false },
                        onSavePin = { pin, requirement ->
                            viewModel.setPin(pin, requirement)
                            showPinDialog = false
                        },
                        onRequirementChanged = { viewModel.setPinRequirement(it) },
                        onLockDelayChanged = { viewModel.setPinLockDelaySeconds(it) },
                        onDisablePin = { viewModel.clearPin(); showPinDialog = false }
                    )
                }

                // НОВОЕ (скрытые чаты): ложный (decoy) PIN — доступен только когда задан основной PIN.
                if (isPinSet) {
                    var showDecoyDialog by remember { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_DECOY_PIN, anchorPositions, highlightedAnchor, colorTheme)) {
                        SettingsNavigateRow(
                            icon = Icons.Filled.Lock,
                            title = if (isDecoyPinSet) "Ложный PIN задан" else "Настроить ложный PIN",
                            subtitle = "Вход по этому коду скрывает выбранные чаты",
                            colorTheme = colorTheme,
                            onClick = { showDecoyDialog = true }
                        )
                    }
                    if (showDecoyDialog) {
                        DecoyPinDialog(
                            isDecoyPinSet = isDecoyPinSet,
                            onDismiss = { showDecoyDialog = false },
                            onSavePin = { pin ->
                                viewModel.setDecoyPin(pin)
                                showDecoyDialog = false
                            },
                            onDisablePin = {
                                viewModel.clearDecoyPin()
                                showDecoyDialog = false
                            }
                        )
                    }
                }
            }

            // Заблокированные
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_BLOCKED_USERS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Block,
                        title = stringResource(R.string.settings_blocked_users),
                        subtitle = stringResource(R.string.settings_blocked_users_subtitle),
                        colorTheme = colorTheme,
                        onClick = onOpenBlockedUsers
                    )
                }
            }

            // НОВОЕ (п.15): настройки приватности «Кто может приглашать в группы / писать / смотреть профиль».
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_WHO_CAN, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.GroupAdd,
                        title = "Кто может…",
                        subtitle = "Приглашать в группы, писать вам, смотреть профиль",
                        colorTheme = colorTheme,
                        onClick = onOpenPrivacyWho
                    )
                }
            }

            // Расширенный профиль
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_PROFILE_VISIBILITY, anchorPositions, highlightedAnchor, colorTheme)) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    stringResource(R.string.settings_extended_profile),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(R.string.settings_extended_profile_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_about_me),
                        subtitle = stringResource(R.string.settings_about_me_subtitle),
                        checked = showAboutMe,
                        onCheckedChange = { viewModel.setShowAboutMe(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_birth_date),
                        subtitle = stringResource(R.string.settings_show_to_others),
                        checked = showBirthDate,
                        onCheckedChange = { viewModel.setShowBirthDate(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_location),
                        subtitle = stringResource(R.string.settings_show_to_others),
                        checked = showLocation,
                        onCheckedChange = { viewModel.setShowLocation(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_website),
                        subtitle = stringResource(R.string.settings_show_to_others),
                        checked = showWebsite,
                        onCheckedChange = { viewModel.setShowWebsite(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_phone_number),
                        subtitle = stringResource(R.string.settings_show_in_profile),
                        checked = showPhoneNumber,
                        onCheckedChange = { viewModel.setShowPhoneNumber(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_email),
                        subtitle = stringResource(R.string.settings_show_in_profile),
                        checked = showEmail,
                        onCheckedChange = { viewModel.setShowEmail(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                }
            }

            // ════════════════════════════════════════
            // УВЕДОМЛЕНИЯ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.VolumeUp,
                    title = stringResource(R.string.settings_section_notifications),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_NOTIFICATIONS, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.NotificationsOff,
                        title = stringResource(R.string.settings_mute_all),
                        subtitle = stringResource(R.string.settings_mute_all_subtitle),
                        checked = muteAllNotifications,
                        onCheckedChange = { viewModel.setMuteAllNotifications(it) },
                        colorTheme = colorTheme
                    )
                    AnimatedVisibility(
                        visible = !muteAllNotifications,
                        enter = fadeIn(androidx.compose.animation.core.tween(200)) + slideInVertically(androidx.compose.animation.core.tween(200))
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsToggleRow(
                                icon = Icons.Filled.VolumeUp,
                                title = stringResource(R.string.settings_sound),
                                subtitle = stringResource(R.string.settings_sound_subtitle),
                                checked = notificationSound,
                                onCheckedChange = { viewModel.setNotificationSound(it) },
                                colorTheme = colorTheme
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsToggleRow(
                                icon = Icons.Filled.Vibration,
                                title = stringResource(R.string.settings_vibration),
                                subtitle = stringResource(R.string.settings_vibration_subtitle),
                                checked = notificationVibration,
                                onCheckedChange = { viewModel.setNotificationVibration(it) },
                                colorTheme = colorTheme
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            // НОВОЕ: РАСШИРЕННЫЕ УВЕДОМЛЕНИЯ + БЛОКНОТ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_QUIET_HOURS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsToggleRow(
                        icon = Icons.Filled.NotificationsOff,
                        title = "Тихие часы",
                        subtitle = "Не беспокоить в заданный ночной интервал",
                        checked = quietHoursEnabled,
                        onCheckedChange = { viewModel.setQuietHoursEnabled(it) },
                        colorTheme = colorTheme
                    )
                    AnimatedVisibility(visible = quietHoursEnabled) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsNavigateRow(
                                icon = Icons.Filled.Timer,
                                title = "Начало: %02d:00".format(quietHoursStart),
                                subtitle = "Нажмите, чтобы сдвинуть на +1 час",
                                colorTheme = colorTheme,
                                onClick = { viewModel.shiftQuietHoursStart(1) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsNavigateRow(
                                icon = Icons.Filled.Timer,
                                title = "Конец: %02d:00".format(quietHoursEnd),
                                subtitle = "Нажмите, чтобы сдвинуть на +1 час",
                                colorTheme = colorTheme,
                                onClick = { viewModel.shiftQuietHoursEnd(1) }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = "Скрывать текст в уведомлениях",
                        subtitle = "Показывать «Новое сообщение» без имени и текста",
                        checked = hideNotificationPreview,
                        onCheckedChange = { viewModel.setHideNotificationPreview(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Timer,
                        title = if (notificationsSnoozedUntil > System.currentTimeMillis()) "Пауза активна — выключить" else "Пауза уведомлений на 1 час",
                        subtitle = if (notificationsSnoozedUntil > System.currentTimeMillis()) "Уведомления временно отключены" else "Тишина на ближайший час",
                        colorTheme = colorTheme,
                        onClick = {
                            if (notificationsSnoozedUntil > System.currentTimeMillis()) viewModel.clearNotificationSnooze()
                            else viewModel.snoozeNotifications(60L * 60L * 1000L)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Timer,
                        title = "Пауза уведомлений на 8 часов",
                        subtitle = "Удобно на ночь или на встречу",
                        colorTheme = colorTheme,
                        onClick = { viewModel.snoozeNotifications(8L * 60L * 60L * 1000L) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.VolumeUp,
                        title = "Отправить тестовое уведомление",
                        subtitle = "Проверить, что уведомления работают",
                        colorTheme = colorTheme,
                        onClick = { viewModel.sendTestNotification() }
                    )
                }
            }
            // ═════════════════════════════���══════════
            // АККА��НТ
            // ══════════════════════════════════���═════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.settings_section_account),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_ACCOUNT, anchorPositions, highlightedAnchor, colorTheme),
                    colorTheme = colorTheme
                )
            }
            // НОВОЕ (AC): раздел «Жалобы» виден только главным админам (2 почты).
            if (viewModel.isAppAdmin) {
                item {
                    SettingsCard {
                        SettingsNavigateRow(
                            icon = Icons.Filled.Flag,
                            title = "Жалобы",
                            subtitle = "Жалобы и обжалования пользователей на рассмотрение",
                            colorTheme = colorTheme,
                            onClick = onOpenReports
                        )
                    }
                }
            }
            // НОВОЕ (батч 7): вход в раздел с 20 новыми функциями.
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_TOOLS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Star,
                        title = "Фишки и инструменты",
                        subtitle = "20 мини-функций: заметки, таймер, пароли, конвертеры и другое",
                        colorTheme = colorTheme,
                        onClick = onOpenTools
                    )
                }
            }
            // НОВОЕ (батч 7): Центр безопасности (2FA по email-коду, защита от скриншотов, статусы).
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SECURITY_CENTER, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Security,
                        title = "Центр безопасности",
                        subtitle = "Двухфакторная аутентификация, сброс через вопросы, защита от скриншотов, статусы",
                        colorTheme = colorTheme,
                        onClick = onOpenSecurity
                    )
                }
            }
            // НОВОЕ (обучение): повторный показ онбординга — тот же экран, что и при первой
            // регистрации, но без сброса/повторной установки флага "обучение пройдено".
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_ONBOARDING, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.School,
                        title = "Пройти обучение",
                        subtitle = "Короткий тур по возможностям приложения",
                        colorTheme = colorTheme,
                        onClick = onOpenOnboarding
                    )
                }
            }
            // НОВОЕ (поиск по настройкам): показывать ли настройки в общем поиске на главном экране.
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SEARCH_IN_GLOBAL, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Search,
                        title = "Показывать настройки в общем поиске",
                        subtitle = "Результаты поиска настроек будут видны на главном экране поиска",
                        checked = showSettingsInGlobalSearch,
                        onCheckedChange = { viewModel.setShowSettingsInGlobalSearch(it) },
                        colorTheme = colorTheme
                    )
                }
            }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_SWITCH_ACCOUNT, anchorPositions, highlightedAnchor, colorTheme)) {
                    // НОВОЕ (Y): сменить аккаунт без повторного входа.
                    SettingsNavigateRow(
                        icon = Icons.Filled.SwapHoriz,
                        title = "Сменить аккаунт",
                        subtitle = "Переключиться между аккаунтами или добавить новый",
                        colorTheme = colorTheme,
                        onClick = onSwitchAccount
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.ExitToApp,
                        title = stringResource(R.string.settings_logout),
                        subtitle = stringResource(R.string.settings_logout_subtitle),
                        colorTheme = colorTheme,
                        tintOverride = YodoError,
                        onClick = { viewModel.logout(); onLoggedOut() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.settings_delete_account),
                        subtitle = stringResource(R.string.settings_delete_account_subtitle),
                        colorTheme = colorTheme,
                        tintOverride = YodoError,
                        onClick = { showDeleteAccountDialog = true }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════
// НОВОЕ (п.18): диалог автоудаления аккаунта
// ══════════════════════════════════════════════════════════
@Composable
private fun AutoDeleteDialog(
    enabled: Boolean,
    days: Int,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int) -> Unit
) {
    var localEnabled by remember { mutableStateOf(enabled) }
    var localDays by remember { mutableFloatStateOf(days.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Автоудаление аккаунта") },
        text = {
            Column {
                Text(
                    "Если вы не будете заходить в приложение в течение указанного времени, аккаунт будет автоматически удалён.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Включить автоудаление", modifier = Modifier.weight(1f))
                    Switch(
                        checked = localEnabled,
                        onCheckedChange = { localEnabled = it }
                    )
                }
                if (localEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Период неактивности: ${localDays.roundToInt()} дней")
                    Slider(
                        value = localDays,
                        onValueChange = { localDays = it },
                        valueRange = 7f..365f,
                        steps = 51
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(localEnabled, localDays.roundToInt()) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ══════════════════════════════════════════════════════════
// НОВОЕ (п.13): диалог выбора фона чата
// ══════════════════════════════════════════════════════════
@Composable
private fun ChatBackgroundDialog(
    currentType: ChatBackgroundType,
    customPath: String,
    onDismiss: () -> Unit,
    onSelect: (ChatBackgroundType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фон чата") },
        text = {
            Column {
                ChatBackgroundType.entries.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(type) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentType == type,
                            onClick = { onSelect(type) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ChatBackgroundPreview(type = type, customPath = customPath)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(type.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun ChatBackgroundPreview(type: ChatBackgroundType, customPath: String) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    val boxModifier = Modifier
        .size(32.dp)
        .clip(shape)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)

    if (type == ChatBackgroundType.CUSTOM_IMAGE) {
        if (customPath.isNotBlank()) {
            coil.compose.AsyncImage(
                model = customPath,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = boxModifier
            )
        } else {
            Box(
                modifier = boxModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Box(
            modifier = boxModifier.background(app.yodo.messenger.features.chats.chatBackgroundPreviewBrush(type))
        )
    }
}

// ══════════════════════════════════════════════════════════
// НОВОЕ (п.4): диалог управления папками чатов
// ══════════════════════════════════════════════════════════
@Composable
private fun ChatFoldersDialog(
    folders: List<app.yodo.messenger.domain.model.ChatFolder>,
    onDismiss: () -> Unit,
    onAddFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameFolder: (app.yodo.messenger.domain.model.ChatFolder, String) -> Unit,
    onReorderFolder: (app.yodo.messenger.domain.model.ChatFolder, Int) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var folderBeingRenamed by remember { mutableStateOf<app.yodo.messenger.domain.model.ChatFolder?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новая папка") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Название папки") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        onAddFolder(newFolderName)
                        newFolderName = ""
                        showAddDialog = false
                    }
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Отмена") }
            }
        )
    }

    folderBeingRenamed?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderBeingRenamed = null },
            title = { Text("Переименовать папку") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Название папки") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameFolder(folder, renameText.trim())
                        folderBeingRenamed = null
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { folderBeingRenamed = null }) { Text("Отмена") }
            }
        )
    }

    val sortedFolders = folders.sortedBy { it.order }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Папки чатов") },
        text = {
            Column {
                if (sortedFolders.isEmpty()) {
                    Text("Нет папок. Создайте первую папку, чтобы организовать чаты.")
                } else {
                    sortedFolders.forEachIndexed { index, folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                IconButton(
                                    onClick = { onReorderFolder(folder, -1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Выше", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onReorderFolder(folder, 1) },
                                    enabled = index < sortedFolders.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Ниже", modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                folder.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        renameText = folder.name
                                        folderBeingRenamed = folder
                                    }
                            )
                            Text("${folder.chatIds.size} чатов", style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = {
                                renameText = folder.name
                                folderBeingRenamed = folder
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Переименовать")
                            }
                            IconButton(onClick = { onDeleteFolder(folder.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = YodoError)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { showAddDialog = true }) {
                    Text("Добавить папку")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

// ══════════════════════════════════════════════════════════
// Переиспользуемые компоненты
// ══════════════════════════════════════════════════════════
@Composable
private fun PinSetupDialog(
    isPinSet: Boolean,
    currentRequirement: PinRequirement,
    onDismiss: () -> Unit,
    onSavePin: (String, PinRequirement) -> Unit,
    onRequirementChanged: (PinRequirement) -> Unit,
    onDisablePin: () -> Unit,
    currentLockDelaySeconds: Int = 0,
    onLockDelayChanged: (Int) -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var selectedRequirement by remember { mutableStateOf(currentRequirement) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val minDigitsError = stringResource(R.string.pin_dialog_min_digits)
    val mismatchError = stringResource(R.string.pin_dialog_mismatch)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPinSet) stringResource(R.string.pin_dialog_change_title) else stringResource(R.string.pin_dialog_set_title)) },
        text = {
            Column {
                // НОВОЕ: красивый ввод — каждая цифра в своей клеточке.
                Text(stringResource(R.string.pin_dialog_new_pin), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = pin, onPinChange = { pin = it; errorMessage = null }, length = 4)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.pin_dialog_repeat_pin), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = confirmPin, onPinChange = { confirmPin = it; errorMessage = null }, length = 4, isError = errorMessage != null)
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.pin_dialog_when_ask), style = MaterialTheme.typography.labelLarge)
                PinRequirement.entries.forEach { requirement ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedRequirement == requirement,
                                onClick = {
                                    selectedRequirement = requirement
                                    onRequirementChanged(requirement)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRequirement == requirement,
                            onClick = {
                                selectedRequirement = requirement
                                onRequirementChanged(requirement)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(requirement.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // НОВОЕ: выбор времени, через которое приложение блокируется после сворачивания.
                if (selectedRequirement == PinRequirement.ON_BACKGROUND) {
                    val presets = listOf(0 to "Сразу", 30 to "30 сек", 60 to "1 мин", 300 to "5 мин", 900 to "15 мин")
                    var showCustom by remember { mutableStateOf(presets.none { it.first == currentLockDelaySeconds }) }
                    var lockDelay by remember { mutableStateOf(currentLockDelaySeconds) }
                    var customText by remember { mutableStateOf(if (presets.none { it.first == currentLockDelaySeconds }) currentLockDelaySeconds.toString() else "") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Блокировать через", style = MaterialTheme.typography.labelLarge)
                    presets.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = !showCustom && lockDelay == seconds,
                                    onClick = { showCustom = false; lockDelay = seconds; onLockDelayChanged(seconds) }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !showCustom && lockDelay == seconds,
                                onClick = { showCustom = false; lockDelay = seconds; onLockDelayChanged(seconds) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = showCustom, onClick = { showCustom = true })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = showCustom, onClick = { showCustom = true })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Своё время (сек)", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (showCustom) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { new ->
                                val digits = new.filter(Char::isDigit).take(5)
                                customText = digits
                                digits.toIntOrNull()?.let { onLockDelayChanged(it) }
                            },
                            label = { Text("Секунд") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> errorMessage = minDigitsError
                    pin != confirmPin -> errorMessage = mismatchError
                    else -> onSavePin(pin, selectedRequirement)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (isPinSet) {
                    TextButton(onClick = onDisablePin) {
                        Text(stringResource(R.string.pin_dialog_disable), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

// НОВОЕ (скрытые чаты): диалог настройки ложного (decoy) PIN-кода.
@Composable
private fun DecoyPinDialog(
    isDecoyPinSet: Boolean,
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit,
    onDisablePin: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDecoyPinSet) "Изменить ложный PIN" else "Ложный PIN") },
        text = {
            Column {
                Text(
                    "Введите отдельный PIN-код. При входе по нему приложение откроется без скрытых чатов — как будто их нет. Он должен отличаться от основного PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ложный PIN", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = pin, onPinChange = { pin = it; errorMessage = null }, length = 4)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Повторите ложный PIN", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = confirmPin, onPinChange = { confirmPin = it; errorMessage = null }, length = 4, isError = errorMessage != null)
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> errorMessage = "Минимум 4 цифры"
                    pin != confirmPin -> errorMessage = "PIN-коды не совпадают"
                    else -> onSavePin(pin)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (isDecoyPinSet) {
                    TextButton(onClick = onDisablePin) {
                        Text(stringResource(R.string.pin_dialog_disable), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

/** Сколько миллисекунд держится подсветка найденного пункта после перехода из поиска. */
private const val HIGHLIGHT_DURATION_MS = 1600L

/**
 * НОВОЕ (поиск по настройкам): модификатор, который запоминает Y-координату
 * элемента на экране в карте [positions] под ключом [anchorId] — используется,
 * чтобы прокрутить список к найденному пункту после тапа по результату поиска.
 * Координата берётся относительно окна, чего достаточно, поскольку список
 * прокручивается на разницу между текущей и целевой позицией на экране.
 */
private fun Modifier.onSettingsAnchor(
    anchorId: String,
    positions: MutableMap<String, Float>
): Modifier = this.then(
    Modifier.onGloballyPositioned { coordinates ->
        positions[anchorId] = coordinates.positionInWindow().y
    }
)

/**
 * НОВОЕ (поиск по настройкам): подсветка найденного пункта после перехода из
 * результатов поиска. Пока [anchorId] == [highlightedAnchor] — фон элемента
 * плавно подсвечивается акцентным цветом, а затем так же плавно гаснет.
 * Совмещает [onSettingsAnchor] (для скролла) с анимированной заливкой фона.
 */
@Composable
private fun Modifier.settingsSearchAnchor(
    anchorId: String,
    positions: MutableMap<String, Float>,
    highlightedAnchor: String?,
    colorTheme: ColorTheme
): Modifier {
    val isHighlighted = anchorId == highlightedAnchor
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted) colorTheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (isHighlighted) 220 else 500),
        label = "settingsAnchorHighlight"
    )
    return this
        .onSettingsAnchor(anchorId, positions)
        .background(highlightColor, RoundedCornerShape(16.dp))
}

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    colorTheme: ColorTheme
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.settings_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val keyboardController = LocalSoftwareKeyboardController.current
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(colorTheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChanged("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsSearchResultsList(
    results: List<SettingsSearchItem>,
    colorTheme: ColorTheme,
    onResultClick: (SettingsSearchItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.settings_search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            )
        } else {
            Text(
                pluralStringResource(R.plurals.settings_search_results_count, results.size, results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            SettingsCard {
                results.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onResultClick(item) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorTheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "${item.sectionTitle} · ${item.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    colorTheme: ColorTheme,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    Brush.verticalGradient(listOf(colorTheme.primary, colorTheme.accent)),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorTheme.primary
        )
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    colorTheme: ColorTheme
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colorTheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colorTheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickReactionDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    colorTheme: ColorTheme
) {
    val reactions = listOf("👍", "❤️", "😂", "🔥", "👏", "😢", "😡", "🎉", "🤔", "👎")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Быстрая реакция") },
        text = {
            // п.6: смайлы сеткой по 5 в ряд (раньше — одна длинная прокручиваемая
            // строка), текущий выбор подсвечен; после выбора диалог закрывается сам.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.chunked(5).forEach { rowEmojis ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowEmojis.forEach { emoji ->
                            val isSelected = emoji == selected
                            Text(
                                emoji,
                                fontSize = 26.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) colorTheme.primary.copy(alpha = 0.18f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onSelect(emoji)
                                        onDismiss()
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colorTheme: ColorTheme,
    enabled: Boolean = true,
    showIcon: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorTheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colorTheme.primary
                else colorTheme.primary.copy(alpha = 0.4f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.4f
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorTheme.primary)
        )
    }
}

@Composable
private fun SettingsNavigateRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colorTheme: ColorTheme,
    onClick: () -> Unit,
    tintOverride: Color? = null
) {
    val iconTint = tintOverride ?: colorTheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = iconTint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}