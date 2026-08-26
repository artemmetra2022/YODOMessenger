package app.yodo.messenger.features.chats

import app.yodo.messenger.ui.components.OfficialChannelAvatar
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import app.yodo.messenger.data.local.HiddenPinResult
import app.yodo.messenger.features.settings.PinCellsInput
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ChatFolder
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
    // НОВОЕ (чат поддержки): открытие админ-панели поддержки.
    onOpenAdminPanel: () -> Unit = {},
    // НОВОЕ (расширение интерфейса каналов): открытие каталога/рекомендаций каналов.
    onDiscoverChannels: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val chatFolders by viewModel.chatFolders.collectAsState()
    // НОВОЕ (скрытые чаты): множество ID скрытых чатов для подписей меню.
    val hiddenChatIds by viewModel.hiddenChatIds.collectAsState()
    // НОВОЕ (скрытые чаты): список скрытых чатов и признак PIN для шторки "потянуть вниз".
    val hiddenChats by viewModel.hiddenChats.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
    val colorTheme = LocalColorTheme.current
    var showFabMenu by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
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
    // НОВОЕ (AF): состояние обновления и жест "потянуть вниз — обновить чаты".
    val isRefreshing by viewModel.isRefreshing.collectAsState()
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
                    // НОВОЕ (расширение интерфейса каналов): пункт «Каталог каналов» в FAB-меню —
                    // рекомендации и подборки каналов по категориям.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_list_discover_channels)) },
                        leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
                        onClick = { showFabMenu = false; onDiscoverChannels() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_list_contacts)) },
                        leadingIcon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
                        onClick = { showFabMenu = false; onOpenContacts() }
                    )
                    // НОВОЕ (чат поддержки): пункт "Админ-панель поддержки" — только для админов.
                    if (viewModel.isSupportAdmin) {
                        DropdownMenuItem(
                            text = { Text("Админ-панель поддержки") },
                            leadingIcon = { Icon(Icons.Filled.SupportAgent, contentDescription = null) },
                            onClick = { showFabMenu = false; onOpenAdminPanel() }
                        )
                    }
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
                        LazyColumn(
                            state = chatListState,
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
                            items(state.chats, key = { it.chatId }) { chat ->
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
                                    onToggleHidden = { viewModel.toggleChatHidden(chat.chatId) }
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
            // НОВОЕ (AF): индикатор обновления сверху при жесте "потянуть вниз".
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(28.dp),
                    strokeWidth = 3.dp
                )
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
// Го��изонтальные табы фильтрации + папки
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
    val bgColor = if (isActive) colorTheme.primary else colorTheme.primary.copy(alpha = 0.12f)
    val textColor = if (isActive) Color.White else colorTheme.primary

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = if (onLongClick != null)
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        else Modifier.clickable { onClick() }
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
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorTheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.clickable { onClick() }
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
    onToggleHidden: () -> Unit = {}
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
                onClick = onClick, onLongClick = { showMenu = true },
                isHidden = isHidden
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text(if (chat.isPinned) stringResource(R.string.chat_list_unpin) else stringResource(R.string.chat_list_pin)) }, onClick = { showMenu = false; onTogglePin() })
            DropdownMenuItem(text = { Text(if (chat.isMuted) stringResource(R.string.chat_list_unmute) else stringResource(R.string.chat_list_mute)) }, onClick = { showMenu = false; onToggleMute() })
            DropdownMenuItem(text = { Text(if (chat.isArchived) stringResource(R.string.chat_list_unarchive) else stringResource(R.string.chat_list_archive_action)) }, onClick = { showMenu = false; onToggleArchive() })
            DropdownMenuItem(text = { Text(stringResource(R.string.chat_list_clear)) }, onClick = { showMenu = false; onClearHistory() })
            // НОВОЕ (скрытые чаты): скрыть/показать чат (виден только под основным PIN).
            DropdownMenuItem(text = { Text(if (isHidden) "Показать чат" else "Скрыть чат") }, onClick = { showMenu = false; onToggleHidden() })
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
    onLongClick: () -> Unit,
    isHidden: Boolean = false
) {
    // ИСПРАВЛЕНО (логотип поддержки): чат поддержки (support_<uid>) раньше
    // попадал в ветку «Избранное» и показывал иконку-закладку. Теперь у него свой логотип.
    val isSupportChat = chat.chatId.startsWith("support_")
    val isSavedChat = !isSupportChat && chat.type == ChatType.PRIVATE && chat.otherUserId == null
    val isChannel = chat.type == ChatType.CHANNEL

    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар
        if (isSupportChat) {
            // НОВОЕ (логотип поддержки): фирменный знак поддержки вместо закладки.
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape)
                    .background(colorTheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_support_logo),
                    contentDescription = "Поддержка YodoMessenger",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (isSavedChat) {
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
            } else if (chat.isVerified) {
                // Официальный канал — фирменный аватар вместо буквы «Y»
                OfficialChannelAvatar(size = 56.dp)
            } else {
                UserAvatar(
                    displayName = chat.title,
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
                    // НОВОЕ (скрытые чаты): маркер-замок у скрытого чата (виден только под основным PIN).
                    if (isHidden) {
                        Icon(Icons.Filled.Lock, contentDescription = "Скрытый чат",
                            modifier = Modifier.size(14.dp).padding(end = 3.dp), tint = colorTheme.primary)
                    }
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