package app.yodo.messenger.features.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Presentation only: the same fixed tools and pickers on both editors. */
internal val CanvasShapeTools = listOf(CanvasTool.LINE, CanvasTool.ARROW, CanvasTool.RECTANGLE,
    CanvasTool.ROUNDED_RECT, CanvasTool.CIRCLE, CanvasTool.ELLIPSE, CanvasTool.TRIANGLE, CanvasTool.DIAMOND)

internal fun canvasToolLabel(tool: CanvasTool): String = when (tool) {
    CanvasTool.PEN -> "Перо"; CanvasTool.HIGHLIGHTER -> "Маркер"; CanvasTool.ERASER -> "Ластик"
    CanvasTool.SELECT -> "Выбор"; CanvasTool.HAND -> "Рука"; CanvasTool.TEXT -> "Текст"; CanvasTool.FORMULA -> "Формула"
    CanvasTool.LINE -> "Линия"; CanvasTool.ARROW -> "Стрелка"; CanvasTool.RECTANGLE -> "Прямоугольник"
    CanvasTool.ROUNDED_RECT -> "Скруглённый"; CanvasTool.CIRCLE -> "Окружность"; CanvasTool.ELLIPSE -> "Эллипс"
    CanvasTool.TRIANGLE -> "Треугольник"; CanvasTool.DIAMOND -> "Ромб"
}

private fun canvasToolIcon(tool: CanvasTool): ImageVector = when (tool) {
    CanvasTool.PEN -> Icons.Default.Edit; CanvasTool.HIGHLIGHTER -> Icons.Default.Brush
    CanvasTool.SELECT -> Icons.Default.CropFree; CanvasTool.ERASER -> Icons.Default.AutoFixNormal; CanvasTool.HAND -> Icons.Default.PanTool
    CanvasTool.TEXT -> Icons.Default.TextFields; CanvasTool.FORMULA -> Icons.Default.Functions
    CanvasTool.LINE -> Icons.Default.Remove; CanvasTool.ARROW -> Icons.Default.NorthEast
    CanvasTool.RECTANGLE -> Icons.Default.CropSquare; CanvasTool.ROUNDED_RECT -> Icons.Default.RoundedCorner
    CanvasTool.CIRCLE, CanvasTool.ELLIPSE -> Icons.Default.RadioButtonUnchecked
    CanvasTool.TRIANGLE -> Icons.Default.ChangeHistory; CanvasTool.DIAMOND -> Icons.Default.Diamond
}

@Composable
internal fun CanvasActionIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(icon, label) }
}

@Composable
internal fun CanvasHistoryBar(canUndo: Boolean, canRedo: Boolean, enabled: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit, onFit: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Surface(tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CanvasActionIcon(Icons.AutoMirrored.Filled.Undo, "Отменить", canUndo, onUndo)
            CanvasActionIcon(Icons.AutoMirrored.Filled.Redo, "Повторить", canRedo, onRedo)
            CanvasActionIcon(Icons.Default.FitScreen, "Вписать доску в экран", enabled, onFit)
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanvasPickerSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
internal fun CanvasEditorDock(tool: CanvasTool, onTool: (CanvasTool) -> Unit, color: Int, onColor: (Int) -> Unit,
    width: Float, onWidth: (Float) -> Unit, fontSize: Float, onFontSize: (Float) -> Unit,
    enabled: Boolean, onImage: (() -> Unit)? = null,
    selectionView: CanvasDrawingView? = null, hasSelection: Boolean = false) {
    var picker by remember { mutableStateOf<String?>(null) }
    val textTool = tool == CanvasTool.TEXT || tool == CanvasTool.FORMULA
    val insertTool = textTool || tool == CanvasTool.HAND
    val selecting = tool==CanvasTool.SELECT && hasSelection
    val quickControls: @Composable () -> Unit = {
            if(selecting) Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                CanvasSelectionActions(selectionView,enabled)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BoardColors.palette.take(3).forEach { (name, value) -> CanvasColorSwatch(name, value, color == value, enabled) { onColor(value) } }
                // Always shows the current color, including one chosen from the full palette.
                IconButton(onClick = { picker = "palette" }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Icon(Icons.Default.Palette, "Все цвета; выбран ${BoardColors.palette.firstOrNull { it.second == color }?.first.orEmpty()}")
                        Box(Modifier.size(10.dp).background(Color(color), CircleShape).border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { picker = "size" }, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${(if (textTool) fontSize else width).toInt()} px", fontSize = 14.sp)
                    Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                }
            }
    }
    val primaryTools: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val shape = tool in CanvasShapeTools
                val slots = listOf(CanvasTool.PEN, CanvasTool.HIGHLIGHTER, CanvasTool.ERASER,
                    if (shape) tool else CanvasTool.RECTANGLE, if (insertTool) tool else CanvasTool.TEXT, CanvasTool.SELECT)
                slots.forEachIndexed { index, slot ->
                    val active = when (index) { 3 -> shape; 4 -> insertTool; else -> tool == slot }
                    val label = when (index) { 3 -> "Фигуры"; 4 -> when(tool) { CanvasTool.FORMULA -> "Формула"; CanvasTool.HAND -> "Рука"; else -> "Текст" }; else -> canvasToolLabel(slot) }
                    Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .semantics { selected = active; contentDescription = if (index == 3) "Фигуры: ${canvasToolLabel(slot)}" else label }
                        .clickable(enabled = enabled) { when (index) { 3 -> picker = "shapes"; 4 -> picker = "insert"; else -> onTool(slot) } }
                        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                            else if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(canvasToolIcon(slot), null, tint = tint, modifier = Modifier.size(22.dp))
                            if (index == 3 || index == 4) Icon(Icons.Default.ExpandMore, null, tint = tint, modifier = Modifier.size(12.dp))
                        }
                        Text(label, fontSize = 14.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
    }
    Surface(shadowElevation = 4.dp, tonalElevation = 1.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (maxWidth >= 600.dp) {
                // On a wide/landscape display the dock becomes one row, leaving more room for the paper.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(if(selecting)296.dp else 280.dp)) { quickControls() }
                    VerticalDivider(Modifier.height(48.dp).padding(horizontal = 4.dp))
                    Box(Modifier.weight(1f)) { primaryTools() }
                }
            } else {
                Column {
                    quickControls()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    primaryTools()
                }
            }
        }
    }
    when (picker) {
        "palette" -> CanvasPickerSheet("Цвет рисунка", { picker = null }) {
            BoardColors.palette.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, value) -> CanvasColorSwatch(name, value, color == value, enabled) { onColor(value); picker = null } }
            } }
        }
        "size" -> CanvasPickerSheet(if (textTool) "Размер текста" else "Толщина линии", { picker = null }) {
            val values = if (textTool) listOf(24f, 32f, 48f) else BoardInkWidths.map(Int::toFloat)
            values.forEach { value -> CanvasChoice("${value.toInt()} px", (if (textTool) fontSize else width) == value, enabled) {
                if (textTool) onFontSize(value) else onWidth(value); picker = null
            } }
        }
        "shapes" -> CanvasPickerSheet("Фигуры", { picker = null }) {
            CanvasShapeTools.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item -> OutlinedButton(modifier = Modifier.weight(1f).heightIn(min = 56.dp), enabled = enabled,
                    onClick = { onTool(item); picker = null }, contentPadding = PaddingValues(8.dp),
                    border = BorderStroke(if (tool == item) 2.dp else 1.dp, if (tool == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (tool == item) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Icon(canvasToolIcon(item), null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(canvasToolLabel(item), fontSize = 14.sp, modifier = Modifier.weight(1f))
                } }
            } }
        }
        "insert" -> CanvasPickerSheet("Добавить на доску", { picker = null }) {
            listOf(CanvasTool.TEXT, CanvasTool.FORMULA, CanvasTool.HAND).forEach { item -> CanvasChoice(canvasToolLabel(item), tool == item, enabled) { onTool(item); picker = null } }
            if (onImage != null) TextButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, onClick = { picker = null; onImage() }) { Text("Картинка из галереи") }
            Text("Выберите текст или формулу, затем коснитесь холста. Рука перемещает холст; двумя пальцами можно двигать и масштабировать его в любом режиме.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CanvasColorSwatch(name: String, value: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).semantics { contentDescription = "Цвет: $name"; selected = active }
        .clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).border(if (active) 2.dp else 1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape).padding(4.dp)
            .background(Color(value), CircleShape))
    }
}

@Composable
private fun CanvasChoice(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = active, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun CanvasBoardOptions(grid: CanvasGrid?, onGrid: (CanvasGrid) -> Unit, enabled: Boolean,
    shapeAssist: Boolean, onShapeAssist: (Boolean) -> Unit, snapGrid: Boolean, onSnapGrid: (Boolean) -> Unit,
    onDismiss: () -> Unit, template: CanvasTemplate? = null, onTemplate: ((CanvasTemplate) -> Unit)? = null) {
    CanvasPickerSheet("Настройки холста", onDismiss) {
        Text("Сетка", style = MaterialTheme.typography.titleMedium)
        CanvasGrid.entries.forEach { item -> CanvasChoice(when (item) {
            CanvasGrid.NONE -> "Без сетки"; CanvasGrid.SQUARES -> "Клетка 24 px"; CanvasGrid.DOTS -> "Точки"
        }, grid == item, enabled) { onGrid(item) } }
        if (onTemplate != null) {
            Text("Шаблон", style = MaterialTheme.typography.titleMedium)
            CanvasTemplate.entries.forEach { item -> CanvasChoice(when (item) {
                CanvasTemplate.NONE -> "Без шаблона"; CanvasTemplate.AXES -> "Оси X/Y"; CanvasTemplate.TRIANGLE -> "Треугольник"
                CanvasTemplate.RIGHT_TRIANGLE -> "Прямоугольный треугольник"
            }, template == item, enabled) { onTemplate(item) } }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Умные фигуры", Modifier.weight(1f)); Switch(checked = shapeAssist, onCheckedChange = onShapeAssist)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Привязка к сетке", Modifier.weight(1f)); Switch(checked = snapGrid, onCheckedChange = onSnapGrid)
        }
    }
}

@Composable
internal fun CanvasHelp(onDismiss: () -> Unit) {
    CanvasPickerSheet("Как рисовать", onDismiss) {
        Text("Два пальца — масштаб и перемещение. Кнопка в верхней панели вписывает доску в экран.")
        Text("Выбор: коснитесь объекта и тяните его. Нижний круг меняет размер, верхний — угол. В нижней панели доступны поворот, размер и копирование. Вставленная картинка остаётся фоном и не выделяется.")
        Text("Рука находится в меню «Текст». Двумя пальцами можно двигать холст в любом режиме.")
        Text("Задержите палец после штриха пером — умная фигура выровняет его. Ластик удаляет объект целиком.")
        Text("В «Фигурах» находятся линии и геометрия. В «Тексте» — текст и формулы; на локальной доске также доступна вставка картинки. Цвет и размер всегда под рукой.")
        Text("Общая доска, онлайн-сессия и отправленные картинки проходят через сервер без сквозного шифрования.")
    }
}

/** Compact contextual actions; the six main tools remain reachable without scrolling. */
@Composable
internal fun CanvasSelectionActions(view: CanvasDrawingView?, enabled: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.padding(4.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp, tonalElevation = 3.dp) {
        Row {
            CanvasActionIcon(Icons.Default.Remove, "Уменьшить объект", enabled) { view?.scaleSelection(.85f) }
            CanvasActionIcon(Icons.Default.Add, "Увеличить объект", enabled) { view?.scaleSelection(1.15f) }
            CanvasActionIcon(Icons.Default.RotateLeft, "Повернуть влево на 15°", enabled) { view?.rotateSelection(-15f) }
            CanvasActionIcon(Icons.Default.RotateRight, "Повернуть вправо на 15°", enabled) { view?.rotateSelection(15f) }
            CanvasActionIcon(Icons.Default.ContentCopy, "Копировать объект", enabled) { view?.copySelection() }
            CanvasActionIcon(Icons.Default.Close, "Снять выделение", enabled) { view?.clearSelection() }
        }
    }
}
