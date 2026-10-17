package app.yodo.messenger.features.settings

import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.FilterChip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.yodo.messenger.ui.components.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.R
import app.yodo.messenger.features.canvas.BoardColors
import app.yodo.messenger.features.canvas.BoardInkWidths
import app.yodo.messenger.features.canvas.BoardPreviewHeight
import app.yodo.messenger.features.canvas.BoardSettings
import app.yodo.messenger.features.canvas.CanvasGrid
import app.yodo.messenger.features.canvas.CanvasSettingsStore
import app.yodo.messenger.ui.theme.LocalColorTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BoardSettingsViewModel @Inject constructor(private val store: CanvasSettingsStore) : ViewModel() {
    val settings: StateFlow<BoardSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardSettings())
    fun showInChat(v: Boolean) { viewModelScope.launch { store.setShowInChat(v) } }
    fun previewHeight(v: BoardPreviewHeight) { viewModelScope.launch { store.setPreviewHeight(v) } }
    fun collapsedInChat(v: Boolean) { viewModelScope.launch { store.setCollapsedInChat(v) } }
    fun inkColor(v: Int) { viewModelScope.launch { store.setInkColor(v) } }
    fun inkWidth(v: Int) { viewModelScope.launch { store.setInkWidth(v) } }
    fun shapeAssist(v: Boolean) { viewModelScope.launch { store.setShapeAssist(v) } }
    fun snapToGrid(v: Boolean) { viewModelScope.launch { store.setSnapToGrid(v) } }
    fun defaultGrid(v: CanvasGrid) { viewModelScope.launch { store.setDefaultGrid(v) } }
    fun haptics(v: Boolean) { viewModelScope.launch { store.setHaptics(v) } }
    fun reset() { viewModelScope.launch { store.reset() } }
}

/** Раздел настроек «Интерактивная доска»: доска в чате, рисование по умолчанию, новые доски. */
@Composable
fun BoardSettingsScreen(
    onBackClick: () -> Unit,
    initialAnchorId: String? = null,
    onOpenBoard: (String, String) -> Unit = { _, _ -> },
    libraryViewModel: CanvasLibraryViewModel = hiltViewModel(),
    viewModel: BoardSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val library by libraryViewModel.state.collectAsState()
    var renameBoard by remember { mutableStateOf<app.yodo.messenger.features.canvas.CanvasSavedBoard?>(null) }
    var deleteBoard by remember { mutableStateOf<app.yodo.messenger.features.canvas.CanvasSavedBoard?>(null) }
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner,libraryViewModel) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)libraryViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(library.boards) {
        if(renameBoard!=null && library.boards.none { it==renameBoard })renameBoard=null
        if(deleteBoard!=null && library.boards.none { it==deleteBoard })deleteBoard=null
    }
    val colorTheme = LocalColorTheme.current
    val listState = rememberLazyListState()
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(initialAnchorId) {
        if (initialAnchorId != null) {
            kotlinx.coroutines.delay(250)
            highlightedAnchor = initialAnchorId
            anchorPositions[initialAnchorId]?.let { listState.animateScrollBy(it - 24f) }
            kotlinx.coroutines.delay(SETTINGS_HIGHLIGHT_DURATION_MS)
            if (highlightedAnchor == initialAnchorId) highlightedAnchor = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Интерактивная доска", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SettingsSectionHeader(icon=Icons.Filled.Edit,title="Сохранённые доски",colorTheme=colorTheme,
                    modifier=Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_BOARD_LIBRARY,anchorPositions,highlightedAnchor,colorTheme))
                Text("Все локальные доски текущего аккаунта на этом устройстве. Нажмите «Открыть», чтобы изменить рисунок.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
                if(library.loading || library.busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=20.dp))
                if(library.boards.isEmpty() && !library.loading)Text("Сохранённых досок пока нет",modifier=Modifier.padding(20.dp))
                library.error?.let { message ->
                    Text(message,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=20.dp,vertical=8.dp))
                }
                TextButton(enabled=!library.busy,onClick=libraryViewModel::refresh,modifier=Modifier.padding(horizontal=12.dp).heightIn(min=48.dp)) {Text("Обновить список")}
            }
            items(library.boards,key={"${it.storageChatHash}/${it.id}"}) { entry ->
                CanvasSavedBoardCard(entry,!library.busy,
                    {entry.chatId?.let { onOpenBoard(it,entry.id) }},{renameBoard=entry},{deleteBoard=entry})
            }

            item {
                SettingsSectionHeader(icon = Icons.Filled.Visibility, title = "Доска в чате", colorTheme = colorTheme,
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_BOARD_IN_CHAT, anchorPositions, highlightedAnchor, colorTheme))
            }
            item {
                SettingsCard {
                    SettingsToggleRow(Icons.Filled.Visibility, "Показывать доску в чате",
                        "Общая доска видна прямо в чате со всеми изменениями; чтобы что-то добавить, нужно нажать на неё",
                        settings.showInChat, viewModel::showInChat, colorTheme)
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    BoardChoiceRow("Размер доски в чате", "Высота карточки", enabled = settings.showInChat) {
                        BoardPreviewHeight.entries.forEach { h ->
                            FilterChip(selected = settings.previewHeight == h, enabled = settings.showInChat, onClick = { viewModel.previewHeight(h) }, label = { Text(h.title) })
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    SettingsToggleRow(Icons.Filled.UnfoldLess, "Сворачивать по умолчанию",
                        "Показывать только заголовок доски; развернуть можно нажатием", settings.collapsedInChat,
                        viewModel::collapsedInChat, colorTheme, enabled = settings.showInChat)
                }
            }
            item {
                SettingsSectionHeader(icon = Icons.Filled.Edit, title = "Рисование", colorTheme = colorTheme,
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_BOARD_DRAWING, anchorPositions, highlightedAnchor, colorTheme))
            }
            item {
                SettingsCard {
                    BoardChoiceRow("Цвет линии по умолчанию", "Выбирается при открытии доски") {
                        BoardColors.palette.forEach { (name, value) ->
                            Box(
                                Modifier.size(40.dp).semantics { contentDescription = "Цвет: $name" }.clickable { viewModel.inkColor(value) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(26.dp).background(Color(value), CircleShape)
                                    .border(if (settings.inkColor == value) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    BoardChoiceRow("Толщина линии по умолчанию", "В пикселях доски") {
                        BoardInkWidths.forEach { w ->
                            FilterChip(selected = settings.inkWidth == w.toFloat(), onClick = { viewModel.inkWidth(w) }, label = { Text("$w px") })
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    SettingsToggleRow(Icons.Filled.AutoFixHigh, "Умные фигуры",
                        "Задержите палец после штриха карандашом — он выровняется в линию или фигуру", settings.shapeAssist, viewModel::shapeAssist, colorTheme)
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    SettingsToggleRow(Icons.Filled.GridOn, "Привязка к сетке",
                        "Притягивать начало и конец линий и фигур к узлам сетки", settings.snapToGrid, viewModel::snapToGrid, colorTheme)
                    HorizontalDivider(Modifier.padding(start = 52.dp))
                    SettingsToggleRow(Icons.Filled.Vibration, "Вибрация при выравнивании",
                        "Короткий отклик, когда штрих стал ровной фигурой", settings.haptics, viewModel::haptics, colorTheme, enabled = settings.shapeAssist)
                }
            }
            item {
                SettingsSectionHeader(icon = Icons.Filled.GridOn, title = "Новые доски", colorTheme = colorTheme,
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_BOARD_NEW, anchorPositions, highlightedAnchor, colorTheme))
            }
            item {
                SettingsCard {
                    BoardChoiceRow("Сетка по умолчанию", "Для новых и общих досок") {
                        CanvasGrid.entries.forEach { g ->
                            FilterChip(selected = settings.defaultGrid == g, onClick = { viewModel.defaultGrid(g) },
                                label = { Text(when (g) { CanvasGrid.NONE -> "Без сетки"; CanvasGrid.SQUARES -> "Клетка"; CanvasGrid.DOTS -> "Точки" }) })
                        }
                    }
                }
            }
            item {
                SettingsCard(Modifier.padding(top = 16.dp)) {
                    SettingsClickableRow(Icons.Filled.RestartAlt, "Сбросить настройки доски", "Вернуть значения по умолчанию", { confirmReset = true }, colorTheme)
                }
            }
            item {
                Text(
                    "Рисунок на общей доске чата передаётся через сервер без сквозного шифрования. Уже существующие доски настройки не меняют: они действуют при открытии доски и для новых досок.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    CanvasLibraryDialogs(renameBoard,deleteBoard,!library.busy,{renameBoard=null;deleteBoard=null},libraryViewModel::rename,libraryViewModel::delete)
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false }, title = { Text("Сбросить настройки доски?") },
        text = { Text("Все параметры раздела вернутся к значениям по умолчанию. Сами доски не изменятся.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; viewModel.reset() }) { Text("Сбросить") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
    )
}

@Composable
private fun BoardChoiceRow(title: String, subtitle: String, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
