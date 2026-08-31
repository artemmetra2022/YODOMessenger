package app.yodo.messenger.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.data.local.ChatBackgroundType
import app.yodo.messenger.domain.model.ChatFolder
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (разделение настроек по категориям): «Чаты» — отправка по Enter,
 * клавиатура, автозагрузка, опросы, быстрая реакция, фон чата, папки чатов и
 * скрытие статус-бара в списке чатов. Логика и внешний вид перенесены из
 * бывшего монолитного SettingsScreen.kt без изменений поведения.
 */
@Composable
fun ChatsSettingsScreen(
    onBackClick: () -> Unit,
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val hideKeyboardOnSend by viewModel.hideKeyboardOnSend.collectAsState()
    val hideKeyboardOnScroll by viewModel.hideKeyboardOnScroll.collectAsState()
    val quickReaction by viewModel.quickReaction.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val advancedPollsEnabled by viewModel.advancedPollsEnabled.collectAsState()
    val chatBackgroundType by viewModel.chatBackgroundType.collectAsState()
    val chatBackgroundCustomPath by viewModel.chatBackgroundCustomPath.collectAsState()
    val chatFolders by viewModel.chatFolders.collectAsState()
    val hideStatusBarOnChatList by viewModel.hideStatusBarOnChatList.collectAsState()

    var showChatBackgroundDialog by remember { mutableStateOf(false) }
    var showChatFoldersDialog by remember { mutableStateOf(false) }
    var showQuickReactionDialog by remember { mutableStateOf(false) }

    val colorTheme = LocalColorTheme.current
    val listState = rememberLazyListState()
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }

    suspend fun scrollAndHighlight(anchorId: String) {
        highlightedAnchor = anchorId
        anchorPositions[anchorId]?.let { listState.animateScrollBy(it - 24f) }
        kotlinx.coroutines.delay(SETTINGS_HIGHLIGHT_DURATION_MS)
        if (highlightedAnchor == anchorId) highlightedAnchor = null
    }

    LaunchedEffect(initialAnchorId) {
        if (initialAnchorId != null) {
            kotlinx.coroutines.delay(250)
            scrollAndHighlight(initialAnchorId)
        }
    }

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
        topBar = {
            TopAppBar(
                title = { Text("Чаты", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
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

            // НОВОЕ: скрытие системного статус-бара (время/батарея) на экране списка чатов.
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_HIDE_STATUS_BAR_CHAT_LIST, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Fullscreen,
                        title = "Скрывать статус-бар в списке чатов",
                        subtitle = "Системная панель времени и батареи будет полностью скрыта на главном экране",
                        checked = hideStatusBarOnChatList,
                        onCheckedChange = { viewModel.setHideStatusBarOnChatList(it) },
                        colorTheme = colorTheme
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Диалог выбора фона чата
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
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentType == type, onClick = { onSelect(type) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.displayName)
                        }
                        ChatBackgroundPreview(type = type, customPath = customPath)
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
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                when (type) {
                    ChatBackgroundType.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant
                    ChatBackgroundType.GRADIENT_1 -> LocalColorTheme.current.primary.copy(alpha = 0.3f)
                    ChatBackgroundType.GRADIENT_2 -> LocalColorTheme.current.secondary.copy(alpha = 0.3f)
                    ChatBackgroundType.GRADIENT_3 -> LocalColorTheme.current.primary.copy(alpha = 0.5f)
                    ChatBackgroundType.GRADIENT_4 -> LocalColorTheme.current.secondary.copy(alpha = 0.5f)
                    ChatBackgroundType.CUSTOM_IMAGE -> MaterialTheme.colorScheme.surfaceVariant
                },
                RoundedCornerShape(8.dp)
            )
    )
}

// ══════════════════════════════════════════════════════════
// Диалог управления папками чатов
// ══════════════════════════════════════════════════════════
@Composable
private fun ChatFoldersDialog(
    folders: List<ChatFolder>,
    onDismiss: () -> Unit,
    onAddFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameFolder: (ChatFolder, String) -> Unit,
    onReorderFolder: (ChatFolder, Int) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var folderBeingRenamed by remember { mutableStateOf<ChatFolder?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; newFolderName = "" },
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
                    if (newFolderName.isNotBlank()) onAddFolder(newFolderName.trim())
                    newFolderName = ""
                    showAddDialog = false
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; newFolderName = "" }) { Text("Отмена") }
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
                    if (renameText.isNotBlank()) onRenameFolder(folder, renameText.trim())
                    folderBeingRenamed = null
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { folderBeingRenamed = null }) { Text("Отмена") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Папки чатов") },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text("Нет папок. Создайте первую папку, чтобы организовать чаты.")
                } else {
                    folders.sortedBy { it.order }.forEachIndexed { index, folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, fontWeight = FontWeight.Medium)
                                Text("${folder.chatIds.size} чатов", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { onReorderFolder(folder, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Вверх")
                            }
                            IconButton(onClick = { onReorderFolder(folder, 1) }, enabled = index < folders.size - 1) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз")
                            }
                            TextButton(onClick = { folderBeingRenamed = folder; renameText = folder.name }) {
                                Text("Изм.")
                            }
                            TextButton(onClick = { onDeleteFolder(folder.id) }) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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
// Диалог выбора быстрой реакции
// ══════════════════════════════════════════════════════════
@Composable
private fun QuickReactionDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme
) {
    val reactions = listOf("👍", "❤️", "😂", "🔥", "👏", "😢", "😡", "🎉", "🤔", "👎")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Быстрая реакция") },
        text = {
            // п.6: смайлы сеткой по 5 в ряд, текущий выбор подсвечен; после
            // выбора диалог закрывается сам.
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
