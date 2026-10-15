package app.yodo.messenger.features.chats

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import app.yodo.messenger.domain.model.BuiltInRole
import app.yodo.messenger.domain.model.CustomRole
import app.yodo.messenger.domain.model.Permission
import app.yodo.messenger.ui.components.UserAvatar

@Composable
fun ManageRolesScreen(
    onBackClick: () -> Unit,
    onOpenAdminLog: () -> Unit,
    onOpenReportQueue: () -> Unit = {},
    viewModel: ManageRolesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var memberForRolePicker by remember { mutableStateOf<RoleMemberRow?>(null) }
    var showCreateRoleDialog by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<CustomRole?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Роли и права", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenReportQueue) {
                        Icon(Icons.Filled.Flag, contentDescription = "Жалобы")
                    }
                    IconButton(onClick = onOpenAdminLog) {
                        Icon(Icons.Filled.History, contentDescription = "Журнал действий")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            item {
                SectionLabel("Участники")
            }
            items(uiState.members, key = { it.user.uid }) { row ->
                MemberRoleRow(
                    row = row,
                    canManage = uiState.canManage,
                    onClick = { if (uiState.canManage && !row.isOwner) memberForRolePicker = row }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("Кастомные роли")
            }
            items(uiState.customRoles, key = { it.id }) { role ->
                CustomRoleRow(
                    role = role,
                    canManage = uiState.canManage,
                    onEdit = { editingRole = role },
                    onDelete = { viewModel.deleteCustomRole(role.id) }
                )
            }
            if (uiState.canManage) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateRoleDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Создать роль", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Диалог выбора роли для участника
    memberForRolePicker?.let { row ->
        RolePickerDialog(
            member = row,
            customRoles = uiState.customRoles,
            onDismiss = { memberForRolePicker = null },
            onSelectBuiltIn = { role ->
                viewModel.assignBuiltInRole(row.user.uid, role)
                memberForRolePicker = null
            },
            onSelectCustom = { customRole ->
                viewModel.assignCustomRole(row.user.uid, customRole.id)
                memberForRolePicker = null
            },
            onRevoke = {
                viewModel.revokeRole(row.user.uid)
                memberForRolePicker = null
            }
        )
    }

    if (showCreateRoleDialog) {
        RoleEditorDialog(
            initialName = "",
            initialPermissions = emptySet(),
            onDismiss = { showCreateRoleDialog = false },
            onSave = { name, permissions ->
                viewModel.createCustomRole(name, permissions)
                showCreateRoleDialog = false
            }
        )
    }

    editingRole?.let { role ->
        RoleEditorDialog(
            initialName = role.name,
            initialPermissions = role.permissions,
            onDismiss = { editingRole = null },
            onSave = { name, permissions ->
                viewModel.updateCustomRole(role.id, name, permissions)
                editingRole = null
            }
        )
    }

    errorMessage?.let {
        AlertDialog(
            onDismissRequest = { viewModel.consumeErrorMessage() },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeErrorMessage() }) { Text("Ок") }
            },
            title = { Text("Ошибка") },
            text = { Text(it) }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun MemberRoleRow(row: RoleMemberRow, canManage: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canManage && !row.isOwner) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = row.user.displayName,
            photoUrl = row.user.photoUrl,
            avatarBase64 = row.user.avatarBase64,
            userId = row.user.uid,
            size = 44.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.user.displayName, style = MaterialTheme.typography.bodyLarge)
        }
        RoleBadge(text = row.roleLabel, highlighted = row.isOwner || row.assignedRole != null)
    }
}

@Composable
private fun RoleBadge(text: String, highlighted: Boolean) {
    val bg = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CustomRoleRow(role: CustomRole, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canManage) { onEdit() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(role.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${role.permissions.size} прав из ${Permission.entries.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canManage) {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Close, contentDescription = "Удалить роль")
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить роль «${role.name}»?") },
            text = { Text("Участники с этой ролью потеряют связанные права.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun RolePickerDialog(
    member: RoleMemberRow,
    customRoles: List<CustomRole>,
    onDismiss: () -> Unit,
    onSelectBuiltIn: (BuiltInRole) -> Unit,
    onSelectCustom: (CustomRole) -> Unit,
    onRevoke: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Роль для ${member.user.displayName}") },
        text = {
            Column {
                listOf(BuiltInRole.MODERATOR, BuiltInRole.ASSISTANT, BuiltInRole.CONTENT_EDITOR).forEach { role ->
                    Text(
                        text = role.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBuiltIn(role) }
                            .padding(vertical = 12.dp)
                    )
                }
                customRoles.forEach { role ->
                    Text(
                        text = role.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCustom(role) }
                            .padding(vertical = 12.dp)
                    )
                }
                if (member.assignedRole != null) {
                    Text(
                        text = "Снять роль",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRevoke() }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun RoleEditorDialog(
    initialName: String,
    initialPermissions: Set<Permission>,
    onDismiss: () -> Unit,
    onSave: (String, Set<Permission>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var permissions by remember { mutableStateOf(initialPermissions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "Новая роль" else "Изменить роль") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название роли") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Permission.entries.forEach { permission ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                permissions = if (permission in permissions) permissions - permission else permissions + permission
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = permission in permissions,
                            onCheckedChange = { checked ->
                                permissions = if (checked) permissions + permission else permissions - permission
                            }
                        )
                        Text(permission.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name, permissions) },
                enabled = name.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
