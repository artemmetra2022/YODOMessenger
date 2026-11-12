package app.yodo.messenger.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.features.canvas.CanvasSavedBoard
import app.yodo.messenger.features.settings.CanvasLibraryViewModel
import app.yodo.messenger.ui.components.Button
import app.yodo.messenger.ui.components.IconButton
import app.yodo.messenger.ui.components.OutlinedButton
import app.yodo.messenger.ui.components.TextButton
import app.yodo.messenger.ui.components.clickable
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * НОВОЕ: полноценный редактор поста — взамен простого однострочного поля ввода.
 * Возможности:
 *   • панель форматирования (жирный/курсив/зачёркнутый/заголовок/список/цитата/ссылка),
 *     оборачивающая текущее выделение в markdown-разметку (см. PostMarkdown.kt);
 *   • живое превью отформатированного текста;
 *   • прикрепление фото (как раньше);
 *   • прикрепление личной доски из библиотеки (настройки → "Мои доски") — вкладка "Доска".
 */
@Composable
fun PostEditorDialog(
    colorTheme: ColorTheme,
    isPosting: Boolean,
    selectedBoard: CanvasLibraryBoardRef?,
    onSelectBoard: (CanvasLibraryBoardRef?) -> Unit,
    onDismiss: () -> Unit,
    onPublish: (text: String, images: List<Uri>) -> Unit
) {
    var fieldValue by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(""))
    }
    var selectedImages by rememberSaveable { mutableStateOf<List<Uri>>(emptyList()) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var showBoardPicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) selectedImages = (selectedImages + uris).take(4)
    }

    if (showBoardPicker) {
        BoardPickerDialog(
            colorTheme = colorTheme,
            onDismiss = { showBoardPicker = false },
            onPick = { ref ->
                onSelectBoard(ref)
                showBoardPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый пост") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                FormattingToolbar(
                    value = fieldValue,
                    onValueChange = { fieldValue = it }
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("Текст", "Просмотр")
                    tabs.forEachIndexed { index, label ->
                        val selected = (index == 1) == showPreview
                        TextButton(onClick = { showPreview = index == 1 }) {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) colorTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (showPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (fieldValue.text.isBlank()) {
                            Text("Нечего показать", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(rememberPostAnnotatedText(fieldValue.text))
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp),
                        placeholder = { Text("О чём расскажете?") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Фото")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { showBoardPicker = true }) {
                        Icon(Icons.Filled.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Доска")
                    }
                }

                if (selectedImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedImages) { uri ->
                            Box(modifier = Modifier.size(64.dp)) {
                                Image(
                                    painter = coil.compose.rememberAsyncImagePainter(uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { selectedImages = selectedImages - uri },
                                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Убрать фото",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                selectedBoard?.let { board ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Dashboard, contentDescription = null, tint = colorTheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(board.title, fontWeight = FontWeight.Medium)
                            board.objectCount?.let {
                                Text("Объектов: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onSelectBoard(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Открепить доску")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(fieldValue.text, selectedImages) },
                enabled = !isPosting && (fieldValue.text.isNotBlank() || selectedImages.isNotEmpty() || selectedBoard != null)
            ) {
                if (isPosting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Опубликовать")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/** Панель кнопок форматирования — оборачивает текущее выделение в markdown-синтаксис. */
@Composable
private fun FormattingToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    fun wrapSelection(prefix: String, suffix: String = prefix) {
        val sel = value.selection
        val start = minOf(sel.start, sel.end)
        val end = maxOf(sel.start, sel.end)
        val selectedText = value.text.substring(start, end)
        val newText = value.text.substring(0, start) + prefix + selectedText + suffix + value.text.substring(end)
        val newCursor = if (selectedText.isEmpty()) start + prefix.length else end + prefix.length + suffix.length
        onValueChange(value.copy(text = newText, selection = TextRange(newCursor)))
    }

    fun prefixLines(prefix: String) {
        val sel = value.selection
        val start = minOf(sel.start, sel.end)
        val end = maxOf(sel.start, sel.end)
        // Находим начало первой затронутой строки, чтобы добавить префикс к каждой строке выделения.
        val lineStart = value.text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val before = value.text.substring(0, lineStart)
        val affected = value.text.substring(lineStart, end)
        val after = value.text.substring(end)
        val updated = affected.split("\n").joinToString("\n") { line -> if (line.startsWith(prefix)) line else prefix + line }
        val newText = before + updated + after
        onValueChange(value.copy(text = newText, selection = TextRange(before.length + updated.length)))
    }

    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarIcon(Icons.Filled.FormatBold, "Жирный") { wrapSelection(PostMarkdown.BOLD_MARK) }
        ToolbarIcon(Icons.Filled.FormatItalic, "Курсив") { wrapSelection(PostMarkdown.ITALIC_MARK) }
        ToolbarIcon(Icons.Filled.FormatStrikethrough, "Зачёркнутый") { wrapSelection(PostMarkdown.STRIKE_MARK) }
        ToolbarIcon(Icons.Filled.Title, "Заголовок") { prefixLines(PostMarkdown.HEADING_PREFIX) }
        ToolbarIcon(Icons.Filled.FormatListBulleted, "Список") { prefixLines(PostMarkdown.BULLET_PREFIX) }
        ToolbarIcon(Icons.Filled.FormatQuote, "Цитата") { prefixLines(PostMarkdown.QUOTE_PREFIX) }
        ToolbarIcon(Icons.Filled.Link, "Ссылка") {
            val sel = value.selection
            val start = minOf(sel.start, sel.end)
            val end = maxOf(sel.start, sel.end)
            val label = value.text.substring(start, end).ifEmpty { "текст" }
            val markup = "[$label](https://)"
            val newText = value.text.substring(0, start) + markup + value.text.substring(end)
            onValueChange(value.copy(text = newText, selection = TextRange(start + markup.length)))
        }
    }
}

@Composable
private fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    }
}

/**
 * НОВОЕ: выбор доски из личной библиотеки (та же библиотека, что в Настройки → Мои доски,
 * CanvasLibraryViewModel). Выбранная доска остаётся приватной — в посте публикуется только
 * статичное превью, рендерящееся позже, непосредственно перед отправкой поста.
 */
@Composable
private fun BoardPickerDialog(
    colorTheme: ColorTheme,
    onDismiss: () -> Unit,
    onPick: (CanvasLibraryBoardRef) -> Unit,
    viewModel: CanvasLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val readableBoards = state.boards.filter { it.readable && it.chatId != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите доску") },
        text = {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                readableBoards.isEmpty() -> Text(
                    "У вас пока нет сохранённых досок. Создайте доску в любом чате — она появится здесь.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(readableBoards, key = { "${it.storageChatHash}/${it.id}" }) { board ->
                        BoardPickerRow(board, colorTheme) {
                            onPick(
                                CanvasLibraryBoardRef(
                                    boardId = board.id,
                                    chatId = board.chatId,
                                    title = board.title,
                                    objectCount = board.objectCount
                                )
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun BoardPickerRow(board: CanvasSavedBoard, colorTheme: ColorTheme, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Dashboard, contentDescription = null, tint = colorTheme.primary)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(board.title, fontWeight = FontWeight.Medium)
            val dateText = remember(board.modifiedAt) {
                SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(board.modifiedAt))
            }
            Text(
                "$dateText${board.objectCount?.let { " · объектов: $it" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val TextFieldValueSaver = androidx.compose.runtime.saveable.Saver<TextFieldValue, String>(
    save = { it.text },
    restore = { TextFieldValue(it) }
)
