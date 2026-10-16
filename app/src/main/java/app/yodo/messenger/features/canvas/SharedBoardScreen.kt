package app.yodo.messenger.features.canvas

import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.OutlinedButton
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.FilterChip
import app.yodo.messenger.ui.components.DropdownMenuItem
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.yodo.messenger.ui.components.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var drawingView by remember { mutableStateOf<CanvasDrawingView?>(null) }
    var textPoint by remember { mutableStateOf<CanvasPoint?>(null) }
    var formulaPoint by remember { mutableStateOf<CanvasPoint?>(null) }
    var editingFormula by remember { mutableStateOf<CanvasObject?>(null) }
    var enteredText by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf<Boolean?>(null) }
    val editable = ui.doc != null && !ui.loading && !ui.busy && !ui.clearing
    val snack = remember { SnackbarHostState() }
    BackHandler(enabled = ui.busy) { /* Finish the destructive operation before leaving. */ }
    // Значения по умолчанию из настроек применяются при их загрузке и при изменении.
    LaunchedEffect(settings) { color = settings.inkColor; width = settings.inkWidth; shapeAssist = settings.shapeAssist; snapGrid = settings.snapToGrid }
    LaunchedEffect(ui.error) { ui.error?.let { snack.showSnackbar(it); viewModel.clearError() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text(ui.doc?.title ?: "Общая доска", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Онлайн · изменения видят все участники чата", style = MaterialTheme.typography.labelSmall)
                } },
                navigationIcon = { IconButton(enabled = !ui.busy, onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.loading || ui.busy || ui.clearing) LinearProgressIndicator(Modifier.fillMaxWidth())
            CanvasToolbar {
                CanvasTool.entries.forEach { option ->
                    FilterChip(selected = tool == option, onClick = { tool = option }, enabled = editable,
                        label = { Text(when (option) {
                            CanvasTool.PEN -> "Карандаш"; CanvasTool.HIGHLIGHTER -> "Маркер"; CanvasTool.LINE -> "Линия"; CanvasTool.ARROW -> "Стрелка"
                            CanvasTool.RECTANGLE -> "Прямоугольник"; CanvasTool.ROUNDED_RECT -> "Скруглённый"; CanvasTool.CIRCLE -> "Окружность"
                            CanvasTool.ELLIPSE -> "Эллипс"; CanvasTool.TRIANGLE -> "Треугольник"; CanvasTool.DIAMOND -> "Ромб"
                            CanvasTool.TEXT -> "Текст"; CanvasTool.FORMULA -> "Формула"; CanvasTool.ERASER -> "Ластик"; CanvasTool.HAND -> "Рука"
                        }) })
                }
            }
            CanvasToolbar {
                BoardColors.palette.forEach { (name, value) ->
                    Box(Modifier.size(44.dp).semantics { contentDescription = "Цвет: $name" }.clickable { color = value }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(28.dp).background(Color(value), CircleShape).border(if (color == value) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                    }
                }
                if (tool == CanvasTool.TEXT || tool == CanvasTool.FORMULA) listOf(24f, 32f, 48f).forEach { v -> FilterChip(selected = fontSize == v, onClick = { fontSize = v }, label = { Text("${v.toInt()} px") }) }
                else BoardInkWidths.forEach { v -> FilterChip(selected = width == v.toFloat(), onClick = { width = v.toFloat() }, label = { Text("$v px") }) }
            }
            CanvasToolbar {
                var gridMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(enabled = editable, onClick = { gridMenu = true }) { Text(when (ui.doc?.grid) { CanvasGrid.SQUARES -> "Клетка 24 px"; CanvasGrid.DOTS -> "Точки"; else -> "Без сетки" }) }
                    DropdownMenu(expanded = gridMenu, onDismissRequest = { gridMenu = false }) {
                        CanvasGrid.entries.forEach { item -> DropdownMenuItem(text = { Text(when (item) { CanvasGrid.NONE -> "Без сетки"; CanvasGrid.SQUARES -> "Клетка 24 px"; CanvasGrid.DOTS -> "Точки" }) }, onClick = { gridMenu = false; viewModel.updateGrid(item) }) }
                    }
                }
                OutlinedButton(enabled = editable && ui.canUndo, onClick = { viewModel.undo() }) { Text("Отмена") }
                OutlinedButton(enabled = editable && ui.canRedo, onClick = { viewModel.undo(true) }) { Text("Повтор") }
                TextButton(enabled = editable, onClick = { drawingView?.resetViewport() }) { Text("Вписать") }
                FilterChip(selected = shapeAssist, onClick = { shapeAssist = !shapeAssist }, label = { Text("Умные фигуры") })
                FilterChip(selected = snapGrid, onClick = { snapGrid = !snapGrid }, label = { Text("К сетке") })
                TextButton(enabled = !ui.busy && ui.doc != null, onClick = { confirmReset = false }) { Text(if (ui.clearing) "Повторить очистку" else "Очистить всё") }
                TextButton(enabled = !ui.busy, onClick = { confirmReset = true }) { Text("Удалить доску") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color(0xffe9edf3))) {
                val doc = ui.doc
                if (doc != null) AndroidView(modifier = Modifier.fillMaxSize(), factory = { context -> CanvasDrawingView(context).also { drawingView = it } }, update = { view ->
                    val changed = view.document?.id != doc.id
                    if (view.documentRevision != ui.generation) view.cancelRemoteConflict()
                    view.documentRevision = ui.generation
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
                if (ui.truncated) "Объектов больше предела: показаны первые ${CanvasLimits.MAX_OBJECTS}. Содержимое идёт через сервер без сквозного шифрования."
                else "Рисунок идёт через сервер без сквозного шифрования · ластик удаляет объект целиком",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
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
