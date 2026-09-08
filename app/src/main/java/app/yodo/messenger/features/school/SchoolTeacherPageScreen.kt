package app.yodo.messenger.features.school

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.SchoolTeacherQuestion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * НОВОЕ (учительские страницы): страница учителя — перенос из Telegram-бота.
 * Ученик: файл урока (ссылка), задать вопрос, подписка на обновления файла.
 * Владелец (аккаунт, привязанный админом): обновить файл урока (/setlesson),
 * скрыть/вернуть вопросы.
 */
@Composable
fun SchoolTeacherPageScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolTeacherPageViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val lessonFiles by viewModel.lessonFiles.collectAsState()
    val message by viewModel.message.collectAsState()

    var showSetFileDialog by remember { mutableStateOf(false) }
    var showAskDialog by remember { mutableStateOf(false) }
    var showAnswerDialogFor by remember { mutableStateOf<SchoolTeacherQuestion?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    val isOwner = viewModel.isOwner

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SchoolTopBar(
                if (isOwner) "Моя страница учителя" else (profile?.name ?: "Страница учителя"),
                onBackClick
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (profile == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Страница учителя пока не создана.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOwner) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Обратитесь к администратору — он создаст профиль и привяжет его к вашему аккаунту.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Шапка профиля
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                profile?.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (!profile?.subject.isNullOrBlank()) {
                                Text(
                                    profile?.subject ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Файл урока (/lesson)
            item {
                val fileUrl = profile?.fileUrl ?: ""
                val fileNote = profile?.fileNote ?: ""
                val fileUpdatedAt = profile?.fileUpdatedAt ?: 0L
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text(
                        "📄 Файл урока",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (fileUrl.isBlank()) {
                        Text(
                            if (isOwner) "Файл ещё не загружен — нажмите «Обновить файл урока»"
                            else "Учитель ещё не загрузил файл урока.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (fileNote.isNotBlank()) {
                            Text(
                                fileNote,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (fileUpdatedAt > 0) {
                            val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
                            Text(
                                "Обновлён: ${df.format(Date(fileUpdatedAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Открыть файл урока")
                        }
                    }
                    if (isOwner) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showSetFileDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✏️ Обновить файл урока")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.toggleSubscription() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (viewModel.amSubscribed) "🔕 Отписаться от обновлений"
                                else "🔔 Подписаться на обновления файла"
                            )
                        }
                        if (viewModel.amSubscribed) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Вы подписаны — уведомим, когда учитель обновит файл.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── История файлов урока (без текущего, последние 3)
            val previousFiles = remember(lessonFiles, profile?.fileUpdatedAt) {
                lessonFiles.filter { it.updatedAt != profile?.fileUpdatedAt }.take(3)
            }
            if (previousFiles.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                    ) {
                        Text(
                            "🗂 Предыдущие файлы",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        previousFiles.forEachIndexed { index, file ->
                            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Description, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.fileNote.ifBlank { "Файл урока" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                    if (file.updatedAt > 0) {
                                        val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
                                        Text(
                                            df.format(Date(file.updatedAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(file.fileUrl)))
                                    }
                                }) {
                                    Text("Открыть")
                                }
                            }
                        }
                    }
                }
            }

            // ── Вопросы (/ask)
            item {
                val unanswered = viewModel.unansweredCount
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text(
                        "❓ Вопросы учеников",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isOwner && unanswered > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ждут ответа: $unanswered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!isOwner) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showAskDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Задать вопрос учителю")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Скрытые вопросы видны только учителю.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Скрытые вопросы ученикам не показываются (только владельцу/админу).
            val visibleQuestions = if (isOwner || viewModel.isAppAdmin) questions
            else questions.filter { !it.hidden }
            if (visibleQuestions.isEmpty()) {
                item {
                    Text(
                        "Вопросов пока нет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                items(visibleQuestions.size) { index ->
                    val q = visibleQuestions[index]
                    QuestionCard(
                        fromName = q.fromName,
                        text = q.text,
                        createdAt = q.createdAt,
                        hidden = q.hidden,
                        answer = q.answer,
                        answeredAt = q.answeredAt,
                        canManage = isOwner || viewModel.isAppAdmin,
                        canAnswer = isOwner || viewModel.isAppAdmin,
                        onToggleHidden = { viewModel.setHidden(q.id, !q.hidden) },
                        onAnswer = { showAnswerDialogFor = q }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showSetFileDialog) {
        SetLessonFileDialog(
            initialUrl = profile?.fileUrl ?: "",
            initialNote = profile?.fileNote ?: "",
            onDismiss = { showSetFileDialog = false },
            onConfirm = { url, note ->
                viewModel.setLessonFile(url, note)
                showSetFileDialog = false
            }
        )
    }
    if (showAskDialog) {
        AskQuestionDialog(
            teacherName = profile?.name ?: "",
            onDismiss = { showAskDialog = false },
            onConfirm = { text ->
                viewModel.askQuestion(text)
                showAskDialog = false
            }
        )
    }
    showAnswerDialogFor?.let { question ->
        AnswerQuestionDialog(
            questionText = question.text,
            initialAnswer = question.answer,
            onDismiss = { showAnswerDialogFor = null },
            onConfirm = { answer ->
                viewModel.answerQuestion(question.id, answer)
                showAnswerDialogFor = null
            }
        )
    }
}

@Composable
private fun QuestionCard(
    fromName: String,
    text: String,
    createdAt: Long,
    hidden: Boolean,
    answer: String,
    answeredAt: Long,
    canManage: Boolean,
    canAnswer: Boolean,
    onToggleHidden: () -> Unit,
    onAnswer: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "от $fromName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (answer.isBlank()) {
                Text(
                    "ждёт ответа",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (createdAt > 0) {
                Text(
                    df.format(Date(createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canManage) {
                IconButton(onClick = onToggleHidden) {
                    Icon(
                        if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (hidden) "Показать" else "Скрыть",
                        tint = if (hidden) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        if (hidden) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Скрыт — видно только учителю",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        // ── Ответ учителя
        if (answer.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ответ учителя",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (answeredAt > 0) {
                    Text(
                        df.format(Date(answeredAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (canAnswer) {
                TextButton(onClick = onAnswer) {
                    Text("✏️ Изменить ответ")
                }
            }
        } else if (canAnswer) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(onClick = onAnswer, modifier = Modifier.fillMaxWidth()) {
                Text("✍️ Ответить")
            }
        }
    }
}

@Composable
private fun SetLessonFileDialog(
    initialUrl: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (fileUrl: String, fileNote: String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Файл урока") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Ссылка на файл (Google Drive и т.п.)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Описание (что за файл)") },
                    singleLine = true
                )
                Text(
                    "Подписчики получат уведомление об обновлении.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url, note) },
                enabled = url.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AskQuestionDialog(
    teacherName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вопрос к «${teacherName}»") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Ваш вопрос") },
                minLines = 3
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * НОВОЕ (ответы учителя): диалог ответа на вопрос ученика. Открыть может
 * владелец страницы или админ; если ответ уже есть — предзаполняется для
 * редактирования.
 */
@Composable
private fun AnswerQuestionDialog(
    questionText: String,
    initialAnswer: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var answer by remember { mutableStateOf(initialAnswer) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ответ на вопрос") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    questionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it },
                    label = { Text("Ваш ответ") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(answer) },
                enabled = answer.isNotBlank()
            ) { Text(if (initialAnswer.isBlank()) "Опубликовать" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
