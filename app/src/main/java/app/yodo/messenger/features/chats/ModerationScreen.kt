package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.DeletedMessageRecord
import app.yodo.messenger.domain.model.ModerationDeleteReason
import app.yodo.messenger.domain.model.ModerationMatchType
import app.yodo.messenger.domain.model.ModerationRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModerationScreen(
    onBack: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsState(initial = emptyList())
    val history by viewModel.history.collectAsState(initial = emptyList())
    val error by viewModel.error.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showRuleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Модерация") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Назад") } },
            actions = {
                if (tab == 0) IconButton(onClick = { showRuleDialog = true }) { Icon(Icons.Filled.Add, "Добавить правило") }
            }
        ) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Автофильтр") }, icon = { Icon(Icons.Filled.FilterAlt, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Удалённые") }, icon = { Icon(Icons.Filled.History, null) })
            }
            if (tab == 0) RulesTab(rules, viewModel)
            else HistoryTab(history, viewModel)
        }
    }

    if (showRuleDialog) {
        RuleDialog(
            onDismiss = { showRuleDialog = false },
            onSave = { viewModel.saveRule(it); showRuleDialog = false }
        )
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Модерация") }, text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun RulesTab(rules: List<ModerationRule>, vm: ModerationViewModel) {
    if (rules.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Автоматических правил пока нет", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Добавьте правило для автоматического удаления сообщений со ссылками или запрещёнными паттернами.")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rules, key = { it.id }) { rule ->
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.name.ifBlank { "Без названия" }, fontWeight = FontWeight.SemiBold)
                        Text("${rule.matchType.label} • ${rule.reason.label}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Text(rule.pattern, maxLines = 2, overflow = TextOverflow.Ellipsis, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                    FilterChip(selected = rule.enabled, onClick = { vm.saveRule(rule.copy(enabled = !rule.enabled)) }, label = { Text(if (rule.enabled) "Вкл" else "Выкл") })
                    IconButton(onClick = { vm.deleteRule(rule.id) }) { Icon(Icons.Filled.Delete, "Удалить") }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(history: List<DeletedMessageRecord>, vm: ModerationViewModel) {
    if (history.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.History, null)
            Spacer(Modifier.height(8.dp))
            Text("История удалённых сообщений пуста")
            Text("Удалённые сообщения доступны для восстановления 30 дней.")
        }
        return
    }
    val format = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(history, key = { it.id }) { item ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.senderName.ifBlank { item.senderId }, fontWeight = FontWeight.SemiBold)
                            Text("${item.reason.label} • ${format.format(Date(item.deletedAt))}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        if (item.automatic) Text("AUTO", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(item.preview.ifBlank { "Медиа/файл без текста" }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text("Восстановить до ${format.format(Date(item.restoreUntil))}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.restore(item) }) {
                        Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text("Восстановить")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(onDismiss: () -> Unit, onSave: (ModerationRule) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf("") }
    var matchType by remember { mutableStateOf(ModerationMatchType.URL_DOMAIN) }
    var reason by remember { mutableStateOf(ModerationDeleteReason.RULES) }
    var matchExpanded by remember { mutableStateOf(false) }
    var reasonExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое правило") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(pattern, { pattern = it }, label = { Text(if (matchType == ModerationMatchType.URL_DOMAIN) "Домен, например example.com" else "Паттерн") }, singleLine = true)
                BoxLikeDropdown("Тип совпадения: ${matchType.label}", matchExpanded, { matchExpanded = true }, { matchType = it; matchExpanded = false }) { ModerationMatchType.entries.forEach { type -> DropdownMenuItem(text = { Text(type.label) }, onClick = { matchType = type; matchExpanded = false }) } }
                BoxLikeDropdown("Причина: ${reason.label}", reasonExpanded, { reasonExpanded = true }) { ModerationDeleteReason.entries.forEach { r -> DropdownMenuItem(text = { Text(r.label) }, onClick = { reason = r; reasonExpanded = false }) } }
            }
        },
        confirmButton = { Button(onClick = { onSave(ModerationRule(name = name, pattern = pattern, matchType = matchType, reason = reason)) }, enabled = pattern.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun BoxLikeDropdown(label: String, expanded: Boolean, onOpen: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        OutlinedTextField(value = label, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen))
        DropdownMenu(expanded = expanded, onDismissRequest = { /* parent owns state */ }, content = content)
    }
}
