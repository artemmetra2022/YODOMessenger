package app.yodo.messenger.features.chats

import app.yodo.messenger.ui.components.SupportBotAvatar
import app.yodo.messenger.ui.components.YodoActionSheet
import app.yodo.messenger.ui.components.YodoMenuAction
import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.FloatingActionButton
import app.yodo.messenger.ui.components.Checkbox
import app.yodo.messenger.ui.components.DropdownMenuItem
import app.yodo.messenger.ui.components.OfficialChannelAvatar
import androidx.compose.foundation.background
import app.yodo.messenger.ui.components.clickable
import app.yodo.messenger.ui.components.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import app.yodo.messenger.data.local.HiddenPinResult
import app.yodo.messenger.data.local.ChatListSortOrder
import app.yodo.messenger.features.settings.PinCellsInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import app.yodo.messenger.ui.components.LocalGlassBackdrop
import app.yodo.messenger.ui.components.glassBackdropSource
import androidx.compose.ui.unit.sp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ChatFolder
import app.yodo.messenger.domain.model.ChatPreview
import app.yodo.messenger.domain.model.ChatType
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.components.glassTint
import app.yodo.messenger.ui.components.liquidGlass
import app.yodo.messenger.ui.components.softMessengerBackdrop
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.LocalInterfaceStyle
import app.yodo.messenger.data.local.InterfaceStyle
import app.yodo.messenger.ui.theme.YodoMotion
import app.yodo.messenger.features.weather.WeatherCompactCard
import app.yodo.messenger.features.weather.WeatherViewModel
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
    val label: String,
    val badge: Int = 0
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
    onWeatherClick: () -> Unit = {},
    // НОВОЕ (чат поддержки): открытие админ-панели поддержки.
    onOpenAdminPanel: () -> Unit = {},
    // НОВОЕ (расширение интерфейса каналов): открытие каталога/рекомендаций каналов.
    onDiscoverChannels: () -> Unit = {},
    // НОВОЕ (админ-функции групп): переход к экрану информации о группе по тапу
    // на бейдж заявок (для владельца/админа — сразу к заявкам, без захода в чат).
    onOpenGroupInfo: (String) -> Unit = {},
    // Экспериментальный интерфейс: список уходит под нижнюю панель (стекло), поэтому её
    // высота приходит снаружи и добавляется в contentPadding списка.
    contentBottomInset: Dp = 0.dp,
    viewModel: ChatListViewModel = hiltViewModel(),
    weatherViewModel: WeatherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val chatFolders by viewModel.chatFolders.collectAsState()
    val compactChatList by viewModel.compactChatList.collectAsState()
    val hideChatListPreviews by viewModel.hideChatListPreviews.collectAsState()
    val weatherCardEnabled by weatherViewModel.weatherCardEnabled.collectAsState()
    val chatListSortOrder by viewModel.chatListSortOrder.collectAsState()
    // НОВОЕ (скрытые чаты): множество ID скрытых чатов для подписей меню.
    val hiddenChatIds by viewModel.hiddenChatIds.collectAsState()
    // НОВОЕ (скрытые чаты): список скрытых чатов и признак PIN для шторки "потянуть вниз".
    val hiddenChats by viewModel.hiddenChats.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
    // БАГ-ФИКС (заголовок главного экрана): состояние сети для «Нет сети…» в топбаре.
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    // НОВОЕ (AF): состояние обновления и жест "потянуть вниз — обновить чаты".
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    // НОВОЕ: настройка "скрывать статус-бар на списке чатов" (переключается в настройках).
    val hideStatusBarOnChatList by viewModel.hideStatusBarOnChatList.collectAsState()
    val colorTheme = LocalColorTheme.current
    val experimentalInterface = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    val glassDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showFabMenu by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    // БАГ-ФИКС (действия меню): снекбар с ошибкой операции (пин/мьют/архив/удаление).
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.actionError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    // НОВОЕ: скрытие системного статус-бара только на этом экране, если включено в
    // настройках. При выходе с экрана (или выключении настройки) бар обязательно
    // возвращается — иначе он остался бы скрытым и на остальных экранах приложения.
    // BEHAVIOR_SHOW_BARS_BY_SWIPE — свайп сверху временно покажет бар, как в обычных
    // полноэкранных приложениях, вместо жёсткого "намертво скрыт".
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(hideStatusBarOnChatList, view) {
        val window = (view.context as? android.app.Activity)?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (hideStatusBarOnChatList && insetsController != null) {
            insetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }
    // НОВОЕ (папки): чат, для которого открыт выбор папки (добавить/убрать из папок).
    var folderPickerChatId by remember { mutableStateOf<String?>(null) }
    // НОВОЕ (папки): id папки, которую редактируем (переименовать/удалить/состав).
    var manageFolderId by remember { mutableStateOf<String?>(null) }

    // НОВОЕ (скрытые чаты): состояние шторки со скрытыми чатами.
    val chatListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showHiddenPinDialog by remember { mutableStateOf(false) }
    var showHiddenWindow by remember { mutableStateOf(false) }
    var showEmptyWindow by remember { mutableStateOf(false) }
    var pullAccum by remember { mutableFloatStateOf(0f) }
    val pullThreshold = 240f
    // Жест "потянуть сверху вниз" на самом верху списка → ОБНОВИТЬ список чатов
    // (раньше здесь открывался список скрытых чатов — по просьбе убрано, сама функция
    // скрытия чатов осталась в меню долгого нажатия).
    val hiddenPullConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = chatListState.firstVisibleItemIndex == 0 &&
                    chatListState.firstVisibleItemScrollOffset == 0
                if (atTop && available.y > 0f) {
                    pullAccum += available.y
                    if (pullAccum > pullThreshold) {
                        pullAccum = 0f
                        viewModel.refresh()
                    }
                } else if (available.y < 0f) {
                    pullAccum = 0f
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        containerColor = if (experimentalInterface) Color.Transparent else MaterialTheme.colorScheme.background,
        snackbarHost = {
            androidx.compose.material3.SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // ИЗМЕНЕНО (по просьбе): расстояние от статус-бара до заголовка
                    // "Yodo Messenger" сделано противоположным тому, что было раньше.
                    // Когда статус-бар ВКЛЮЧЁН (виден) — отступ должен быть МЕНЬШЕ, чем
                    // штатный полный statusBarsPadding() (он ощущался слишком большим):
                    // бер��м реальную высоту статус-бара и вычитаем часть отступа, но не
                    // уходим ниже нуля на случай нулевого инсета (жест-навигация/edge-to-edge
                    // без видимого статус-бара). Когда статус-бар ВЫКЛЮЧЕН (скрыт
                    // настройкой) — отступ, наоборот, УВЕЛИЧЕН (раньше был мелкий
                    // фиксированный 4.dp, из-за чего заголовок оказывался слишком
                    // близко к самому верху экрана).
                    .then(
                        if (experimentalInterface) {
                            // Плавающая плашка: отступ под статус-баром и по бокам снаружи стекла.
                            if (hideStatusBarOnChatList) {
                                Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 6.dp)
                            } else {
                                val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars
                                    .asPaddingValues().calculateTopPadding()
                                Modifier.padding(start = 12.dp, end = 12.dp, top = statusBarHeight + 4.dp, bottom = 6.dp)
                            }
                        } else if (hideStatusBarOnChatList) {
                            Modifier.padding(top = 20.dp)
                        } else {
                            val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars
                                .asPaddingValues().calculateTopPadding()
                            Modifier.padding(top = (statusBarHeight - 12.dp).coerceAtLeast(0.dp))
                        }
                    )
                    .liquidGlass(
                        enabled = experimentalInterface,
                        shape = RoundedCornerShape(26.dp),
                        tint = glassTint(glassDark),
                        dark = glassDark,
                        elevation = 10
                    )
            ) {
                // Заголовок + иконки
                // ИЗМЕНЕНО: вертикальный отступ уменьшен примерно в 2,2 раза (было 8dp)
                // по просьбе — расстояние от верха до "Yodo Messenger" меньше независимо
                // от того, скрыт статус-бар или нет.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // БАГ-ФИКС (заголовок главного экрана): во время загрузки чатов с
                    // сервера показываем «Обновление…» вместо «Yodo Messenger»,
                    // а без сети — «Нет сети…» — пользователь сразу видит реальный
                    // статус приложения, а не вечное имя приложения.
                    val isInitialLoading = uiState is ChatListUiState.Loading
                    val headerText = when {
                        !isNetworkAvailable -> "Нет сети…"
                        isInitialLoading || isRefreshing -> "Обновление…"
                        else -> if (experimentalInterface) "YODO" else "Yodo Messenger"
                    }
                    val headerColor = if (!isNetworkAvailable || isInitialLoading || isRefreshing) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    } else if (experimentalInterface) {
                        colorTheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = headerColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.chat_search_icon_cd))
                    }
                    IconButton(onClick = onProfileClick, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.chat_profile_cd))
                    }
                }
                
                // Горизонтальные табы фильтрации (как в Telegram) + папки чатов
                val allActive = (uiState as? ChatListUiState.Content)?.allActiveChats ?: emptyList()
                
                // Строим список табов: стандартные фильтры + пользовательские папки
                val tabs = buildList {
                    add(FilterTab(ChatFilter.ALL, stringResource(R.string.chat_filter_all)))
                    add(FilterTab(ChatFilter.UNREAD, stringResource(R.string.chat_filter_unread), 
                        badge = allActive.count { it.unreadCount > 0 }))
                    add(FilterTab(ChatFilter.PRIVATE, stringResource(R.string.chat_filter_private)))
                    add(FilterTab(ChatFilter.GROUPS, stringResource(R.string.chat_filter_groups)))
                    // НОВОЕ (расширение интерфейса каналов): отдельный таб «Каналы» —
                    // только чаты типа CHANNEL, где пользователь владелец/админ/подписчик.
                    add(FilterTab(ChatFilter.CHANNELS, stringResource(R.string.chat_filter_channels),
                        badge = allActive.count { it.type == ChatType.CHANNEL }))
                    
                    // НОВОЕ (п.4): добавляем пользовательские папки
                    chatFolders.sortedBy { it.order }.forEach { folder ->
                        val folderChatsCount = allActive.count { it.chatId in folder.chatIds }
                        add(FilterTab(
                            filter = ChatFilter.Folder(folder.id),
                            label = folder.name,
                            badge = folderChatsCount
                        ))
                    }
                }
                
                ChatFilterTabs(
                    tabs = tabs,
                    activeFilter = activeFilter,
                    onTabSelected = { viewModel.setFilter(it) },
                    onAddFolder = { showFolderDialog = true },
                    // НОВОЕ (папки): долгое нажатие на папке — управление (переименовать/удалить/состав).
                    onFolderLongPress = { folderId -> manageFolderId = folderId }
                )
                if (weatherCardEnabled) {
                    WeatherCompactCard(
                        onClick = onWeatherClick,
                        viewModel = weatherViewModel
                    )
                }
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    modifier = Modifier.liquidGlass(
                        enabled = experimentalInterface,
                        shape = CircleShape,
                        tint = colorTheme.primary,
                        dark = glassDark,
                        elevation = 14
                    ),
                    containerColor = if (experimentalInterface) colorTheme.primary.copy(alpha = 0.68f) else colorTheme.primary
                ) {
                    Icon(
                        if (experimentalInterface) Icons.Filled.Edit else Icons.Filled.Add,
                        contentDescription = stringResource(R.string.chat_create_cd),
                        tint = Color.White
                    )
                }
                if (showFabMenu) {
                    YodoActionSheet(title = "Новое общение", subtitle = "Выберите, с чего начать", onDismissRequest = { showFabMenu = false },
                        actions = buildList {
                            add(YodoMenuAction(stringResource(R.string.chat_list_new_chat), Icons.Filled.ChatBubbleOutline, "Создать") { onSearchClick() })
                            add(YodoMenuAction(stringResource(R.string.chat_list_new_group), Icons.Filled.Group, "Создать") { onCreateGroupClick() })
                            add(YodoMenuAction(stringResource(R.string.chat_list_new_channel), Icons.Filled.Campaign, "Создать") { onCreateChannelClick() })
                            add(YodoMenuAction(stringResource(R.string.chat_list_discover_channels), Icons.Filled.Campaign, "Найти") { onDiscoverChannels() })
                            add(YodoMenuAction(stringResource(R.string.chat_list_contacts), Icons.Filled.Contacts, "Найти") { onOpenContacts() })
                            if (viewModel.isSupportAdmin) add(YodoMenuAction("Админ-панель поддержки", Icons.Filled.AdminPanelSettings, "Управление") { onOpenAdminPanel() })
                        })
                }

            }
        }
    ) { padding ->
        // Экспериментальный интерфейс: список проходит под верхней и нижней стеклянными
        // панелями (иначе нечего преломлять), поэтому вместо padding используем contentPadding.
        val glassBackdrop = LocalGlassBackdrop.current
        val listPadding = if (experimentalInterface) {
            PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + contentBottomInset
            )
        } else {
            PaddingValues(0.dp)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (experimentalInterface) Modifier else Modifier.padding(padding))
                .glassBackdropSource(if (experimentalInterface) glassBackdrop else null)
                .softMessengerBackdrop(
                    enabled = experimentalInterface,
                    primary = colorTheme.primary,
                    accent = colorTheme.accent,
                    dark = glassDark
                )
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides null) {
            when (val state = uiState) {
                is ChatListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ChatListUiState.Empty -> {
                    EmptyChatsState(
                        message = stringResource(R.string.chat_list_empty_all),
                        experimental = experimentalInterface,
                        colorTheme = colorTheme,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ChatListUiState.Content -> {
                    if (state.chats.isEmpty()) {
                        // Пустой результат фильтрации
                        EmptyChatsState(
                            message = emptyFilterMessage(activeFilter),
                            experimental = experimentalInterface,
                            colorTheme = colorTheme,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = chatListState,
                            contentPadding = listPadding,
                            modifier = Modifier.fillMaxSize().nestedScroll(hiddenPullConnection)
                        ) {
                            // НОВОЕ (папки): внутри папки — кнопка "Добавить" для быстрого добавления
                            // чатов/групп/каналов и редактирования состава.
                            (activeFilter as? ChatFilter.Folder)?.let { folderFilter ->
                                item(key = "folder_add_row") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { manageFolderId = folderFilter.folderId }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = colorTheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "Добавить чаты в папку",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorTheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
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
                            // НОВОЕ (п.16, оптимизация прокрутки): contentType помогает
                            // LazyColumn переиспользовать композиции однотипных строк.
                            val sortedChats = when (chatListSortOrder) {
                                ChatListSortOrder.RECENT -> state.chats
                                ChatListSortOrder.UNREAD_FIRST -> state.chats.sortedWith(
                                    compareByDescending<ChatPreview> { it.isPinned }
                                        .thenByDescending { it.unreadCount > 0 }
                                        .thenByDescending { it.lastMessageTimestamp }
                                )
                                ChatListSortOrder.ALPHABETICAL -> state.chats.sortedWith(
                                    compareByDescending<ChatPreview> { it.isPinned }
                                        .thenBy { it.title.lowercase(Locale.getDefault()) }
                                )
                            }
                            items(sortedChats, key = { it.chatId }, contentType = { "chat_row" }) { chat ->
                                SwipeableChatListItem(
                                    chat = chat,
                                    colorTheme = colorTheme,
                                    onClick = { onChatClick(chat.chatId) },
                                    onTogglePin = { viewModel.togglePinChat(chat.chatId) },
                                    onToggleMute = { viewModel.toggleMuteChat(chat.chatId) },
                                    onDelete = { viewModel.deleteChat(chat.chatId) },
                                    onClearHistory = { viewModel.clearChatHistory(chat.chatId) },
                                    onToggleArchive = { viewModel.toggleArchiveChat(chat.chatId) },
                                    isHidden = chat.chatId in hiddenChatIds,
                                    onToggleHidden = { viewModel.toggleChatHidden(chat.chatId) },
                                    // НОВОЕ (админ-функции групп): тап по бейджу заявок —
                                    // сразу к экрану группы с заявками.
                                    onRequestsClick = { onOpenGroupInfo(chat.chatId) },
                                    compact = compactChatList,
                                    hidePreview = hideChatListPreviews,
                                    modifier = Modifier.animateItemPlacement(
                                        YodoMotion.emphasized(YodoMotion.DURATION_MEDIUM)
                                    )
                                )
                                if (experimentalInterface) {
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(start = 84.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    )
                                }
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
            // НОВОЕ (AF): индикатор обновления сверху при жесте "потянуть вниз".
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(28.dp),
                    strokeWidth = 3.dp
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = chatListState.firstVisibleItemIndex > 3,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                enter = androidx.compose.animation.fadeIn(YodoMotion.standard()) +
                    androidx.compose.animation.scaleIn(YodoMotion.confirm(), initialScale = 0.75f),
                exit = androidx.compose.animation.fadeOut(YodoMotion.exit()) +
                    androidx.compose.animation.scaleOut(YodoMotion.exit(), targetScale = 0.75f)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (experimentalInterface) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = if (experimentalInterface) 0.dp else 5.dp,
                    modifier = Modifier
                        .liquidGlass(
                            enabled = experimentalInterface,
                            shape = RoundedCornerShape(22.dp),
                            tint = MaterialTheme.colorScheme.surface,
                            dark = glassDark,
                            elevation = 7
                        )
                        .clickable {
                            scope.launch { chatListState.animateScrollToItem(0) }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Наверх",
                            tint = colorTheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Наверх",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorTheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        }
    }

    // НОВОЕ (п.4): диалог создания папки
    if (showFolderDialog) {
        AddFolderDialog(
            onDismiss = { showFolderDialog = false },
            onConfirm = { name ->
                viewModel.addChatFolder(name)
                showFolderDialog = false
            }
        )
    }

    // НОВОЕ (скрытые чаты): ввод PIN после жеста "потянуть вниз".
    if (showHiddenPinDialog) {
        HiddenChatsPinDialog(
            onDismiss = { showHiddenPinDialog = false },
            onSubmit = { pin ->
                scope.launch {
                    val result = viewModel.checkHiddenPin(pin)
                    showHiddenPinDialog = false
                    when (result) {
                        HiddenPinResult.MAIN -> showHiddenWindow = true
                        HiddenPinResult.DECOY, HiddenPinResult.NONE -> showEmptyWindow = true
                    }
                }
            }
        )
    }

    // НОВОЕ (скрытые чаты): окно со скрытыми чатами (верный PIN).
    if (showHiddenWindow) {
        HiddenChatsWindow(
            chats = hiddenChats,
            colorTheme = colorTheme,
            onDismiss = { showHiddenWindow = false },
            onChatClick = { chatId -> showHiddenWindow = false; onChatClick(chatId) },
            onUnhide = { chatId -> viewModel.toggleChatHidden(chatId) }
        )
    }

    // НОВОЕ (скрытые чаты): пустое окно для ложного/неверного PIN.
    if (showEmptyWindow) {
        HiddenChatsEmptyWindow(onDismiss = { showEmptyWindow = false })
    }
}

// ---------------------------------------------------------------------------
// НОВОЕ (скрытые чаты): диалог ввода PIN для шторки
// ---------------------------------------------------------------------------
@Composable
private fun HiddenChatsPinDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Введите PIN-код") },
        text = {
            Column {
                Text(
                    "Введите код, чтобы открыть скрытые чаты.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                PinCellsInput(pin = pin, onPinChange = { pin = it }, length = 4)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = pin.length >= 4) {
                Text("Открыть")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ---------------------------------------------------------------------------
// НОВОЕ (скрытые чаты): окно со скрытыми чатами
// ---------------------------------------------------------------------------
@Composable
private fun HiddenChatsWindow(
    chats: List<ChatPreview>,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onDismiss: () -> Unit,
    onChatClick: (String) -> Unit,
    onUnhide: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Скрытые чаты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                if (chats.isEmpty()) {
                    Text(
                        "Нет скрытых чатов",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(chats, key = { it.chatId }) { chat ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.chatId) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    displayName = chat.title,
                                    photoUrl = chat.avatarUrl,
                                    avatarBase64 = chat.avatarBase64,
                                    size = 44.dp,
                                    userId = chat.otherUserId ?: chat.chatId
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(chat.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(chat.lastMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = { onUnhide(chat.chatId) }) { Text("Вытащить") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// НОВОЕ (скрытые чаты): пустое окно (ложный/неверный PIN)
// ---------------------------------------------------------------------------
@Composable
private fun HiddenChatsEmptyWindow(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Чатов не найдено", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Горизонтальные табы фильтрации + папки
// ---------------------------------------------------------------------------
@Composable
private fun ChatFilterTabs(
    tabs: List<FilterTab>,
    activeFilter: ChatFilter,
    onTabSelected: (ChatFilter) -> Unit,
    onAddFolder: () -> Unit,
    onFolderLongPress: (String) -> Unit = {}
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
            // НОВОЕ (папки): для пользовательских папок доступно долгое нажатие.
            val folderId = (tab.filter as? ChatFilter.Folder)?.folderId
            FilterChip(
                label = tab.label,
                isActive = isActive,
                badge = tab.badge,
                onClick = { onTabSelected(tab.filter) },
                onLongClick = if (folderId != null) ({ onFolderLongPress(folderId) }) else null
            )
        }
        
        // Кнопка добавления папки
        item {
            AddFolderChip(onClick = onAddFolder)
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isActive: Boolean,
    badge: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colorTheme = LocalColorTheme.current
    val experimental = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = if (isActive) colorTheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val glassModifier = Modifier.liquidGlass(
        enabled = experimental,
        shape = RoundedCornerShape(20.dp),
        tint = if (isActive) colorTheme.primary else MaterialTheme.colorScheme.surface,
        dark = dark,
        elevation = if (isActive) 7 else 3
    )
    val clickableModifier = if (onLongClick != null) {
        glassModifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else glassModifier.clickable { onClick() }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (experimental) Color.Transparent else bgColor,
        modifier = clickableModifier
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

// НОВОЕ (п.4): кнопка добавления папки
@Composable
private fun AddFolderChip(onClick: () -> Unit) {
    val colorTheme = LocalColorTheme.current
    val experimental = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (experimental) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .liquidGlass(
                enabled = experimental,
                shape = RoundedCornerShape(20.dp),
                tint = glassTint(dark),
                dark = dark,
                elevation = 3
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Добавить папку",
                tint = colorTheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Папка",
                color = colorTheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// НОВОЕ (п.4): диалог создания папки
@Composable
private fun AddFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая папка") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Название папки") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (folderName.isNotBlank()) onConfirm(folderName.trim()) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// НОВОЕ (папки — добавить чат в папку): выбор одной или нескольких папок для чата.
// Показывает чекбоксы по текущему членству; можно создать новую папку прямо здесь.
@Composable
private fun FolderPickerDialog(
    folders: List<ChatFolder>,
    chatId: String,
    onToggle: (folderId: String, add: Boolean) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в папку") },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text(
                        "У вас пока нет папок. Создайте первую, чтобы сгруппировать чаты.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(folders) { folder ->
                            val inFolder = chatId in folder.chatIds
                            var checked by remember(folder.id, inFolder) { mutableStateOf(inFolder) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checked = !checked
                                        onToggle(folder.id, checked)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        checked = it
                                        onToggle(folder.id, it)
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (showCreate) {
                    var newName by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Название новой папки") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(
                                onClick = { if (newName.isNotBlank()) { onCreateFolder(newName.trim()); newName = ""; showCreate = false } },
                                enabled = newName.isNotBlank()
                            ) { Text("OK") }
                        }
                    )
                } else {
                    TextButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Новая папка")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}

// НОВОЕ (папки — удалить/редактировать состав): управление папкой.
// Переименование, удаление и чеклист всех чатов/групп/каналов для быстрого добавления.
@Composable
private fun ManageFolderDialog(
    folder: ChatFolder,
    allChats: List<ChatPreview>,
    onRename: (String) -> Unit,
    onToggleChat: (chatId: String, add: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(folder.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Папка: ${folder.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название папки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (name.isNotBlank() && name.trim() != folder.name) {
                            TextButton(onClick = { onRename(name.trim()) }) { Text("Сохр.") }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Состав папки — отметьте чаты/группы/каналы:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                if (allChats.isEmpty()) {
                    Text("Нет доступных чатов.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(allChats, key = { it.chatId }) { chat ->
                            val inFolder = chat.chatId in folder.chatIds
                            var checked by remember(chat.chatId, inFolder) { mutableStateOf(inFolder) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checked = !checked
                                        onToggleChat(chat.chatId, checked)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        checked = it
                                        onToggleChat(chat.chatId, it)
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val typeLabel = when (chat.type) {
                                    ChatType.GROUP -> "Группа"
                                    ChatType.CHANNEL -> "Канал"
                                    ChatType.PRIVATE -> "Чат"
                                }
                                Column {
                                    Text(chat.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (confirmDelete) {
                    Text("Удалить папку? Сами чаты не удаляются.", color = Color.Red, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = onDelete) { Text("Удалить", color = Color.Red) }
                        TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Удалить папку", color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}

@Composable
private fun EmptyChatsState(
    message: String,
    experimental: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = modifier
            .padding(24.dp)
            .liquidGlass(
                enabled = experimental,
                shape = RoundedCornerShape(26.dp),
                tint = MaterialTheme.colorScheme.surface,
                dark = dark,
                elevation = 5
            )
            .clip(RoundedCornerShape(26.dp))
            .background(if (experimental) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colorTheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = colorTheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "Новые диалоги появятся здесь",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun emptyFilterMessage(filter: ChatFilter): String = when (filter) {
    ChatFilter.ALL      -> stringResource(R.string.chat_list_empty_all)
    ChatFilter.PRIVATE  -> stringResource(R.string.chat_list_empty_private)
    ChatFilter.GROUPS   -> stringResource(R.string.chat_list_empty_groups)
    // НОВОЕ (расширение интерфейса каналов): пустое состояние для таба «Каналы».
    ChatFilter.CHANNELS -> stringResource(R.string.chat_list_empty_channels)
    ChatFilter.UNREAD   -> stringResource(R.string.chat_list_empty_unread)
    is ChatFilter.Folder -> "В этой папке пока нет чатов"
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
    onToggleArchive: () -> Unit = {},
    isHidden: Boolean = false,
    onToggleHidden: () -> Unit = {},
    // НОВОЕ (админ-функции групп): тап по бейджу заявок на карточке группы.
    onRequestsClick: () -> Unit = {},
    compact: Boolean = false,
    hidePreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    // НОВОЕ (motion): свайп теперь анимируется пружиной вместо мгновенного
    // "прилипания" к пальцу — во время драга следует за пальцем 1:1 (это
    // и должно быть мгновенным), а при отпускании плавно долетает до 0
    // или до порога удаления через Animatable, а не через ручной stateful offset.
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmMenuAction by remember { mutableStateOf<String?>(null) }
    val deleteThreshold = -100f
    val maxDrag = -200f
    val scope = rememberCoroutineScope()

    // Прогресс раскрытия свайпа 0f..1f — управляет и цветом подложки, и
    // масштабом иконки удаления, чтобы жест ощущался отзывчивым, а не бинарным.
    val revealProgress = (offsetX.value / maxDrag).coerceIn(0f, 1f)
    val pastDeleteThreshold = offsetX.value < deleteThreshold

    val deleteIconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pastDeleteThreshold) 1.15f else 0.9f + revealProgress * 0.2f,
        animationSpec = YodoMotion.confirm(),
        label = "deleteIconScale"
    )
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = Color.Red.copy(alpha = 0.12f + revealProgress * 0.18f),
        animationSpec = YodoMotion.standard(),
        label = "swipeTrackColor"
    )

    // БАГ-ФИКС (свайп-удаление чата): детектор горизонтального свайпа раньше висел
    // на пустом Box-сиблинге нулевой высоты — жест физически не мог сработать,
    // поэтому и строка не сдвигалась, и удаление свайпом не работало. Теперь
    // pointerInput стоит на основном Box, в котором лежит контент строки.
    Box(
        modifier = modifier
            .pointerInput(chat.chatId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < deleteThreshold) {
                                // НОВОЕ (motion): улетает влево за край экрана перед удалением,
                                // а не исчезает мгновенно — глаз успевает связать жест с результатом.
                                offsetX.animateTo(-1000f, animationSpec = YodoMotion.exit(YodoMotion.DURATION_FAST))
                                onDelete()
                            } else {
                                offsetX.animateTo(0f, animationSpec = YodoMotion.drag())
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, animationSpec = YodoMotion.drag()) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceIn(maxDrag, 0f))
                        }
                    }
                )
            }
    ) {
        if (revealProgress > 0.02f) {
            Box(
                modifier = Modifier.fillMaxSize().background(trackColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.chat_list_delete_cd),
                    tint = Color.Red,
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = deleteIconScale
                            scaleY = deleteIconScale
                        }
                )
            }
        }
        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            ChatListItem(
                chat = chat, colorTheme = colorTheme,
                onClick = onClick, onLongClick = { showMenu = true },
                onRequestsClick = onRequestsClick,
                isHidden = isHidden,
                compact = compact,
                hidePreview = hidePreview
            )
        }
        if (showMenu) {
            YodoActionSheet(title = chat.title, subtitle = "Действия с перепиской",
                onDismissRequest = { showMenu = false }, actions = listOf(
                    YodoMenuAction(if (chat.isPinned) stringResource(R.string.chat_list_unpin) else stringResource(R.string.chat_list_pin), Icons.Filled.PushPin, "В списке чатов") { onTogglePin() },
                    YodoMenuAction(if (chat.isMuted) stringResource(R.string.chat_list_unmute) else stringResource(R.string.chat_list_mute), if (chat.isMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "Уведомления") { onToggleMute() },
                    YodoMenuAction(if (chat.isArchived) stringResource(R.string.chat_list_unarchive) else stringResource(R.string.chat_list_archive_action), if (chat.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive, "В списке чатов") { onToggleArchive() },
                    YodoMenuAction(if (isHidden) "Показать чат" else "Скрыть чат", if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Приватность") { onToggleHidden() },
                    YodoMenuAction(stringResource(R.string.chat_list_clear), Icons.Filled.CleaningServices, "Опасные действия", destructive = true) { confirmMenuAction = "clear" },
                    YodoMenuAction(stringResource(R.string.chat_list_delete), Icons.Filled.Delete, "Опасные действия", destructive = true) { confirmMenuAction = "delete" }
                ))
        }
        confirmMenuAction?.let { action ->
            AlertDialog(onDismissRequest = { confirmMenuAction = null },
                title = { Text(if (action == "clear") "Очистить историю?" else "Удалить чат?") },
                text = { Text(if (action == "clear") "Будут удалены сообщения, доступные вам для удаления. Отменить это действие нельзя."
                    else "Удаление может затронуть историю сообщений. В группе или канале вы также выйдете из числа участников. Продолжить?") },
                confirmButton = { TextButton(onClick = { confirmMenuAction = null; if (action == "clear") onClearHistory() else onDelete() }) {
                    Text(if (action == "clear") "Очистить" else "Удалить", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { confirmMenuAction = null }) { Text("Отмена") } })
        }

    }
}

// ---------------------------------------------------------------------------
// ChatListItem
// ---------------------------------------------------------------------------
@Composable
private fun ChatListItem(
    chat: ChatPreview,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    // НОВОЕ (админ-функции групп): тап по бейджу заявок открывает экран группы.
    onRequestsClick: () -> Unit = {},
    isHidden: Boolean = false,
    compact: Boolean = false,
    hidePreview: Boolean = false
) {
    // ИСПРАВЛЕНО (логотип поддержки): чат поддержки (support_<uid>) раньше
    // попадал в ветку «Избранное» и показывал иконку-закладку. Теперь у него свой логотип.
    val isSupportChat = chat.chatId.startsWith("support_")
    val isSavedChat = !isSupportChat && chat.type == ChatType.PRIVATE && chat.otherUserId == null
    val isChannel = chat.type == ChatType.CHANNEL
    val experimentalInterface = LocalInterfaceStyle.current == InterfaceStyle.EXPERIMENTAL
    val avatarSize = when {
        compact -> 46.dp
        experimentalInterface -> 54.dp
        else -> 56.dp
    }
    val glassDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // НОВОЕ (иерархия): единственный флаг, который решает "жирность" всей строки.
    // Непрочитанный чат должен читаться с одного взгляда — не только по бейджу
    // с числом, но и по весу заголовка и цвету превью. Прочитанные чаты уходят
    // на второй план, чтобы глаз сам находил, куда нужно вернуться.
    val hasUnread = chat.unreadCount > 0
    val titleColor = MaterialTheme.colorScheme.onSurface
    val previewColor = if (hasUnread) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

    // НОВОЕ (motion): строка чата слегка "прижимается" при нажатии — тот же
    // эффект, что и у пузырей сообщений, поддерживает единый тактильный язык
    // приложения между списком и самим чатом.
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = YodoMotion.standard(YodoMotion.DURATION_INSTANT),
        label = "chatRowPressScale"
    )
    val rowBackground by androidx.compose.animation.animateColorAsState(
        targetValue = if (hasUnread) {
            colorTheme.primary.copy(alpha = if (experimentalInterface) 0.035f else 0.05f)
        } else {
            Color.Transparent
        },
        animationSpec = YodoMotion.standard(YodoMotion.DURATION_MEDIUM),
        label = "chatRowBackground"
    )

    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (experimentalInterface) Modifier.padding(horizontal = 8.dp, vertical = 3.dp) else Modifier)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(rowBackground)
            .liquidGlass(
                enabled = experimentalInterface,
                shape = RoundedCornerShape(18.dp),
                tint = if (hasUnread) colorTheme.primary else glassTint(glassDark),
                dark = glassDark,
                elevation = 3
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            // НОВОЕ (иерархия): высота строки стабилизирована к компактным ~72dp
            // (56dp аватар + 8dp сверху/снизу) вместо "плавающего" padding,
            // который раньше давал заметный разброс высоты между строками
            // с разным количеством значков в заголовке.
            .heightIn(min = if (compact) 62.dp else 72.dp)
            .padding(
                horizontal = if (experimentalInterface) 12.dp else 16.dp,
                vertical = if (compact) 5.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        if (isSupportChat) {
            SupportBotAvatar(size = avatarSize, accent = colorTheme.primary)
        } else if (isSavedChat) {
            Box(
                modifier = Modifier.size(avatarSize).clip(CircleShape)
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
                    size = avatarSize,
                    userId = chat.chatId
                )
            } else if (chat.isVerified) {
                // Официальный канал — фирменный аватар вместо буквы «Y»
                OfficialChannelAvatar(size = avatarSize)
            } else {
                UserAvatar(
                    displayName = chat.title,
                    photoUrl = null,
                    avatarBase64 = null,
                    size = avatarSize,
                    userId = chat.chatId
                )
            }
        } else {
            Box {
                UserAvatar(
                    displayName = chat.title,
                    photoUrl = chat.avatarUrl,
                    avatarBase64 = chat.avatarBase64,
                    size = avatarSize,
                    userId = chat.otherUserId ?: chat.chatId
                )
                if (chat.isOnline) {
                    OnlineStatusDot(chat)
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
                    // НОВОЕ (иерархия): статусные иконки (замок/мьют/пин) сведены в один
                    // приглушённый визуальный "вес" — они вторичны по отношению к имени
                    // и не должны спорить с ним за внимание. Раньше каждая иконка была
                    // самостоятельным ярким акцентом (в т.ч. цветом темы у замка).
                    if (isHidden) {
                        Icon(Icons.Filled.Lock, contentDescription = "Скрытый чат",
                            modifier = Modifier.size(13.dp).padding(end = 3.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    }
                    // НОВОЕ (замок у скрытых групп): отдельный замок — специально для
                    // групп/каналов с accessMode = HIDDEN (не видны в поиске, доступ
                    // только по ссылке/приглашению/QR). Независим от isHidden выше
                    // (PIN-скрытие чата) — оба замка могут стоять одновременно.
                    if (chat.isHiddenAccessGroup) {
                        Icon(Icons.Filled.Lock, contentDescription = "Скрытая группа",
                            modifier = Modifier.size(13.dp).padding(end = 3.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    }
                    if (chat.isMuted) {
                        Icon(Icons.Filled.NotificationsOff, contentDescription = null,
                            modifier = Modifier.size(13.dp).padding(end = 3.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    }
                    if (chat.isPinned) {
                        Icon(Icons.Filled.PushPin, contentDescription = null,
                            modifier = Modifier.size(13.dp).padding(end = 3.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    }
                    Text(
                        chat.title,
                        style = MaterialTheme.typography.bodyLarge,
                        // НОВОЕ (иерархия): непрочитанный чат — SemiBold и полная непрозрачность;
                        // прочитанный — Medium. Разница едва заметна построчно, но мгновенно
                        // читается при взгляде на список целиком, как в почтовых клиентах.
                        fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Medium,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (chat.isVerified) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = "Верифицирован",
                            tint = colorTheme.primary,
                            modifier = Modifier.padding(start = 4.dp).size(17.dp)
                        )
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
                            // НОВОЕ (иерархия): время — акцентного цвета темы у непрочитанных,
                            // нейтрально-серое у прочитанных. Раньше было одинаково серым
                            // всегда, и глазу нечем было "зацепиться" в правом столбце.
                            fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (hasUnread) colorTheme.primary else Color.Gray
                        )
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                        if (chat.lastMessageSenderId != null && chat.lastMessageSenderId == currentUid) {
                            LastMessageStatusIcon(chat.lastMessageStatus)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Нижняя строка
            Row(verticalAlignment = Alignment.CenterVertically) {
                // При включённом «Скрывать текст сообщений» черновик не показываем вовсе:
                // один префикс «Черновик:» всё равно выдавал, что переписка есть.
                if (!hidePreview && chat.draftText.isNotBlank()) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Medium)) {
                                append(stringResource(R.string.chat_list_draft_prefix))
                            }
                            withStyle(SpanStyle(color = previewColor)) {
                                append(chat.draftText)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // БАГ-ФИКС (превью шифрованного чата): текст последнего сообщения
                    // показывается открыто (lastMessagePlain), а замок — отдельной
                    // приглушённой иконкой перед текстом, только у E2EE-личных чатов.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (chat.isEncryptedPreview) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Зашифрованный чат",
                                modifier = Modifier.size(12.dp).padding(end = 3.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                        Text(
                            // При включённом «Скрывать текст сообщений» превью не выводим вовсе:
                            // заглушка «Сообщение скрыто» сама сообщала, что переписка есть.
                            if (hidePreview) {
                                ""
                            } else {
                                chat.lastMessage.ifBlank { if (isChannel) stringResource(R.string.chat_list_channel_label) else "" }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            // НОВОЕ (иерархия): превью последнего сообщения — вторичный текст.
                            // У непрочитанных чуть темнее (78% альфы), у прочитанных заметно
                            // приглушённей (55%), так список "расслаивается" на то, что важно
                            // прямо сейчас, и то, что уже обработано.
                            color = previewColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                // НОВОЕ (админ-функции групп): бейдж ожидающих заявок на вступление —
                // виден только владельцу/админам группы (у остальных поле всегда 0).
                // Лёгкий «приглушённый» стиль, чтобы не спорить с бейджем непрочитанных.
                androidx.compose.animation.AnimatedVisibility(
                    visible = chat.type == ChatType.GROUP && chat.pendingRequestsCount > 0,
                    enter = androidx.compose.animation.scaleIn(
                        animationSpec = YodoMotion.confirm(),
                        initialScale = 0.4f
                    ) + androidx.compose.animation.fadeIn(YodoMotion.standard(YodoMotion.DURATION_FAST)),
                    exit = androidx.compose.animation.scaleOut(
                        animationSpec = YodoMotion.exit(),
                        targetScale = 0.4f
                    ) + androidx.compose.animation.fadeOut(YodoMotion.exit())
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp)
                            .clip(CircleShape)
                            .background(colorTheme.primary.copy(alpha = 0.14f))
                            // НОВОЕ (админ-функции групп): бейдж кликабелен — ведёт
                            // прямо к заявкам (экран информации о группе).
                            .clickable { onRequestsClick() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.HowToReg,
                            contentDescription = "Заявки на вступление",
                            tint = colorTheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (chat.pendingRequestsCount > 99) "99+" else chat.pendingRequestsCount.toString(),
                            color = colorTheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                run {
                    // НОВОЕ (motion): бейдж непрочитанных появляется/меняет число с лёгким
                    // всплытием — сообщает "здесь что-то изменилось" тем же языком,
                    // которым бейджи говорят на иконке приложения и в уведомлениях.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = chat.unreadCount > 0,
                        enter = androidx.compose.animation.scaleIn(
                            animationSpec = YodoMotion.confirm(),
                            initialScale = 0.4f
                        ) + androidx.compose.animation.fadeIn(YodoMotion.standard(YodoMotion.DURATION_FAST)),
                        exit = androidx.compose.animation.scaleOut(
                            animationSpec = YodoMotion.exit(),
                            targetScale = 0.4f
                        ) + androidx.compose.animation.fadeOut(YodoMotion.exit())
                    ) {
                        Box(
                            modifier = Modifier.padding(start = 6.dp)
                                .clip(CircleShape).background(colorTheme.primary)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
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

/**
 * НОВОЕ (баг 12): посекундный "тик" для статусов онлайн. Каждый элемент списка, где
 * читается этот state, пересчитывается раз в секунду — подпись "был(а) N мин назад"
 * обновляется в реальном времени, а "зависший" статус "в сети" (когда процесс
 * собеседника убит системой без onStop) гаснет сам в пределах секунды после порога
 * устаревания, не дожидаясь событий Firestore.
 */
@Composable
private fun rememberNowTick(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** НОВОЕ (баг 12): зелёная точка с посекундной проверкой устаревания статуса. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.OnlineStatusDot(chat: ChatPreview) {
    val now = rememberNowTick()
    val effectivelyOnline = chat.isOnline &&
        (chat.lastSeenMillis == 0L ||
            (now - chat.lastSeenMillis) <= PresenceRepository.PRESENCE_STALE_THRESHOLD_MILLIS)
    if (effectivelyOnline) {
        Box(
            modifier = Modifier.size(14.dp).align(Alignment.BottomEnd)
                .clip(CircleShape).background(MaterialTheme.colorScheme.background)
                .padding(2.dp).clip(CircleShape).background(YodoOnline)
        )
    }
}

@Composable
private fun PresenceStatusText(chat: ChatPreview, modifier: Modifier = Modifier) {
    val now = rememberNowTick()
    // НОВОЕ (баг 12): статус пересчитывается каждую секунду — и "в сети", и "был(а)…"...
    val effectivelyOnline = chat.isOnline &&
        (chat.lastSeenMillis == 0L ||
            (now - chat.lastSeenMillis) <= PresenceRepository.PRESENCE_STALE_THRESHOLD_MILLIS)
    when {
        effectivelyOnline -> Text(
            "в сети",
            style = MaterialTheme.typography.labelMedium,
            color = YodoOnline,
            maxLines = 1,
            modifier = modifier
        )
        // «был(а) ...» в списке чатов не показываем — только в самом чате и в профиле.
    }
}

/**
 * ИСПРАВЛЕНО (индикатор доставлено/прочитано в списке чатов): раньше здесь была своя
 * упрощённая логика ("READ" -> две галочки, всё остальное -> одна), которая не отличала
 * "отправляется"/"не отправлено"/"доставлено" и не была синхронизирована по смыслу и
 * цвету с индикатором внутри чата (см. ChatScreen). Теперь используется тот же набор
 * иконок/цветов, что и в самом чате, а рядом с "в сети" — как и просили — статус
 * пересчитывается раз в секунду через rememberNowTick(), а не только по приходу нового
 * снапшота Firestore, так что список не "подвисает" со старой галочкой.
 */
@Composable
private fun LastMessageStatusIcon(rawStatus: String?) {
    val status = app.yodo.messenger.domain.model.MessageStatus.entries.firstOrNull { it.name == rawStatus }
        ?: app.yodo.messenger.domain.model.MessageStatus.SENDING
    MessageReceiptIndicator(status, compact = true, neutralColor = MaterialTheme.colorScheme.onSurfaceVariant)

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