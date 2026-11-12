package app.yodo.messenger.features.canvas

import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.DropdownMenuItem
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/** Редактор общей доски чата: изменения видят все участники с заданным интервалом, в том числе в карточке внутри чата. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedBoardScreen(onBackClick: () -> Unit, viewModel: SharedBoardViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var tool by remember { mutableStateOf(CanvasTool.PEN) }
    var color by remember { mutableIntStateOf(settings.inkColor) }
    var width by remember { mutableFloatStateOf(settings.inkWidth) }
    var fontSize by remember { mutableFloatStateOf(32f) }
    var shapeAssist by remember { mutableStateOf(settings.shapeAssist) }
    var snapGrid by remember { mutableStateOf(settings.snapToGrid) }
    var hasSelection by remember { mutableStateOf(false) }
    var drawingView by remember { mutableStateOf<CanvasDrawingView?>(null) }
    var textPoint by remember { mutableStateOf<CanvasPoint?>(null) }
    var formulaPoint by remember { mutableStateOf<CanvasPoint?>(null) }
    var editingFormula by remember { mutableStateOf<CanvasObject?>(null) }
    var enteredText by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf<Boolean?>(null) }
    val editable = ui.doc != null && !ui.loading && !ui.busy && !ui.clearing
    var moreMenu by remember { mutableStateOf(false) }
    var boardOptions by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val workspaceColor = MaterialTheme.colorScheme.surfaceVariant
    val paperEdgeColor = MaterialTheme.colorScheme.outlineVariant
    val snack = remember { SnackbarHostState() }
    BackHandler(enabled = ui.busy) { /* Finish the destructive operation before leaving. */ }
    // Значения по умолчанию из настроек применяются при их загрузке и при изменении.
    LaunchedEffect(settings) { color = settings.inkColor; width = settings.inkWidth; shapeAssist = settings.shapeAssist; snapGrid = settings.snapToGrid }
    LaunchedEffect(ui.error) { ui.error?.let { snack.showSnackbar(it); viewModel.clearError() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text(ui.doc?.title ?: "Общая доска", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(if(ui.clearing) "Очистка…" else "Общая · для участников чата", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } },
                navigationIcon = { CanvasActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", !ui.busy, onBackClick) },
                actions = { Box {
                    CanvasActionIcon(Icons.Default.MoreVert, "Действия с общей доской") { moreMenu=true }
                    DropdownMenu(expanded=moreMenu,onDismissRequest={moreMenu=false}) {
                        DropdownMenuItem(text={Text("Настройки холста")},onClick={moreMenu=false;boardOptions=true})
                        DropdownMenuItem(text={Text("Как рисовать")},onClick={moreMenu=false;help=true})
                        HorizontalDivider()
                        DropdownMenuItem(text={Text(if(ui.clearing) "Повторить очистку" else "Очистить всё",color=MaterialTheme.colorScheme.error)},enabled=!ui.busy&&ui.doc!=null,onClick={moreMenu=false;confirmReset=false})
                        DropdownMenuItem(text={Text("Удалить доску",color=MaterialTheme.colorScheme.error)},enabled=!ui.busy,onClick={moreMenu=false;confirmReset=true})
                    }
                } }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.loading || ui.busy || ui.clearing) LinearProgressIndicator(Modifier.fillMaxWidth())
            CanvasHistoryBar(editable&&ui.canUndo,editable&&ui.canRedo,editable,
                {viewModel.undo()},{viewModel.undo(true)},{drawingView?.resetViewport()}) {
                CanvasActionIcon(Icons.Default.Tune,"Настройки холста") { boardOptions=true }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(workspaceColor)) {
                val doc = ui.doc
                if (doc != null) AndroidView(modifier = Modifier.fillMaxSize(), factory = { context -> CanvasDrawingView(context).also { drawingView = it } }, update = { view ->
                    val changed = view.document?.id != doc.id
                    if (view.documentRevision != ui.generation) view.cancelRemoteConflict()
                    view.documentRevision = ui.generation
                    view.onSelectionChange={hasSelection=it};view.onEditError=viewModel::reportEditError
                    view.workspaceColor=workspaceColor.toArgb();view.paperEdgeColor=paperEdgeColor.toArgb()
                    view.document = doc; view.backgroundImage = null; view.tool = tool; view.inkColor = color; view.inkWidth = width; view.fontSize = fontSize
                    view.shapeAssist = shapeAssist; view.snapToGrid = snapGrid; view.haptics = settings.haptics
                    view.isEnabled = editable
                    view.onGesture = viewModel::commitGesture
                    view.onCommit = viewModel::commit
                    view.onText = { point -> textPoint = point; enteredText = "" }
                    view.onFormula = { point, existing -> formulaPoint = point; editingFormula = existing }
                    view.onLimit = { viewModel.reportLimit() }
                    if (changed) view.resetViewport()
                    view.invalidate()
                }) else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ui.loading) CircularProgressIndicator() else {
                        Text(ui.error ?: "Доска удалена или пока не создана")
                        TextButton(enabled = !ui.busy, onClick = { viewModel.createBoard() }) { Text("Создать доску") }
                    }
                }
            }
            Text(
                if (ui.truncated) "Показаны первые ${CanvasLimits.MAX_OBJECTS} объектов · без сквозного шифрования"
                else "Без сквозного шифрования",
                style = MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            CanvasEditorDock(tool,{tool=it},color,{color=it},width,{width=it},fontSize,{fontSize=it},enabled=editable,selectionView=drawingView,hasSelection=hasSelection)
        }
    }
    if(boardOptions) CanvasBoardOptions(ui.doc?.grid,viewModel::updateGrid,editable,
        shapeAssist,{shapeAssist=it},snapGrid,{snapGrid=it},{boardOptions=false})
    if(help) CanvasHelp { help=false }
    LaunchedEffect(ui.generation, ui.doc?.id) {
        textPoint = null; formulaPoint = null; editingFormula = null
    }
    confirmReset?.let { delete ->
        AlertDialog(onDismissRequest = { confirmReset = null },
            title = { Text(if (delete) "Удалить общую доску?" else "Очистить общую доску?") },
            text = { Text(if (delete) "Доска и все её объекты будут удалены для всех участников чата без возможности отмены. Позже можно создать новую доску."
                else "Все рисунки, текст и формулы будут удалены у всех участников, включая объекты за пределом отображения. Отменить очистку нельзя.") },
            confirmButton = { TextButton(onClick = { confirmReset = null; viewModel.reset(delete) { if (delete) onBackClick() } }) { Text(if (delete) "Удалить" else "Очистить всё") } },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Отмена") } })
    }
    formulaPoint?.let { point ->
        CanvasFormulaDialog(editingFormula, fontSize, editingFormula?.color ?: color, enabled = editable, onDismiss = { formulaPoint = null; editingFormula = null }, onInsert = { source, size ->
            if (viewModel.putFormula(point, source, size, editingFormula?.color ?: color, editingFormula)) null else viewModel.ui.value.error ?: "Формулу не удалось добавить"
        })
    }
    textPoint?.let { point ->
        AlertDialog(onDismissRequest = { textPoint = null }, title = { Text("Текст на доске") },
            text = { OutlinedTextField(value = enteredText, onValueChange = { if (it.length <= 500 && it.lines().size <= 8) enteredText = it }, label = { Text("Текст (до 8 строк)") }, maxLines = 8) },
            confirmButton = { TextButton(enabled = editable && enteredText.isNotBlank(), onClick = { viewModel.addText(point, enteredText, color, fontSize); textPoint = null }) { Text("Добавить") } },
            dismissButton = { TextButton(onClick = { textPoint = null }) { Text("Отмена") } })
    }
}
