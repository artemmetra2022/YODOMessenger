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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.theme.YodoPrimary

@Composable
fun CreateGroupScreen(
    onBackClick: () -> Unit,
    onGroupCreated: (String) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val createdChatId by viewModel.createdChatId.collectAsState()

    LaunchedEffect(createdChatId) {
        createdChatId?.let {
            onGroupCreated(it)
            viewModel.consumeCreatedChatId()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая группа", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Название группы") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true
            )

            if (uiState.selectedUsers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Выбрано: ${uiState.selectedUsers.size}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    items(uiState.selectedUsers, key = { "selected_${it.uid}" }) { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.displayName, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeSelected(user) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Убрать")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChanged(it)
                },
                label = { Text("Добавить участников (имя или @username)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true
            )

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.searchResults, key = { it.uid }) { user ->
                            SelectableUserRow(user = user, onClick = { viewModel.toggleUser(user) })
                        }
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            Button(
                onClick = { viewModel.createGroup(groupName) },
                enabled = !uiState.isCreating && uiState.selectedUsers.size >= 2 && groupName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Создать группу")
                }
            }
        }
    }
}

@Composable
private fun SelectableUserRow(user: YodoUser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(YodoPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.displayName.take(1).uppercase(),
                color = YodoPrimary,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.displayName, style = MaterialTheme.typography.bodyLarge)
            user.username?.let { Text(text = "@$it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
