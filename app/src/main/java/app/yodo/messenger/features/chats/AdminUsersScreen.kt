package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.AdminUserDetails
import app.yodo.messenger.domain.model.AdminRole
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.model.AdminUserModerationStats
import app.yodo.messenger.domain.model.AdminTimelineEntry
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.YodoError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun adminDate(value: Long): String =
    if (value <= 0L) "—" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(value))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenSecurity: () -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val details by viewModel.details.collectAsState()
    val error by viewModel.error.collectAsState()

    var search by remember { mutableStateOf("") }
    var statusMenu by remember { mutableStateOf(false) }
    var blockDialog by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var adminDialog by remember { mutableStateOf(false) }
    var adminRole by remember { mutableStateOf(AdminRole.ADMIN) }
    var presenceMenu by remember { mutableStateOf(false) }
    var difficultyMenu by remember { mutableStateOf(false) }
    var classMenu by remember { mutableStateOf(false) }
    var classDialog by remember { mutableStateOf(false) }
    var classValue by remember { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); viewModel.consumeError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Пользователи") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (viewModel.isAppAdmin) {
                        TextButton(onClick = { onOpenSecurity() }) { Text("2FA") }
                    }
                    IconButton(onClick = { viewModel.reload() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Поиск: UID, имя, @username, email") },
                leadingIcon = { Icon(Icons.Default.Search, "Поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    FilterChip(selected = filters.status != null, onClick = { statusMenu = true }, label = { Text(filters.status?.label ?: "Статус") })
                    DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                        DropdownMenuItem(text = { Text("Все статусы") }, onClick = { viewModel.setFilters(filters.copy(status = null)); statusMenu = false })
                        AdminUserStatus.entries.forEach { status ->
                            DropdownMenuItem(text = { Text(status.label) }, onClick = { viewModel.setFilters(filters.copy(status = status)); statusMenu = false })
                        }
                    }
                }
                Box {
                    FilterChip(selected = filters.presence != AdminPresenceFilter.ALL, onClick = { presenceMenu = true }, label = { Text(filters.presence.label) })
                    DropdownMenu(expanded = presenceMenu, onDismissRequest = { presenceMenu = false }) {
                        AdminPresenceFilter.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(p.label) }, onClick = { viewModel.setFilters(filters.copy(presence = p)); presenceMenu = false })
                        }
                    }
                }
                Box {
                    FilterChip(selected = filters.difficulty != AdminDifficultyFilter.ALL, onClick = { difficultyMenu = true }, label = { Text(if (filters.difficulty == AdminDifficultyFilter.DIFFICULT) "Трудные" else "Все") })
                    DropdownMenu(expanded = difficultyMenu, onDismissRequest = { difficultyMenu = false }) {
                        AdminDifficultyFilter.entries.forEach { d ->
                            DropdownMenuItem(text = { Text(d.label) }, onClick = { viewModel.setFilters(filters.copy(difficulty = d)); difficultyMenu = false })
                        }
                    }
                }
                Box {
                    FilterChip(selected = filters.classId.isNotBlank(), onClick = { classMenu = true }, label = { Text(if (filters.classId.isBlank()) "Класс" else filters.classId) })
                    DropdownMenu(expanded = classMenu, onDismissRequest = { classMenu = false }) {
                        DropdownMenuItem(text = { Text("Все классы") }, onClick = { viewModel.setFilters(filters.copy(classId = "")); classMenu = false })
                        viewModel.availableClasses().forEach { clazz ->
                            DropdownMenuItem(text = { Text(clazz) }, onClick = { viewModel.setFilters(filters.copy(classId = clazz)); classMenu = false })
                        }
                    }
                }
                OutlinedTextField(value = filters.country, onValueChange = { viewModel.setFilters(filters.copy(country = it)) }, label = { Text("Страна") }, singleLine = true, modifier = Modifier.width(150.dp))
                OutlinedTextField(value = filters.registeredFrom, onValueChange = { viewModel.setFilters(filters.copy(registeredFrom = it)) }, label = { Text("Рег. от") }, placeholder = { Text("2026-01-01") }, singleLine = true, modifier = Modifier.width(150.dp))
                OutlinedTextField(value = filters.registeredTo, onValueChange = { viewModel.setFilters(filters.copy(registeredTo = it)) }, label = { Text("Рег. до") }, placeholder = { Text("2026-12-31") }, singleLine = true, modifier = Modifier.width(150.dp))
            }

            Text("Онлайн = активность за последние 5 минут • Трудный = 3+ жалобы или 3+ блокировки", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            val visibleUsers = viewModel.searchUsers(search)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = visibleUsers.isNotEmpty() && selected.size == visibleUsers.size,
                    onCheckedChange = { viewModel.selectAllUsers(visibleUsers.map { it.uid }) }
                )
                Text("Выбрано: ${selected.size}", modifier = Modifier.weight(1f))
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { blockDialog = true }) {
                        Text("Заблокировать", color = YodoError)
                    }
                    TextButton(onClick = { viewModel.unblockSelected() }) {
                        Text("Разблокировать")
                    }
                    TextButton(onClick = { classValue = ""; classDialog = true }) {
                        Text("Изменить класс")
                    }
                    if (selected.size == 1 && viewModel.isAppAdmin) {
                        TextButton(onClick = { adminDialog = true }) {
                            Text("Назначить админом")
                        }
                    }
                }
            }

            when (val state = uiState) {
                AdminUsersUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is AdminUsersUiState.Results -> {
                    if (visibleUsers.isEmpty()) {
                        Text("Пользователи не найдены", Modifier.padding(16.dp))
                    } else {
                        LazyColumn {
                            items(visibleUsers, key = { it.uid }) { user ->
                                AdminUserRow(
                                    user = user,
                                    status = viewModel.statusOf(user),
                                    checked = user.uid in selected,
                                    online = viewModel.isOnline(user),
                                    moderation = viewModel.moderationStatsOf(user.uid),
                                    onChecked = { viewModel.toggleSelected(user.uid) },
                                    onOpen = { viewModel.openDetails(user.uid) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (blockDialog) {
        AlertDialog(
            onDismissRequest = { blockDialog = false },
            title = { Text("Групповая блокировка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Будет заблокировано: ${selected.size}")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Причина блокировки") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.blockSelected(reason, description)
                    blockDialog = false
                    reason = ""; description = ""
                }) { Text("Заблокировать", color = YodoError) }
            },
            dismissButton = { TextButton(onClick = { blockDialog = false }) { Text("Отмена") } }
        )
    }


    if (classDialog) {
        AlertDialog(
            onDismissRequest = { classDialog = false },
            title = { Text("Изменить класс") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пользователей выбрано: ${selected.size}")
                    OutlinedTextField(value = classValue, onValueChange = { classValue = it }, label = { Text("Класс") }, placeholder = { Text("Например, 9А") }, singleLine = true)
                    Text("Оставьте поле пустым, чтобы убрать класс.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.updateSelectedClass(classValue); classDialog = false }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { classDialog = false }) { Text("Отмена") } }
        )
    }


    if (adminDialog) {
        val uid = selected.singleOrNull() ?: details?.user?.uid
        if (uid != null) {
            AlertDialog(
                onDismissRequest = { adminDialog = false },
                title = { Text("Права администратора") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Пользователь: $uid", style = MaterialTheme.typography.bodySmall)
                        AdminRole.entries.forEach { role ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = adminRole == role, onClick = { adminRole = role })
                                Text(role.label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.assignAdmin(uid, adminRole)
                        adminDialog = false
                    }) { Text("Назначить") }
                },
                dismissButton = { TextButton(onClick = { adminDialog = false }) { Text("Отмена") } }
            )
        }
    }

    details?.let {
        AdminUserDetailsDialog(
            details = it,
            onOpenProfile = onOpenUserProfile,
            onClose = viewModel::closeDetails,
            onAssignAdmin = { adminRole = it; adminDialog = true },
            onRemoveAdmin = { viewModel.removeAdmin(it.user.uid) }
        )
    }
}

@Composable
private fun AdminUserRow(
    user: YodoUser,
    status: AdminUserStatus,
    checked: Boolean,
    online: Boolean,
    moderation: AdminUserModerationStats,
    onChecked: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onChecked() })
        UserAvatar(
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            avatarBase64 = user.avatarBase64,
            size = 44.dp,
            userId = user.uid
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "@${user.username ?: "без username"} • ${status.label} • ${if (online) "онлайн" else "оффлайн"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (status == AdminUserStatus.BLOCKED) YodoError else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Класс: ${user.classId ?: "—"} • Жалобы: ${moderation.reports} • Блокировки: ${moderation.blocks}",
                style = MaterialTheme.typography.labelSmall,
                color = if (moderation.difficult) YodoError else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Регистрация: ${adminDate(user.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun AdminUserDetailsDialog(
    details: AdminUserDetails,
    onOpenProfile: (String) -> Unit,
    onClose: () -> Unit,
    onAssignAdmin: (AdminRole) -> Unit,
    onRemoveAdmin: () -> Unit
) {
    val u = details.user
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Карточка пользователя") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(u.displayName, style = MaterialTheme.typography.titleLarge)
                    Text("@${u.username ?: "—"}")
                    Text("UID: ${u.uid}", style = MaterialTheme.typography.bodySmall)
                    Text("Страна: ${u.country ?: "не указана"}")
                    Text("Регистрация: ${adminDate(u.createdAt)}")
                    Text("Последняя активность: ${adminDate(u.lastActiveAt)}")
                }
                item { HorizontalDivider() }
                item {
                    Text("Статистика", style = MaterialTheme.typography.titleMedium)
                    Text("Отправлено сообщений: ${details.messagesSent}")
                    Text("Получено жалоб: ${details.reportsReceived}")
                    Text("Класс: ${u.classId ?: "—"}")
                    Text("Статус: ${if (details.user.lastActiveAt > 0 && System.currentTimeMillis() - details.user.lastActiveAt <= 5L * 60L * 1000L) "онлайн" else "оффлайн"}")
                }

                item { TimelineSection("История действий", details.actionHistory) }
                item { TimelineSection("История жалоб", details.reportHistory) }
                item { TimelineSection("История сообщений", details.messageHistory) }
                item { TimelineSection("История обращений", details.appealHistory) }

                item {
                    Text("Активность за последний месяц", style = MaterialTheme.typography.titleMedium)
                    AdminActivityChart(details.activity)
                }
                item {
                    if (details.adminAssignment != null) {
                        Text("Администратор: ${details.adminAssignment.role.label}")
                        TextButton(onClick = onRemoveAdmin) { Text("Снять администратора", color = YodoError) }
                    } else {
                        TextButton(onClick = { onAssignAdmin(AdminRole.ADMIN) }) { Text("Назначить администратором") }
                    }
                }
                item {
                    Text("Последние 5 входов", style = MaterialTheme.typography.titleMedium)
                    if (details.loginHistory.isEmpty()) Text("История пока отсутствует")
                    details.loginHistory.forEach {
                        Text("${adminDate(it.timestamp)} • ${it.provider} • ${it.device}")
                        Text("IP: ${it.ipAddress ?: "не записан"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Text("IP-адреса", style = MaterialTheme.typography.titleMedium)
                    Text(if (details.ipAddresses.isEmpty()) "IP не записываются клиентом" else details.ipAddresses.joinToString("\n"))
                }
                item {
                    Text("Группы и каналы", style = MaterialTheme.typography.titleMedium)
                    if (details.groupsAndChannels.isEmpty()) Text("Нет групп/каналов")
                    details.groupsAndChannels.forEach {
                        Text("• ${it.title} — ${if (it.type == "CHANNEL") "Канал" else "Группа"} (${it.role})")
                    }
                }
                item {
                    Text("История блокировок", style = MaterialTheme.typography.titleMedium)
                    if (details.blockHistory.isEmpty()) Text("Блокировок не было")
                    details.blockHistory.forEach {
                        Text("${adminDate(it.timestamp)} • ${if (it.action == "BLOCK") "Блокировка" else "Разблокировка"}")
                        if (it.reason.isNotBlank()) Text("Причина: ${it.reason}")
                        if (it.description.isNotBlank()) Text(it.description)
                        Text("Администратор: ${it.byName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onClose(); onOpenProfile(u.uid) }) { Text("Профиль") }
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
        }
    )
}


@Composable
private fun TimelineSection(title: String, items: List<AdminTimelineEntry>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (items.isEmpty()) Text("История пока отсутствует", style = MaterialTheme.typography.bodySmall)
    items.take(30).forEach { entry ->
        Text("${adminDate(entry.timestamp)} • ${entry.title}", style = MaterialTheme.typography.bodySmall)
        if (entry.status.isNotBlank()) Text("Статус: ${entry.status}", style = MaterialTheme.typography.labelSmall)
        if (entry.details.isNotBlank()) Text(entry.details, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (entry.actorName.isNotBlank()) Text("Кто: ${entry.actorName}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AdminActivityChart(points: List<app.yodo.messenger.domain.model.AdminActivityPoint>) {
    if (points.isEmpty()) {
        Text("Данных активности пока нет", style = MaterialTheme.typography.bodySmall)
        return
    }
    val maxValue = maxOf(1, points.maxOf { it.total })
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 4.dp)) {
        val left = 8f
        val right = size.width - 8f
        val top = 10f
        val bottom = size.height - 10f
        val stepX = if (points.size == 1) 0f else (right - left) / (points.size - 1)
        val coords = points.mapIndexed { i, p ->
            val x = left + i * stepX
            val y = bottom - (p.total.toFloat() / maxValue) * (bottom - top)
            Offset(x, y)
        }
        for (i in 0 until coords.lastIndex) drawLine(coords[i], coords[i + 1], strokeWidth = 4f)
        coords.forEach { drawCircle(it, radius = 3.5f) }
    }
    Text(
        "Точки показывают входы/активные сессии по дням. Максимум: $maxValue",
        style = MaterialTheme.typography.labelSmall
    )
}
