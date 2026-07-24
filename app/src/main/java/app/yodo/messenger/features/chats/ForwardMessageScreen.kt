package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ForwardMessageScreen(
    onBackClick: () -> Unit,
    onForwarded: (String) -> Unit,
    viewModel: ForwardMessageViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val forwardedToChatId by viewModel.forwardedToChatId.collectAsState()

    LaunchedEffect(forwardedToChatId) {
        forwardedToChatId?.let { onForwarded(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Переслать в...", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(chats, key = { it.chatId }) { chat ->
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.forwardTo(chat.chatId) }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }
    }
}
