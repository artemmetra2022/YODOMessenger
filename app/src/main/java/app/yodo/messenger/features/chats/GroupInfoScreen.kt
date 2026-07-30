package app.yodo.messenger.features.chats

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun GroupInfoScreen(
    onBackClick: () -> Unit,
    onLeftGroup: () -> Unit,
    viewModel: GroupInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val didLeave by viewModel.didLeave.collectAsState()

    LaunchedEffect(didLeave) {
        if (didLeave) onLeftGroup()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация о группе", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                        text = "Группа не найдена",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            is GroupInfoUiState.Content -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        text = state.info.title,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = "Участники (${state.info.members.size})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.info.members, key = { it.uid }) { member ->
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
                                Column {
                                    Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
                                    if (member.uid == state.info.createdBy) {
                                        Text("Создатель группы", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.leaveGroup() },
                        colors = ButtonDefaults.buttonColors(containerColor = YodoError),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text("Покинуть группу", color = Color.White)
                    }
                }
            }
        }
    }
}
