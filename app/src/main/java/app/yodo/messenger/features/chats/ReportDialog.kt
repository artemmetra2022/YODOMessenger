package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.ReportReason

/**
 * Модальный диалог подачи жалобы. Используется и для сообщения (messageId != null),
 * и для профиля пользователя (messageId == null). targetUserName можно не передавать —
 * если не указано, имя будет разрешено внутри ViewModel по targetUserId.
 */
@Composable
fun ReportDialog(
    chatId: String,
    targetUserId: String,
    targetUserName: String,
    messageId: String? = null,
    messagePreview: String = "",
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit,
    viewModel: SubmitReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedReason by remember { mutableStateOf(ReportReason.SPAM) }
    var customText by remember { mutableStateOf("") }
    val resolvedName by viewModel.resolvedTargetName.collectAsState()

    LaunchedEffect(targetUserId, targetUserName) {
        if (targetUserName.isBlank()) viewModel.resolveTargetName(targetUserId)
    }

    LaunchedEffect(uiState.submitted) {
        if (uiState.submitted) {
            onSubmitted()
            viewModel.reset()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (messageId != null) "Пожаловаться на сообщение" else "Пожаловаться на пользователя") },
        text = {
            Column {
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                        Text(reason.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (selectedReason == ReportReason.OTHER) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("Опишите причину") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                uiState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp))
            } else {
                TextButton(
                    onClick = {
                        val nameToUse = targetUserName.ifBlank { resolvedName ?: "Пользователь" }
                        if (messageId != null) {
                            viewModel.submitMessageReport(
                                chatId, messageId, messagePreview, targetUserId, nameToUse,
                                selectedReason, customText
                            )
                        } else {
                            viewModel.submitUserReport(chatId, targetUserId, nameToUse, selectedReason, customText)
                        }
                    }
                ) { Text("Отправить") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
