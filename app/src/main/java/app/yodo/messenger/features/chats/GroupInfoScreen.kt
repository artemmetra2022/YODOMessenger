package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.JoinRequest
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.R
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun GroupInfoScreen(
    onBackClick: () -> Unit,
    onLeftGroup: () -> Unit,
    onOpenManageRoles: (String) -> Unit = {},
    onOpenForumTopics: (String) -> Unit = {},
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val adminState by viewModel.adminState.collectAsState()
    val didLeave by viewModel.didLeave.collectAsState()

    // НОВОЕ (админ-функции групп): участник, над которым открыт диалог действий.
    var memberActions by remember { mutableStateOf<YodoUser?>(null) }
    // НОВОЕ (админ-функции групп): участник, которого подтверждаем к исключению/бану.
    var confirmMemberAction by remember { mutableStateOf<Pair<YodoUser, String>?>(null) }
    // НОВОЕ: подтверждение выхода из группы — раньше кнопка срабатывала в один тап,
    // случайное касание молча выкидывало пользователя из чата.
    var showLeaveConfirm by remember { mutableStateOf(false) }
    // НОВОЕ (передача владения): участник, которому владелец хочет передать права.
    var confirmTransferTo by remember { mutableStateOf<YodoUser?>(null) }

    LaunchedEffect(didLeave) {
        if (didLeave) onLeftGroup()
    }

    // НОВОЕ (админ-функции групп): ошибки действий (отказ правил и т.п.) показываем снекбаром.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(adminState.errorMessage) {
        adminState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_info_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is GroupInfoUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
            is GroupInfoUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        text = stringResource(R.string.group_info_not_found),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            is GroupInfoUiState.Content -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UserAvatar(
                            displayName = state.info.title,
                            photoUrl = null,
                            avatarBase64 = null,
                            size = 96.dp
                        )
                        Text(
                            text = state.info.title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            text = stringResource(R.string.group_info_members, state.info.members.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (state.info.description.isNotBlank()) {
                            Text(
                                text = state.info.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // НОВОЕ (конфиденциальность групп): показываем текущий режим доступа.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (state.info.accessMode) {
                                app.yodo.messenger.domain.model.ChannelAccessMode.OPEN -> Icons.Filled.Public
                                app.yodo.messenger.domain.model.ChannelAccessMode.MODERATED -> Icons.Filled.HowToReg
                                app.yodo.messenger.domain.model.ChannelAccessMode.HIDDEN -> Icons.Filled.Lock
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                state.info.accessMode.title,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                state.info.accessMode.description,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (viewModel.myUid != null && viewModel.myUid == state.info.createdBy) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenManageRoles(viewModel.chatId) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.group_info_manage_roles),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // НОВОЕ (форумные группы): пункт-переход к разделам форума, если группа — форум.
                    if (state.info.isForum) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenForumTopics(viewModel.chatId) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.group_info_forum_topics),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        // НОВОЕ (админ-функции групп): заявки на вступление — только владелец/админ.
                        if (adminState.canManageMembers && adminState.joinRequests.isNotEmpty()) {
                            item {
                                Text(
                                    "Заявки на вступление (${adminState.joinRequests.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(adminState.joinRequests, key = { "req_${it.userId}" }) { request ->
                                JoinRequestRow(
                                    request = request,
                                    onApprove = { viewModel.approveRequest(request.userId) },
                                    onReject = { viewModel.rejectRequest(request.userId) }
                                )
                            }
                        }

                        items(state.info.members, key = { it.uid }) { member ->
                            // НОВОЕ (админ-функции групп): с кем можно совершать действия —
                            // не сам текущий пользователь, не владелец, и текущий управляет составом.
                            val canActOnMember = adminState.canManageMembers &&
                                    member.uid != viewModel.myUid &&
                                    member.uid != state.info.createdBy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (canActOnMember)
                                            Modifier.clickable { memberActions = member }
                                        else Modifier
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    displayName = member.displayName,
                                    photoUrl = member.photoUrl,
                                    avatarBase64 = member.avatarBase64,
                                    size = 44.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        member.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (member.uid == state.info.createdBy) {
                                        Text(stringResource(R.string.group_info_creator), style = MaterialTheme.typography.labelMedium)
                                    } else {
                                        // НОВОЕ (бейджи ролей): подпись роли под именем участника.
                                        adminState.memberRoles[member.uid]?.let { role ->
                                            Text(
                                                role,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                // Подсказка, что по строке есть действия (иконка справа).
                                if (canActOnMember) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Действия",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // НОВОЕ (админ-функции групп): забаненные участники — только владелец/админ.
                        if (adminState.canManageMembers && adminState.bannedMembers.isNotEmpty()) {
                            item {
                                Text(
                                    "Забаненные",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(adminState.bannedMembers, key = { "ban_${it.uid}" }) { member ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        displayName = member.displayName,
                                        photoUrl = member.photoUrl,
                                        avatarBase64 = member.avatarBase64,
                                        size = 44.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            member.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = { viewModel.unbanMember(member.uid) }) {
                                        Text("Разбанить", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // ИЗМЕНЕНО: владелец не может просто выйти — сначала обязан передать
                    // права другому участнику (это же проверяется на сервере в правилах).
                    val isOwner = state.info.createdBy == viewModel.myUid
                    Button(
                        onClick = { showLeaveConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = YodoError),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(stringResource(R.string.group_info_leave), color = Color.White)
                    }
                    if (isOwner) {
                        Text(
                            "Вы владелец группы — чтобы выйти, сначала назначьте владельцем другого участника (значок ⋮ у его имени).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // НОВОЕ: подтверждение выхода из группы.
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Выйти из группы?") },
            text = { Text("Вы покинете «${(uiState as? GroupInfoUiState.Content)?.info?.title ?: "группу"}». Вернуться можно будет по заявке или новому приглашению.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    viewModel.leaveGroup()
                }) { Text("Выйти", color = YodoError) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Отмена") }
            }
        )
    }

    // НОВОЕ (админ-функции групп): диалог действий над участником — вертикальный
    // список пунктов (две длинные надписи в одной строке confirm/dismiss сжимались
    // и налезали друг на друга на узких экранах).
    memberActions?.let { member ->
        AlertDialog(
            onDismissRequest = { memberActions = null },
            title = {
                Text(
                    member.displayName,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MemberActionRow(
                        icon = Icons.Filled.PersonRemove,
                        label = "Исключить из группы",
                        onClick = { memberActions = null; confirmMemberAction = member to "kick" }
                    )
                    MemberActionRow(
                        icon = Icons.Filled.Block,
                        label = "Забанить",
                        onClick = { memberActions = null; confirmMemberAction = member to "ban" }
                    )
                    // НОВОЕ (передача владения): доступно только текущему владельцу группы,
                    // не обычному админу.
                    if ((uiState as? GroupInfoUiState.Content)?.info?.createdBy == viewModel.myUid) {
                        MemberActionRow(
                            icon = Icons.Filled.Shield,
                            label = "Назначить владельцем",
                            onClick = { memberActions = null; confirmTransferTo = member }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { memberActions = null }) { Text("Отмена") }
            }
        )
    }

    // НОВОЕ (админ-функции групп): подтверждение исключения/бана.
    confirmMemberAction?.let { (member, action) ->
        val isKick = action == "kick"
        AlertDialog(
            onDismissRequest = { confirmMemberAction = null },
            title = { Text(if (isKick) "Исключить участника?" else "Забанить участника?") },
            text = { Text(
                if (isKick)
                    "${member.displayName} будет исключён из группы. Он сможет подать заявку на вступление заново."
                else
                    "${member.displayName} будет забанен и не сможет вернуться в группу. Разбан возможен только вручную."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    confirmMemberAction = null
                    if (isKick) viewModel.kickMember(member.uid) else viewModel.banMember(member.uid)
                }) { Text(if (isKick) "Исключить" else "Забанить", color = YodoError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmMemberAction = null }) { Text("Отмена") }
            }
        )
    }

    // НОВОЕ (передача владения): подтверждение — действие необратимо без
    // участия нового владельца (только он сможет передать права обратно).
    confirmTransferTo?.let { member ->
        AlertDialog(
            onDismissRequest = { confirmTransferTo = null },
            title = { Text("Назначить владельцем?") },
            text = {
                Text(
                    "${member.displayName} станет владельцем группы. Вы останетесь администратором, " +
                        "но потеряете возможность передать права обратно себе — это сможет сделать только новый владелец."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmTransferTo = null
                    viewModel.transferOwnership(member.uid)
                }) { Text("Назначить", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTransferTo = null } ) { Text("Отмена") }
            }
        )
    }
}

/** НОВОЕ (админ-функции групп): пункт списка действий над участником — на всю
 *  ширину, с иконкой; не сжимается в отличие от двух кнопок в одной строке. */
@Composable
private fun MemberActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = YodoError, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = YodoError)
    }
}

/** Строка заявки на вступление: имя + кнопки «Одобрить»/«Отклонить». */
@Composable
private fun JoinRequestRow(
    request: JoinRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = request.displayName,
            photoUrl = request.photoUrl,
            avatarBase64 = request.avatarBase64,
            size = 40.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                request.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            request.username?.let {
                Text(
                    "@$it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // Компактный внутренний отступ — иначе длинные имена вытесняли кнопки.
        TextButton(
            onClick = onApprove,
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("Одобрить", color = MaterialTheme.colorScheme.primary)
        }
        TextButton(
            onClick = onReject,
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("Отклонить", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
