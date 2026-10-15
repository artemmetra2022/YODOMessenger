package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.YodoError

/**
 * НОВОЕ (единая вкладка «Админка»): поиск пользователя по имени/username и его
 * глобальная блокировка прямо из результатов. Контекстная блокировка с профиля
 * конкретного человека (UserProfileScreen) остаётся как есть — это параллельный,
 * более быстрый путь для админа, который ищет по имени, а не переходит по чату.
 */
@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val blockedStatus by viewModel.blockedStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var confirmBlock by remember { mutableStateOf<YodoUser?>(null) }
    var blockReason by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Пользователи") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.onQueryChanged(it) },
                label = { Text("Имя или username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            when (val state = uiState) {
                is AdminUsersUiState.Idle -> {
                    Text(
                        "Введите имя, чтобы найти пользователя",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is AdminUsersUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AdminUsersUiState.NoResults -> {
                    Text(
                        "Никого не найдено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is AdminUsersUiState.Results -> {
                    LazyColumn {
                        items(state.users, key = { it.uid }) { user ->
                            val isBlocked = blockedStatus[user.uid] == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenUserProfile(user.uid) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    displayName = user.displayName,
                                    photoUrl = user.photoUrl,
                                    avatarBase64 = user.avatarBase64,
                                    size = 44.dp,
                                    userId = user.uid
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        user.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    user.username?.let {
                                        Text(
                                            "@$it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isBlocked) {
                                        Text(
                                            "Заблокирован глобально",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = YodoError
                                        )
                                    }
                                }
                                if (isBlocked) {
                                    TextButton(onClick = { viewModel.unblockUser(user.uid) }) {
                                        Text("Разблокировать")
                                    }
                                } else {
                                    TextButton(onClick = { confirmBlock = user; blockReason = "" }) {
                                        Text("Заблокировать", color = YodoError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmBlock?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmBlock = null },
            title = { Text("Заблокировать ${user.displayName}?") },
            text = {
                Column {
                    Text("Пользователь глобально потеряет доступ к приложению. Укажите причину:")
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = blockReason,
                        onValueChange = { blockReason = it },
                        label = { Text("Причина") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = confirmBlock
                    confirmBlock = null
                    if (target != null) viewModel.blockUser(target.uid, blockReason)
                }) { Text("Заблокировать", color = YodoError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = null }) { Text("Отмена") }
            }
        )
    }
}
