package app.yodo.messenger.features.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.LocalColorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// НОВОЕ (чат поддержки): админ-панель со списком всех обращений в поддержку.
// Тап по беседе открывает тот же ChatScreen (chatId = support_<uid>) — ответ уходит в тот же чат.
// НОВОЕ (п.19 ТЗ): долгий тап по обращению открывает диалог ограничения возможности
// писать в поддержку (отдельно от полной блокировки аккаунта) — на время или навсегда.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdminPanelScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: AdminPanelViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()
    val restrictionDialog by viewModel.restrictionDialog.collectAsState()
    val colorTheme = LocalColorTheme.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    text = "Поддержка — обращения",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!viewModel.isAdmin) {
                Text(
                    text = "Доступ только для администраторов.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center
                )
            } else if (conversations.isEmpty()) {
                Text(
                    text = "Пока нет обращений в поддержку.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations, key = { it.chatId }) { conv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenConversation(conv.chatId) },
                                    // НОВОЕ (п.19 ТЗ): долгий тап — ограничить обращения в поддержку.
                                    onLongClick = { viewModel.openRestrictionDialog(conv.userId, conv.userName) }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colorTheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = conv.userName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (conv.awaitingReply) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE53935))
                                        )
                                    }
                                }
                                if (conv.userEmail.isNotBlank()) {
                                    Text(
                                        text = conv.userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = conv.lastMessage.ifBlank { "Нет сообщений" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }

    if (restrictionDialog != null) {
        SupportRestrictionDialog(
            state = restrictionDialog!!,
            onDismiss = { viewModel.closeRestrictionDialog() },
            onApply = { reason, durationMillis -> viewModel.applyRestriction(reason, durationMillis) },
            onRemove = { viewModel.removeRestriction() }
        )
    }
}

// НОВОЕ (п.19 ТЗ): варианты срока ограничения — на время (несколько готовых
// пресетов) или навсегда.
private enum class RestrictionDuration(val label: String, val millis: Long?) {
    HOUR_1("1 час", 60L * 60_000L),
    DAY_1("1 день", 24L * 60 * 60_000L),
    DAY_7("7 дней", 7L * 24 * 60 * 60_000L),
    DAY_30("30 дней", 30L * 24 * 60 * 60_000L),
    FOREVER("Навсегда", null)
}

// НОВОЕ (п.19 ТЗ): диалог для администрации — ограничить (или снять ограничение)
// возможность конкретного пользователя писать в поддержку, отдельно от глобального бана.
@Composable
private fun SupportRestrictionDialog(
    state: AdminPanelViewModel.RestrictionDialogState,
    onDismiss: () -> Unit,
    onApply: (reason: String, durationMillis: Long?) -> Unit,
    onRemove: () -> Unit
) {
    var reason by rememberSaveable(state.userId) { mutableStateOf("") }
    var selectedDuration by remember(state.userId) { mutableStateOf(RestrictionDuration.DAY_1) }

    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        title = { Text("Ограничить обращения в поддержку") },
        text = {
            Column {
                Text(
                    text = "Пользователь: ${state.userName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                if (state.isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Загрузка текущего статуса...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (state.current != null) {
                    val until = state.current.expiresAt
                    val untilText = if (until == null) "навсегда" else
                        "до " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(until))
                    Text(
                        text = "Уже ограничен ($untilText)" +
                            (if (state.current.reason.isNotBlank()) ". Причина: ${state.current.reason}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    text = "Срок ограничения:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    RestrictionDuration.entries.forEach { option ->
                        FilterChip(
                            selected = selectedDuration == option,
                            onClick = { selectedDuration = option },
                            label = { Text(option.label) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text("Причина (необязательно)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    maxLines = 3
                )
                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(reason.trim(), selectedDuration.millis) },
                enabled = !state.isSubmitting
            ) {
                Text("Ограничить")
            }
        },
        dismissButton = {
            Row {
                if (state.current != null) {
                    TextButton(onClick = onRemove, enabled = !state.isSubmitting) {
                        Text("Снять ограничение")
                    }
                }
                TextButton(onClick = onDismiss, enabled = !state.isSubmitting) {
                    Text("Отмена")
                }
            }
        }
    )
}
