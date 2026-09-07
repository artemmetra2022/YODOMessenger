package app.yodo.messenger.features.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.SchoolTeacherProfile
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.theme.YodoError

/**
 * НОВОЕ (учительские страницы): админ-панель профилей учителей — создание
 * профилей и привязка аккаунта мессенджера к учителю («второй профиль»).
 * Перенос учительских разделов админки Telegram-бота: админ решает, какой
 * аккаунт получает права учителя; учитель затем сам ведёт свою страницу.
 */
@Composable
fun SchoolTeacherAdminScreen(
    onBackClick: () -> Unit,
    viewModel: SchoolTeacherAdminViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val message by viewModel.message.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var linkTarget by remember { mutableStateOf<SchoolTeacherProfile?>(null) }
    var unlinkTarget by remember { mutableStateOf<SchoolTeacherProfile?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    if (!viewModel.isAppAdmin) {
        Scaffold(
            topBar = { SchoolTopBar("Профили учителей", onBackClick) },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
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
        topBar = { SchoolTopBar("Профили учителей", onBackClick) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Привяжите аккаунт мессенджера к учителю — пользователь получит " +
                        "«Мою страницу учителя» в разделе «Школа» (файл урока и вопросы).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            item {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать профиль учителя")
                }
            }
            if (profiles.isEmpty()) {
                item {
                    Text(
                        "Профилей пока нет. Создайте первый — имя должно совпадать " +
                            "с именем в справочнике «Учителя», чтобы страница открылась из карточки.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            } else {
                items(profiles.size) { index ->
                    val profile = profiles[index]
                    TeacherProfileAdminCard(
                        profile = profile,
                        onLink = { linkTarget = profile },
                        onUnlink = { unlinkTarget = profile }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateTeacherProfileDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, subject ->
                viewModel.createProfile(name, subject)
                showCreateDialog = false
            }
        )
    }
    linkTarget?.let { target ->
        LinkUserDialog(
            teacherName = target.name,
            results = searchResults,
            onQueryChanged = { viewModel.onSearchQuery(it) },
            onLink = { user -> viewModel.linkUser(target.name, user) },
            onDismiss = {
                linkTarget = null
                viewModel.clearSearch()
            }
        )
    }
    unlinkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unlinkTarget = null },
            title = { Text("Снять привязку?") },
            text = {
                Text("Аккаунт «${target.linkedUserName}» потеряет доступ к странице «${target.name}».")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unlinkUser(target.name)
                    unlinkTarget = null
                }) { Text("Снять", color = YodoError) }
            },
            dismissButton = { TextButton(onClick = { unlinkTarget = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun TeacherProfileAdminCard(
    profile: SchoolTeacherProfile,
    onLink: () -> Unit,
    onUnlink: () -> Unit
) {
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (profile.subject.isNotBlank()) {
                    Text(
                        profile.subject,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (profile.linkedUserId.isBlank()) "❌ Не привязан к аккаунту"
                    else "✅ Аккаунт: ${profile.linkedUserName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.linkedUserId.isBlank()) YodoError
                    else MaterialTheme.colorScheme.primary
                )
                if (profile.subscribers.isNotEmpty()) {
                    Text(
                        "Подписчиков файла: ${profile.subscribers.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onLink) {
                Icon(Icons.Filled.Link, contentDescription = "Привязать аккаунт",
                    tint = MaterialTheme.colorScheme.primary)
            }
            if (profile.linkedUserId.isNotBlank()) {
                IconButton(onClick = onUnlink) {
                    Icon(Icons.Filled.LinkOff, contentDescription = "Снять привязку", tint = YodoError)
                }
            }
        }
    }
}

@Composable
private fun CreateTeacherProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, subject: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый профиль учителя") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Имя учителя (как в справочнике)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it },
                    label = { Text("Предмет (необязательно)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, subject) },
                enabled = name.isNotBlank()
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun LinkUserDialog(
    teacherName: String,
    results: List<YodoUser>,
    onQueryChanged: (String) -> Unit,
    onLink: (YodoUser) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Привязать аккаунт к «${teacherName}»") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChanged(it)
                    },
                    label = { Text("Имя или username пользователя") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (query.isNotBlank() && results.isEmpty()) {
                    Text(
                        "Ничего не найдено",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (results.isNotEmpty()) {
                    Text(
                        "Нажмите на пользователя для привязки:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(results.size) { index ->
                            val user = results[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onLink(user) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                                    user.username?.let {
                                        Text(
                                            "@$it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

