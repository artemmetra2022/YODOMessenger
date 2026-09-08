package app.yodo.messenger.features.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.SchoolNews
import app.yodo.messenger.ui.theme.YodoError

/**
 * НОВОЕ (раздел «Школа»): школьная админ-панель для админов приложения —
 * перенос админских функций бота: создание/редактирование/закрепление/удаление
 * новостей (/event, admin_news), создание/удаление опросов (/polls),
 * просмотр идей (/idea) и оценок (/reviews). Доступ проверяет ViewModel
 * (isAppAdmin), не-админу экран показывает заглушку.
 */
@Composable
fun SchoolAdminScreen(
    onBackClick: () -> Unit,
    onOpenTeacherProfiles: () -> Unit = {},
    viewModel: SchoolAdminViewModel = hiltViewModel()
) {
    val news by viewModel.news.collectAsState()
    val message by viewModel.message.collectAsState()
    // НОВОЕ (глобальное управление разделом): скрытие «Школы» у всех и
    // пометка актуальности расписания уроков.
    val schoolSectionHidden by viewModel.schoolSectionHidden.collectAsState()
    val scheduleStatus by viewModel.scheduleStatus.collectAsState()

    var showAddNewsDialog by remember { mutableStateOf(false) }
    var showAddPollDialog by remember { mutableStateOf(false) }
    var showScheduleStatusDialog by remember { mutableStateOf(false) }
    var editNewsTarget by remember { mutableStateOf<SchoolNews?>(null) }
    var deleteNewsTarget by remember { mutableStateOf<SchoolNews?>(null) }
    var deletePollTarget by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    if (!viewModel.isAppAdmin) {
        Scaffold(
            topBar = { SchoolTopBar("Администрирование школы", onBackClick) },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "⛔ Доступ только для администраторов приложения.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SchoolTopBar("Администрирование школы", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "⚙️ Раздел и расписание",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    // НОВОЕ (глобальное скрытие «Школы»): прячет кнопку у всех
                    // обычных пользователей; админы видят её всегда.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🙈 Скрыть раздел «Школа» у всех",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Обычные пользователи не увидят кнопку «Школа». Админы видят её всегда",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = schoolSectionHidden,
                            onCheckedChange = { viewModel.setSchoolSectionHidden(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // НОВОЕ (пометка расписания): актуально/неактуально + дата.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "📄 Пометка расписания уроков",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            val status = scheduleStatus
                            Text(
                                if (status == null) "Пометка ещё не ставилась"
                                else if (status.actual)
                                    if (status.untilDate.isBlank()) "✅ Актуально"
                                    else "✅ Актуально на ${status.untilDate}"
                                else
                                    if (status.untilDate.isBlank()) "⚠️ Неактуальное"
                                    else "⚠️ Неактуальное (было на ${status.untilDate})",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status?.actual == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(onClick = { showScheduleStatusDialog = true }) {
                            Text("Изменить")
                        }
                    }
                }
            }
            item {
                Text(
                    "📰 Управление новостями",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            item {
                Button(
                    onClick = { showAddNewsDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать новость")
                }
            }
            if (news.isEmpty()) {
                item {
                    Text(
                        "Новостей пока нет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            } else {
                items(news.size) { index ->
                    val item = news[index]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (item.pinned) "📌 " else "") + "Новость от ${item.sender}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.setNewsPinned(item.id, !item.pinned) }) {
                                Icon(
                                    Icons.Filled.PushPin, contentDescription = "Закрепить",
                                    tint = if (item.pinned) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deleteNewsTarget = item }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = YodoError)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { editNewsTarget = item }) {
                            Text("✏️ Редактировать текст")
                        }
                    }
                }
            }

            item {
                Text(
                    "📊 Управление опросами",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
            item {
                Button(
                    onClick = { showAddPollDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Создать опрос")
                }
            }
            item {
                PollsAdminSection(
                    onDeletePoll = { deletePollTarget = it }
                )
            }

            item {
                Text(
                    "💡 Идеи пользователей",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
            item { IdeasAdminSection() }

            item {
                Text(
                    "🏫 Профили учителей",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
            item {
                Button(
                    onClick = onOpenTeacherProfiles,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Профили учителей и привязка аккаунтов")
                }
            }

            item {
                Text(
                    "⭐ Оценки раздела",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
            item { ReviewsAdminSection() }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showAddNewsDialog) {
        AddNewsDialog(
            onDismiss = { showAddNewsDialog = false },
            onConfirm = { sender, text, eventDate ->
                viewModel.addNews(sender, text, eventDate)
                showAddNewsDialog = false
            }
        )
    }
    if (showAddPollDialog) {
        AddPollDialog(
            onDismiss = { showAddPollDialog = false },
            onConfirm = { question, options ->
                viewModel.createPoll(question, options)
                showAddPollDialog = false
            }
        )
    }
    // НОВОЕ (пометка расписания): актуально/неактуально + дата, на которую
    // расписание актуально (свободная строка, показывается как есть).
    if (showScheduleStatusDialog) {
        ScheduleStatusDialog(
            initialActual = scheduleStatus?.actual ?: false,
            initialUntilDate = scheduleStatus?.untilDate ?: "",
            onDismiss = { showScheduleStatusDialog = false },
            onConfirm = { actual, untilDate ->
                viewModel.setScheduleStatus(actual, untilDate)
                showScheduleStatusDialog = false
            }
        )
    }
    editNewsTarget?.let { target ->
        EditTextDialog(
            title = "Редактирование новости",
            initial = target.text,
            onDismiss = { editNewsTarget = null },
            onConfirm = { text ->
                viewModel.editNewsText(target.id, text)
                editNewsTarget = null
            }
        )
    }
    deleteNewsTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteNewsTarget = null },
            title = { Text("Удалить новость?") },
            text = { Text(target.text) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNews(target.id)
                    deleteNewsTarget = null
                }) { Text("Удалить", color = YodoError) }
            },
            dismissButton = { TextButton(onClick = { deleteNewsTarget = null }) { Text("Отмена") } }
        )
    }
    deletePollTarget?.let { pollId ->
        AlertDialog(
            onDismissRequest = { deletePollTarget = null },
            title = { Text("Удалить опрос?") },
            text = { Text("Голосования будут потеряны.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePoll(pollId)
                    deletePollTarget = null
                }) { Text("Удалить", color = YodoError) }
            },
            dismissButton = { TextButton(onClick = { deletePollTarget = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun PollsAdminSection(onDeletePoll: (String) -> Unit) {
    val viewModel: SchoolAdminViewModel = hiltViewModel()
    val polls by viewModel.polls.collectAsState()
    if (polls.isEmpty()) {
        Text(
            "Активных опросов нет.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    } else {
        polls.forEach { poll ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${poll.question} (${poll.totalVotes} гол.)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDeletePoll(poll.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = YodoError)
                    }
                }
            }
        }
    }
}

@Composable
private fun IdeasAdminSection() {
    val viewModel: SchoolAdminViewModel = hiltViewModel()
    val ideas by viewModel.ideas.collectAsState()
    if (ideas.isEmpty()) {
        Text(
            "Идей пока нет.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    } else {
        ideas.take(20).forEach { idea ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    "💡 ${idea.authorName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(idea.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReviewsAdminSection() {
    val viewModel: SchoolAdminViewModel = hiltViewModel()
    val reviews by viewModel.reviews.collectAsState()
    if (reviews.isEmpty()) {
        Text(
            "Оценок пока нет.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    } else {
        val avg = reviews.map { it.stars }.average()
        Text(
            "Средняя оценка: ${"%.1f".format(avg)} ⭐ (${reviews.size} отзывов)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        reviews.take(20).forEach { review ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    "⭐".repeat(review.stars.coerceIn(0, 5)) + " " + review.authorName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (review.liked != "-") Text("✅ ${review.liked}", style = MaterialTheme.typography.bodySmall)
                if (review.disliked != "-") Text("❌ ${review.disliked}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AddNewsDialog(
    onDismiss: () -> Unit,
    onConfirm: (sender: String, text: String, eventDate: String) -> Unit
) {
    var sender by remember { mutableStateOf("Администрация гимназии") }
    var text by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая новость") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sender, onValueChange = { sender = it },
                    label = { Text("Отправитель") }, singleLine = true
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Информация о событии") }, minLines = 3
                )
                OutlinedTextField(
                    value = eventDate, onValueChange = { eventDate = it },
                    label = { Text("Дата события (ДД.ММ.ГГГГ ЧЧ:ММ, можно «-»)") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(sender, text, eventDate) },
                enabled = text.isNotBlank()
            ) { Text("Опубликовать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AddPollDialog(
    onDismiss: () -> Unit,
    onConfirm: (question: String, options: List<String>) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var optionsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый опрос") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question, onValueChange = { question = it },
                    label = { Text("Вопрос") }, singleLine = true
                )
                OutlinedTextField(
                    value = optionsText, onValueChange = { optionsText = it },
                    label = { Text("Варианты, каждый с новой строки (минимум 2)") }, minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val options = optionsText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                    onConfirm(question, options)
                },
                enabled = question.isNotBlank() && optionsText.split("\n").count { it.isNotBlank() } >= 2
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun EditTextDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Новый текст") }, minLines = 3
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * НОВОЕ (пометка расписания): диалог «актуально/неактуально» с датой, на
 * которую расписание актуально. Дата — свободная строка («15.09.2026»),
 * валидации не требует: показывается ученикам как есть.
 */
@Composable
private fun ScheduleStatusDialog(
    initialActual: Boolean,
    initialUntilDate: String,
    onDismiss: () -> Unit,
    onConfirm: (actual: Boolean, untilDate: String) -> Unit
) {
    var actual by remember { mutableStateOf(initialActual) }
    var untilDate by remember { mutableStateOf(initialUntilDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пометка расписания уроков") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (actual) "✅ Расписание актуально" else "⚠️ Расписание неактуально",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = actual, onCheckedChange = { actual = it })
                }
                OutlinedTextField(
                    value = untilDate, onValueChange = { untilDate = it },
                    label = { Text("Актуально на дату (напр. 15.09.2026, можно «-»)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(actual, untilDate) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
