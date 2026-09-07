package app.yodo.messenger.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.PrivacyWho
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.theme.LocalColorTheme
import kotlinx.coroutines.launch

/**
 * НОВОЕ (п.15): настройки приватности вида «Кто может …».
 *
 * — Кто может приглашать в группы: ограничение проверяется при приглашении в
 *   группу/канал и при добавлении участников при создании группы.
 * — Кто может писать тебе: проверяется при создании НОВОГО личного чата.
 * — Кто может видеть мой профиль: чужой профиль показывается заглушкой.
 *
 * «Только знакомые» = те, кто есть в вашем списке контактов YODO (добавлены
 * по QR) либо с кем у вас уже есть личный чат.
 */
private val WHO_OPTIONS = listOf(
    PrivacyWho.EVERYONE to "Все",
    PrivacyWho.CONTACTS to "Только знакомые",
    PrivacyWho.NOBODY to "Никто"
)

private fun optionLabel(value: PrivacyWho): String =
    WHO_OPTIONS.firstOrNull { it.first == value }?.second ?: "Все"

private fun optionHint(value: PrivacyWho): String = when (value) {
    PrivacyWho.EVERYONE -> "любой пользователь"
    PrivacyWho.CONTACTS -> "только знакомые (контакты и те, с кем есть чат)"
    PrivacyWho.NOBODY -> "никто"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyWhoScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val whoCanInviteToGroups by viewModel.whoCanInviteToGroups.collectAsState()
    val whoCanMessageMe by viewModel.whoCanMessageMe.collectAsState()
    val whoCanSeeMyProfile by viewModel.whoCanSeeMyProfile.collectAsState()
    val messagePrivacyExceptions by viewModel.messagePrivacyExceptions.collectAsState()

    var editing by remember { mutableStateOf<PrivacyWhoKey?>(null) }
    var showExceptionsPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadMessagePrivacyExceptions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кто может…") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            PrivacyWhoCard(
                icon = { Icon(Icons.Filled.Diversity3, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(22.dp)) },
                title = "Приглашать в группы",
                currentValue = whoCanInviteToGroups,
                onClick = { editing = PrivacyWhoKey.INVITE }
            )
            Spacer(modifier = Modifier.height(10.dp))
            PrivacyWhoCard(
                icon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(22.dp)) },
                title = "Писать мне в личный чат",
                currentValue = whoCanMessageMe,
                onClick = { editing = PrivacyWhoKey.MESSAGE }
            )
            // НОВОЕ (исключения): показываем всегда под настройкой «Писать мне в личный
            // чат» — пользователи из этого списка могут писать вне зависимости от
            // выбранного режима (в т.ч. при «Никто»).
            Spacer(modifier = Modifier.height(6.dp))
            ExceptionsSection(
                exceptions = messagePrivacyExceptions,
                onAddClick = { showExceptionsPicker = true },
                onRemove = { viewModel.removeMessagePrivacyException(it) }
            )
            Spacer(modifier = Modifier.height(10.dp))
            PrivacyWhoCard(
                icon = { Icon(Icons.Filled.Visibility, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(22.dp)) },
                title = "Смотреть мой профиль",
                currentValue = whoCanSeeMyProfile,
                onClick = { editing = PrivacyWhoKey.PROFILE }
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "«Только знакомые» — это те, кто есть в вашем списке контактов YODO (добавлены по QR-коду), либо те, с кем у вас уже есть личный чат.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    editing?.let { key ->
        val (current, onPick) = when (key) {
            PrivacyWhoKey.INVITE -> whoCanInviteToGroups to viewModel::setWhoCanInviteToGroups
            PrivacyWhoKey.MESSAGE -> whoCanMessageMe to viewModel::setWhoCanMessageMe
            PrivacyWhoKey.PROFILE -> whoCanSeeMyProfile to viewModel::setWhoCanSeeMyProfile
        }
        PrivacyWhoDialog(
            title = when (key) {
                PrivacyWhoKey.INVITE -> "Кто может приглашать в группы?"
                PrivacyWhoKey.MESSAGE -> "Кто может писать тебе?"
                PrivacyWhoKey.PROFILE -> "Кто может видеть твой профиль?"
            },
            currentValue = current,
            onDismiss = { editing = null },
            onPick = { value ->
                onPick(value)
                editing = null
            }
        )
    }

    if (showExceptionsPicker) {
        ExceptionsPickerDialog(
            alreadyAdded = messagePrivacyExceptions.map { it.uid }.toSet(),
            onDismiss = { showExceptionsPicker = false },
            onPick = { uid ->
                viewModel.addMessagePrivacyException(uid)
                showExceptionsPicker = false
            }
        )
    }
}

private enum class PrivacyWhoKey { INVITE, MESSAGE, PROFILE }

@Composable
private fun PrivacyWhoCard(
    icon: @Composable () -> Unit,
    title: String,
    currentValue: PrivacyWho,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = optionLabel(currentValue) + " — " + optionHint(currentValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrivacyWhoDialog(
    title: String,
    currentValue: PrivacyWho,
    onDismiss: () -> Unit,
    onPick: (PrivacyWho) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                WHO_OPTIONS.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == currentValue,
                            onClick = { onPick(value) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                optionHint(value),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// НОВОЕ (исключения из «Кто может мне писать»): карточка со списком пользователей,
// которым разрешено писать всегда, вне зависимости от выбранного режима (в т.ч. NOBODY).
// Показывается всегда под настройкой «Писать мне в личный чат», а не только при NOBODY/
// CONTACTS — так пользователь может подготовить список заранее.
@Composable
private fun ExceptionsSection(
    exceptions: List<YodoUser>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit
) {
    val colorTheme = LocalColorTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Исключения",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onAddClick) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
            Text(
                text = "Эти пользователи всегда могут писать вам, даже если выбрано «Никто» или «Только знакомые».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (exceptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                exceptions.forEach { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = user.displayName.ifBlank { user.username ?: "Пользователь" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemove(user.uid) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Удалить из исключений",
                                tint = colorTheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// НОВОЕ (исключения): простой диалог поиска пользователя по имени/username для
// добавления в список исключений. Переиспользует userRepository.searchUsers —
// тот же паттерн, что в CreateGroupViewModel/InviteToChannelViewModel/SearchViewModel.
@Composable
private fun ExceptionsPickerDialog(
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    viewModel: PrivacyExceptionsPickerViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    LaunchedEffect(query) { viewModel.search(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в исключения") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Имя или @username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    when {
                        isSearching -> Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                        results.isEmpty() && query.isNotBlank() -> Text(
                            "Никого не найдено",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                        else -> LazyColumn {
                            items(results.filter { it.uid !in alreadyAdded }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            user.displayName.ifBlank { "Пользователь" },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        user.username?.let {
                                            Text(
                                                "@$it",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(onClick = { onPick(user.uid) }) { Text("Добавить") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
