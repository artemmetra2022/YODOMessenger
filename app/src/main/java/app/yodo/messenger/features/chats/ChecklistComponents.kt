package app.yodo.messenger.features.chats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import app.yodo.messenger.domain.model.SharedChecklist

@Composable
fun ChecklistCreationDialog(onDismiss: () -> Unit, onSend: (String, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var itemText by remember { mutableStateOf("") }
    val items = itemText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val valid = runCatching { SharedChecklist.create(title, items) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checklist_create)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.checklist_group_notice))
                OutlinedTextField(value = title, onValueChange = { if (it.length <= SharedChecklist.MAX_TITLE) title = it },
                    label = { Text(stringResource(R.string.checklist_name)) }, singleLine = true)
                OutlinedTextField(value = itemText, onValueChange = { if (it.length <= 5000) itemText = it },
                    label = { Text(stringResource(R.string.checklist_items)) }, minLines = 4, maxLines = 10,
                    isError = itemText.isNotBlank() && (items.size > SharedChecklist.MAX_ITEMS || items.any { it.length > SharedChecklist.MAX_ITEM_LENGTH }))
                Text(stringResource(R.string.checklist_limits))
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSend(title.trim(), items) }) {
            Text(stringResource(R.string.feature_send))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_cancel)) } }
    )
}

@Composable
fun SharedChecklistBubble(
    checklist: SharedChecklist, currentUserId: String?, memberNames: Map<String, String>,
    enabled: Boolean, textColor: Color, onCheckedChange: (Int, Boolean) -> Unit
) {
    Column(Modifier.widthIn(min = 180.dp, max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(checklist.title, style = MaterialTheme.typography.titleSmall, color = textColor)
        Text(stringResource(R.string.checklist_progress, checklist.completedCount, checklist.items.size),
            style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
        checklist.items.forEachIndexed { index, label ->
            val by = checklist.completedBy(index)
            Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = by != null, onCheckedChange = { onCheckedChange(index, it) }, enabled = enabled)
                Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp)) {
                    Text(label, color = textColor,
                        textDecoration = if (by != null) TextDecoration.LineThrough else TextDecoration.None)
                    if (by != null) {
                        val name = if (by == currentUserId) stringResource(R.string.checklist_you)
                            else memberNames[by] ?: stringResource(R.string.checklist_member)
                        Text(stringResource(R.string.checklist_completed_by, name),
                            style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
