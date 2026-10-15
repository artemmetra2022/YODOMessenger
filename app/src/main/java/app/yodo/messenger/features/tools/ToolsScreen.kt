package app.yodo.messenger.features.tools

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * НОВОЕ (батч 7): единый экран «Фишки и инструменты».
 *
 * Содержит 20 новых функций, которых раньше не было. Всё работает локально
 * (без сети), чтобы ничего не ломало основной мессенджер. Каждый инструмент —
 * раскрывающаяся карточка.
 */
@Composable
fun ToolsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("yodo_tools", Context.MODE_PRIVATE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фишки и инструменты") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text(
                    "20 новых функций — нажмите на любую, чтобы открыть.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            item { ToolCard("1. Личные заметки", Icons.Filled.Note) { NotesTool(prefs) } }
            item { ToolCard("2. Список дел", Icons.Filled.CheckCircle) { TodoTool(prefs) } }
            item { ToolCard("3. Быстрые шаблоны ответов", Icons.Filled.Bolt) { TemplatesTool(prefs) } }
            item { ToolCard("4. Генератор паролей", Icons.Filled.Key) { PasswordTool() } }
            item { ToolCard("5. Секундомер", Icons.Filled.Timer) { StopwatchTool() } }
            item { ToolCard("6. Таймер обратного отсчёта", Icons.Filled.Timer) { CountdownTool() } }
            item { ToolCard("7. Тап-счётчик", Icons.Filled.PlusOne) { CounterTool() } }
            item { ToolCard("8. Конвертер единиц", Icons.Filled.Straighten) { UnitConverterTool() } }
            item { ToolCard("9. Калькулятор чаевых", Icons.Filled.Percent) { TipTool() } }
            item { ToolCard("10. Сколько дней прошло", Icons.Filled.Timer) { DaysBetweenTool() } }
            item { ToolCard("11. Кубик и случайное число", Icons.Filled.Casino) { DiceTool() } }
            item { ToolCard("12. Орёл или решка", Icons.Filled.Casino) { CoinTool() } }
            item { ToolCard("13. Жребий (случайный выбор)", Icons.Filled.Casino) { RandomPickerTool() } }
            item { ToolCard("14. Счётчик воды", Icons.Filled.LocalDrink) { WaterTool(prefs) } }
            item { ToolCard("15. Цитата дня", Icons.Filled.FormatQuote) { QuoteTool() } }
            item { ToolCard("16. Преобразование текста", Icons.Filled.TextFields) { TextCaseTool() } }
            item { ToolCard("17. Base64 кодировщик", Icons.Filled.Code) { Base64Tool() } }
            item { ToolCard("18. Счётчик слов и символов", Icons.Filled.TextFields) { WordCountTool() } }
            item { ToolCard("19. Случайный цвет", Icons.Filled.Palette) { ColorTool() } }
            item { ToolCard("20. Счёт очков (двое)", Icons.Filled.EmojiEvents) { ScoreboardTool() } }
        }
    }
}

@Composable
private fun ToolCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun CopyRow(value: String) {
    val clipboard = LocalClipboardManager.current
    if (value.isBlank()) return
    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Копировать")
    }
}

// 1. Заметки
@Composable
private fun NotesTool(prefs: android.content.SharedPreferences) {
    var text by remember { mutableStateOf(prefs.getString("notes", "") ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; prefs.edit().putString("notes", it).apply() },
        modifier = Modifier.fillMaxWidth().height(140.dp),
        placeholder = { Text("Ваши личные заметки… сохраняются автоматически") }
    )
}

// Общий список-хелпер (для дел, шаблонов) — вынесен в util/PrefsListUtils.kt,
// чтобы список шаблонов был доступен и из чата (кнопка «Быстрые ответы»).
private fun loadList(prefs: android.content.SharedPreferences, key: String): List<String> =
    app.yodo.messenger.util.loadPrefsList(prefs, key)

private fun saveList(prefs: android.content.SharedPreferences, key: String, list: List<String>) {
    app.yodo.messenger.util.savePrefsList(prefs, key, list)
}

// 2. Список дел
@Composable
private fun TodoTool(prefs: android.content.SharedPreferences) {
    var items by remember { mutableStateOf(loadList(prefs, "todo")) }
    var input by remember { mutableStateOf("") }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Новая задача") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (input.isNotBlank()) { items = items + input.trim(); saveList(prefs, "todo", items); input = "" }
            }) { Text("+") }
        }
        Spacer(Modifier.height(8.dp))
        items.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("• $task", modifier = Modifier.weight(1f))
                IconButton(onClick = { items = items - task; saveList(prefs, "todo", items) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// 3. Шаблоны ответов
@Composable
private fun TemplatesTool(prefs: android.content.SharedPreferences) {
    var items by remember { mutableStateOf(loadList(prefs, "templates")) }
    var input by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    Column {
        Text("Сохраните частые фразы и копируйте в один клик.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Текст шаблона") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (input.isNotBlank()) { items = items + input.trim(); saveList(prefs, "templates", items); input = "" }
            }) { Text("+") }
        }
        Spacer(Modifier.height(8.dp))
        items.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(t, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(t)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { items = items - t; saveList(prefs, "templates", items) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// 4. Генератор паролей
@Composable
private fun PasswordTool() {
    var length by remember { mutableStateOf(16f) }
    var symbols by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }
    fun gen() {
        val base = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        val extra = "!@#\$%^&*-_=+?"
        val alphabet = if (symbols) base + extra else base
        result = (1..length.roundToInt()).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
    Column {
        Text("Длина: ${length.roundToInt()}")
        Slider(value = length, onValueChange = { length = it }, valueRange = 6f..40f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Спецсимволы", modifier = Modifier.weight(1f))
            Switch(checked = symbols, onCheckedChange = { symbols = it })
        }
        Button(onClick = { gen() }) { Text("Сгенерировать") }
        if (result.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(result, fontWeight = FontWeight.Bold)
            CopyRow(result)
        }
    }
}

// 5. Секундомер
@Composable
private fun StopwatchTool() {
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(running) {
        while (running) { delay(100); elapsed += 100 }
    }
    val s = elapsed / 1000
    Column {
        Text(String.format("%02d:%02d.%d", s / 60, s % 60, (elapsed % 1000) / 100),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { running = !running }) { Text(if (running) "Пауза" else "Старт") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { running = false; elapsed = 0 }) { Text("Сброс") }
        }
    }
}

// 6. Таймер
@Composable
private fun CountdownTool() {
    var minutes by remember { mutableStateOf("5") }
    var left by remember { mutableStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && left > 0) { delay(1000); left -= 1000 }
        if (left <= 0) running = false
    }
    val s = (left / 1000).coerceAtLeast(0)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = minutes, onValueChange = { minutes = it.filter { c -> c.isDigit() } },
                modifier = Modifier.width(100.dp), label = { Text("Минут") }
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { left = (minutes.toLongOrNull() ?: 0) * 60_000; running = true }) { Text("Старт") }
        }
        Spacer(Modifier.height(8.dp))
        Text(String.format("%02d:%02d", s / 60, s % 60),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (!running && left == 0L && minutes.isNotBlank()) Text("Готово!", color = MaterialTheme.colorScheme.primary)
    }
}

// 7. Тап-счётчик
@Composable
private fun CounterTool() {
    var count by remember { mutableStateOf(0) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("$count", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Row {
            OutlinedButton(onClick = { if (count > 0) count-- }) { Text("−") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { count++ }) { Text("+1") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { count = 0 }) { Text("Сброс") }
        }
    }
}

// 8. Конвертер единиц (км/мили, кг/фунты, C/F)
@Composable
private fun UnitConverterTool() {
    val modes = listOf("км → мили", "кг → фунты", "°C → °F")
    var mode by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    val v = input.replace(',', '.').toDoubleOrNull()
    val out = when (mode) {
        0 -> v?.let { it * 0.621371 }
        1 -> v?.let { it * 2.20462 }
        else -> v?.let { it * 9 / 5 + 32 }
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            modes.forEachIndexed { i, m ->
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(m) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Значение") })
        if (out != null) Text("= ${"%.2f".format(out)}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

// 9. Чаевые
@Composable
private fun TipTool() {
    var bill by remember { mutableStateOf("") }
    var percent by remember { mutableStateOf(10f) }
    val b = bill.replace(',', '.').toDoubleOrNull() ?: 0.0
    val tip = b * percent / 100
    Column {
        OutlinedTextField(value = bill, onValueChange = { bill = it }, label = { Text("Сумма счёта") })
        Text("Чаевые: ${percent.roundToInt()}%")
        Slider(value = percent, onValueChange = { percent = it }, valueRange = 0f..30f)
        Text("Чаевые: ${"%.2f".format(tip)} · Итого: ${"%.2f".format(b + tip)}", fontWeight = FontWeight.Bold)
    }
}

// 10. Сколько дней прошло/осталось от даты
@Composable
private fun DaysBetweenTool() {
    var d by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = d, onValueChange = { d = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("ДД") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = m, onValueChange = { m = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("ММ") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = y, onValueChange = { y = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("ГГГГ") }, modifier = Modifier.weight(1.3f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val day = d.toIntOrNull(); val mon = m.toIntOrNull(); val year = y.toIntOrNull()
            if (day != null && mon != null && year != null) {
                val cal = java.util.Calendar.getInstance()
                cal.set(year, mon - 1, day, 0, 0, 0)
                val diff = System.currentTimeMillis() - cal.timeInMillis
                val days = diff / (1000L * 60 * 60 * 24)
                result = if (days >= 0) "Прошло дней: $days" else "Осталось дней: ${-days}"
            } else result = "Проверьте дату"
        }) { Text("Посчитать") }
        if (result.isNotBlank()) Text(result, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

// 11. Кубик
@Composable
private fun DiceTool() {
    var max by remember { mutableStateOf("6") }
    var value by remember { mutableStateOf(0) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = max, onValueChange = { max = it.filter { c -> c.isDigit() } }, label = { Text("Максимум") }, modifier = Modifier.width(120.dp))
            Spacer(Modifier.width(12.dp))
            Button(onClick = { value = Random.nextInt(1, (max.toIntOrNull() ?: 6).coerceAtLeast(2) + 1) }) { Text("Бросить") }
        }
        if (value > 0) Text("🎲 $value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

// 12. Орёл/решка
@Composable
private fun CoinTool() {
    var side by remember { mutableStateOf("") }
    Column {
        Button(onClick = { side = if (Random.nextBoolean()) "Орёл" else "Решка" }) { Text("Подбросить") }
        if (side.isNotBlank()) Text(side, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

// 13. Жребий
@Composable
private fun RandomPickerTool() {
    var input by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Варианты через запятую") })
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val opts = input.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (opts.isNotEmpty()) picked = opts.random()
        }) { Text("Выбрать") }
        if (picked.isNotBlank()) Text("→ $picked", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

// 14. Счётчик воды
@Composable
private fun WaterTool(prefs: android.content.SharedPreferences) {
    val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    var glasses by remember { mutableStateOf(if (prefs.getString("water_day", "") == today) prefs.getInt("water", 0) else 0) }
    fun save() { prefs.edit().putInt("water", glasses).putString("water_day", today).apply() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("💧 Сегодня: $glasses стаканов", style = MaterialTheme.typography.titleMedium)
        Row {
            OutlinedButton(onClick = { if (glasses > 0) { glasses--; save() } }) { Text("−") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { glasses++; save() }) { Text("+ стакан") }
        }
    }
}

// 15. Цитата дня
@Composable
private fun QuoteTool() {
    val quotes = remember {
        listOf(
            "Дорогу осилит идущий.",
            "Лучшее время начать — сейчас.",
            "Маленький шаг каждый день — большой результат за год.",
            "Сделано лучше, чем идеально.",
            "Ты сильнее, чем думаешь."
        )
    }
    var q by remember { mutableStateOf(quotes.random()) }
    Column {
        Text("«$q»", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { q = quotes.random() }) { Text("Следующая") }
    }
}

// 16. Преобразование текста
@Composable
private fun TextCaseTool() {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Текст") })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { result = input.uppercase() }) { Text("АБВ") }
            OutlinedButton(onClick = { result = input.lowercase() }) { Text("абв") }
            OutlinedButton(onClick = { result = input.reversed() }) { Text("←") }
        }
        if (result.isNotBlank()) { Text(result, modifier = Modifier.padding(top = 6.dp)); CopyRow(result) }
    }
}

// 17. Base64
@Composable
private fun Base64Tool() {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Текст") })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                result = Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }) { Text("Закодировать") }
            OutlinedButton(onClick = {
                result = try { String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8) } catch (e: Exception) { "Ошибка декодирования" }
            }) { Text("Раскодировать") }
        }
        if (result.isNotBlank()) { Text(result, modifier = Modifier.padding(top = 6.dp)); CopyRow(result) }
    }
}

// 18. Счётчик слов
@Composable
private fun WordCountTool() {
    var input by remember { mutableStateOf("") }
    val words = input.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    Column {
        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth().height(120.dp), label = { Text("Текст") })
        Text("Слов: $words · Символов: ${input.length} · Без пробелов: ${input.count { !it.isWhitespace() }}",
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

// 19. Случайный цвет
@Composable
private fun ColorTool() {
    var color by remember { mutableStateOf(Color(0xFF2196F3)) }
    var hex by remember { mutableStateOf("#2196F3") }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(48.dp).padding(2.dp)
                    .then(Modifier)
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(10.dp)) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.size(44.dp)) {}
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(hex, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val r = Random.nextInt(256); val g = Random.nextInt(256); val b = Random.nextInt(256)
            color = Color(r, g, b)
            hex = String.format("#%02X%02X%02X", r, g, b)
        }) { Text("Случайный цвет") }
        CopyRow(hex)
    }
}

// 20. Счёт очков на двоих
@Composable
private fun ScoreboardTool() {
    var a by remember { mutableStateOf(0) }
    var b by remember { mutableStateOf(0) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Игрок A")
            Text("$a", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { a++ }) { Text("+1") }
            OutlinedButton(onClick = { if (a > 0) a-- }) { Text("−") }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Игрок B")
            Text("$b", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { b++ }) { Text("+1") }
            OutlinedButton(onClick = { if (b > 0) b-- }) { Text("−") }
        }
    }
}
