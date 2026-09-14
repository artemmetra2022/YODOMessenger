package app.yodo.messenger.features.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.LocalColorTheme

// НОВОЕ (архивация чатов): отдельный экран со списком архивных чатов.
// Переиспользует ChatListViewModel — тот же поток данных, что и основной список,
// просто показывает archivedChats вместо chats.
@Composable
fun ArchivedChatsScreen(
    onChatClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                }
                Text(
                    text = stringResource(R.string.archived_chats_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is ChatListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ChatListUiState.Content -> {
                    if (state.archivedChats.isEmpty()) {
                        Text(
                            text = stringResource(R.string.archived_chats_empty),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.archivedChats, key = { it.chatId }) { chat ->
                                SwipeableChatListItem(
                                    chat = chat,
                                    colorTheme = colorTheme,
                                    onClick = { onChatClick(chat.chatId) },
                                    onTogglePin = { viewModel.togglePinChat(chat.chatId) },
                                    onToggleMute = { viewModel.toggleMuteChat(chat.chatId) },
                                    onDelete = { viewModel.deleteChat(chat.chatId) },
                                    onClearHistory = { viewModel.clearChatHistory(chat.chatId) },
                                    onToggleArchive = { viewModel.toggleArchiveChat(chat.chatId) }
                                )
                            }
                        }
                    }
                }
                is ChatListUiState.Empty -> {
                    Text(
                        text = "В архиве пока пусто",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is ChatListUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.archived_chats_load_error), style = MaterialTheme.typography.titleLarge)
                        Text(state.message, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
