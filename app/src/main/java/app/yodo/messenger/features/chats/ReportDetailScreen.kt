package app.yodo.messenger.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.Report
import app.yodo.messenger.domain.model.ReportComment
import app.yodo.messenger.domain.model.ReportResolution
import app.yodo.messenger.domain.model.ReportStatus
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.ReportTargetType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun ReportDetailScreen(
    onBackClick: () -> Unit,
    viewModel: ReportDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val actionCompleted by viewModel.actionCompleted.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var showResolveDialog by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(actionCompleted) {
        if (actionCompleted) onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Жалоба", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading || uiState.report == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }
        val report = uiState.report!!

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { ReportSummaryCard(report) }

                if (report.deletionScheduledAt != null && !report.deletionCancelled) {
                    item {
                        ScheduledDeletionCard(
                            executeAt = report.deletionScheduledAt,
                            onCancel = { viewModel.cancelScheduledDeletion() }
                        )
                    }
                }

                uiState.bulkDeletionScheduledAt?.let { executeAt ->
                    item {
                        BulkScheduledCard(executeAt)
                    }
                }

                if (uiState.contextMessages.isNotEmpty()) {
                    item {
                        Text(
                            "Контекст жалобы",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(uiState.contextMessages, key = { it.id }) { message ->
                        MessageContextRow(
                            message = message,
                            isTarget = message.id == report.targetMessageId
                        )
                    }
                }

                if (report.targetType == ReportTargetType.MESSAGE && report.targetMessageId != null) {
                    item {
                        OutlinedButton(
                            onClick = { showBulkDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Удалить сообщения пользователя за период")
                        }
                    }
                }

                item {
                    Text(
                        "Обсуждение",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(uiState.comments, key = { it.id }) { comment ->
                    ReportCommentRow(comment)
                }
            }

            if (report.status == ReportStatus.PENDING && report.deletionScheduledAt == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDismissDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Отклонить") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showResolveDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Принять меры") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Комментарий...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = {
                    if (commentText.isNotBlank()) {
                        viewModel.addComment(commentText)
                        commentText = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }

        if (uiState.isSubmittingAction) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f))) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        if (showDismissDialog) {
            DismissReportDialog(
                onDismiss = { showDismissDialog = false },
                onConfirm = { comment ->
                    showDismissDialog = false
                    viewModel.dismiss(comment)
                }
            )
        }
        if (showResolveDialog) {
            ResolveReportDialog(
                report = report,
                onDismiss = { showResolveDialog = false },
                onConfirm = { resolution, comment, deleteMessage, banUser, silentDelete ->
                    showResolveDialog = false
                    viewModel.resolve(resolution, comment, deleteMessage, banUser, silentDelete)
                }
            )
        }
        if (showBulkDeleteDialog) {
            BulkDeleteDialog(
                userName = report.targetUserName,
                onDismiss = { showBulkDeleteDialog = false },
                onConfirm = { startAt, endAt ->
                    showBulkDeleteDialog = false
                    viewModel.scheduleBulkDeletion(startAt, endAt)
                }
            )
        }

        errorMessage?.let {
            AlertDialog(
                onDismissRequest = { viewModel.consumeErrorMessage() },
                confirmButton = { TextButton(onClick = { viewModel.consumeErrorMessage() }) { Text("Ок") } },
                title = { Text("Ошибка") },
                text = { Text(it) }
            )
        }
    }
}

@Composable
private fun ScheduledDeletionCard(
    executeAt: Long,
    onCancel: () -> Unit
) {
    val remaining = (executeAt - System.currentTimeMillis()).coerceAtLeast(0L)
    val hours = (remaining / (60L * 60L * 1000L)).coerceAtLeast(0L)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(12.dp)
    ) {
        Text("Удаление запланировано", fontWeight = FontWeight.Bold)
        Text(
            "Сообщение будет удалено через 24 часа после решения администратора. " +
                "Сейчас осталось примерно ${hours + 1} ч.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onClick = onCancel) {
            Text("Отменить удаление")
        }
    }
}

@Composable
private fun BulkScheduledCard(executeAt: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp)
    ) {
        Text("Массовое удаление запланировано", fontWeight = FontWeight.Bold)
        Text(
            "Сообщения выбранного пользователя за период будут обработаны через 24 часа. " +
                "До этого момента их можно не удалять.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "Выполнение: ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(executeAt))}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MessageContextRow(message: Message, isTarget: Boolean) {
    val title = if (isTarget) "⚑ Сообщение из жалобы" else "Сообщение"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isTarget) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .padding(10.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
            color = if (isTarget) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            message.previewText().ifBlank { "Медиа/сообщение без текста" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun BulkDeleteDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val today = LocalDate.now()
    var startText by remember { mutableStateOf(today.minusDays(7).format(formatter)) }
    var endText by remember { mutableStateOf(today.format(formatter)) }
    var selectedDays by remember { mutableStateOf(7L) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyDays(days: Long) {
        selectedDays = days
        startText = today.minusDays(days).format(formatter)
        endText = today.format(formatter)
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Массовое удаление") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Удалить сообщения пользователя «$userName» за выбранный период? " +
                        "Удаление будет выполнено только через 24 часа."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedDays == 1L,
                        onClick = { applyDays(1) },
                        label = { Text("24 ч") }
                    )
                    FilterChip(
                        selected = selectedDays == 7L,
                        onClick = { applyDays(7) },
                        label = { Text("7 дней") }
                    )
                    FilterChip(
                        selected = selectedDays == 30L,
                        onClick = { applyDays(30) },
                        label = { Text("30 дней") }
                    )
                }
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; selectedDays = 0L },
                    label = { Text("С даты (ДД.ММ.ГГГГ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it; selectedDays = 0L },
                    label = { Text("По дату (ДД.ММ.ГГГГ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val start = LocalDate.parse(startText, formatter)
                    val end = LocalDate.parse(endText, formatter)
                    if (end.isBefore(start)) {
                        error = "Конечная дата не может быть раньше начальной."
                    } else {
                        val zone = ZoneId.systemDefault()
                        val startAt = start.atStartOfDay(zone).toInstant().toEpochMilli()
                        val endAt = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                        onConfirm(startAt, endAt)
                    }
                } catch (_: DateTimeParseException) {
                    error = "Введите даты в формате ДД.ММ.ГГГГ."
                }
            }) { Text("Запланировать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ReportSummaryCard(report: Report) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // НОВОЕ (AD): пометка «Обжалование» для обращений по блокировке.
        if (report.isAppeal) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF8E24AA).copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("ОБЖАЛОВАНИЕ БЛОКИРОВКИ", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8E24AA), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(report.reason.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (report.customReasonText.isNotBlank()) {
            Text(report.customReasonText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        // НОВОЕ (AD): фото, приложенное к обжалованию.
        report.appealPhotoBase64?.let { b64 ->
            val bmp = remember(b64) { app.yodo.messenger.util.ImageUtils.decodeBase64ToBitmap(b64) }
            bmp?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Фото обжалования",
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        InfoRow("Тип", if (report.targetType == ReportTargetType.MESSAGE) "Сообщение" else "Пользователь")
        InfoRow("Нарушитель", report.targetUserName)
        InfoRow("Автор жалобы", report.reporterName)
        InfoRow("Дата", formatFullTimestamp(report.createdAt))
        if (!report.targetMessagePreview.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    "«${report.targetMessagePreview}»",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (report.status != ReportStatus.PENDING) {
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow("Статус", report.status.label)
            report.resolution?.let { InfoRow("Решение", it.label) }
            report.reviewedByName?.let { InfoRow("Рассмотрел", it) }
            if (report.reviewerComment.isNotBlank()) {
                InfoRow("Комментарий", report.reviewerComment)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReportCommentRow(comment: ReportComment) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row {
            Text(comment.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                formatFullTimestamp(comment.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DismissReportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отклонить жалобу?") },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий (необязательно)") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(comment) }) { Text("Отклонить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ResolveReportDialog(
    report: Report,
    onDismiss: () -> Unit,
    onConfirm: (ReportResolution, String, Boolean, Boolean, Boolean) -> Unit
) {
    var deleteMessage by remember { mutableStateOf(report.targetType == ReportTargetType.MESSAGE) }
    var banUser by remember { mutableStateOf(false) }
    // НОВОЕ (баг 10): тихое удаление — доступно только вместе с удалением сообщения.
    var silentDelete by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Принять меры") },
        text = {
            Column {
                if (report.targetType == ReportTargetType.MESSAGE && report.targetMessageId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = deleteMessage, onCheckedChange = { deleteMessage = it })
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Удалить сообщение")
                    }
                    // НОВОЕ (баг 10): тихое удаление — сообщение исчезает бесследно,
                    // без заглушки «Сообщение удалено администратором».
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = silentDelete,
                            onCheckedChange = {
                                silentDelete = it
                                if (it) deleteMessage = true
                            },
                            enabled = deleteMessage
                        )
                        Icon(
                            Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                            tint = if (deleteMessage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Тихое удаление (без пометки)",
                            color = if (deleteMessage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (deleteMessage) {
                        Text(
                            if (silentDelete) "Сообщение будет удалено бесследно — участники его больше не увидят."
                            else "В чате появится пометка «Сообщение удалено администратором».",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp).fillMaxWidth()
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = banUser, onCheckedChange = { banUser = it })
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Забанить пользователя")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий (необязательно)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val resolution = when {
                        banUser -> ReportResolution.USER_BANNED
                        deleteMessage -> ReportResolution.MESSAGE_DELETED
                        else -> ReportResolution.NO_ACTION
                    }
                    onConfirm(resolution, comment, deleteMessage, banUser, silentDelete)
                }
            ) { Text("Подтвердить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun formatFullTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(millis))
