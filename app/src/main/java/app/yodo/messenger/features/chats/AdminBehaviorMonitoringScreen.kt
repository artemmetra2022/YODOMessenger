package app.yodo.messenger.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AdminBehaviorMonitoringScreen(
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    viewModel: AdminBehaviorMonitoringViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Мониторинг поведения") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                }
            )
        }
    ) { padding ->
        if (loading && state == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.padding(32.dp))
                CircularProgressIndicator()
            }
        } else {
            val snapshot = state ?: return@Scaffold
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Автоматическая пауза для новичков", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Профили младше 3 дней: максимум 5 сообщений в минуту, затем пауза на 60 секунд.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = snapshot.newcomerPauseEnabled,
                                onCheckedChange = viewModel::setNewcomerPause
                            )
                        }
                    }
                }

                item {
                    Text("Подозрительная активность", style = MaterialTheme.typography.titleLarge)
                }
                if (snapshot.alerts.isEmpty()) {
                    item { Text("Подозрительной активности не обнаружено.") }
                } else {
                    items(snapshot.alerts, key = { "${it.userId}_${it.type}" }) { alert ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenUserProfile(alert.userId) }
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(alert.title, style = MaterialTheme.typography.titleMedium)
                                Text(alert.userName)
                                Text(alert.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        "Список «трудных» пользователей",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Пользователи с 3+ жалобами или 3+ блокировками.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (snapshot.difficultUsers.isEmpty()) {
                    item { Text("Таких пользователей пока нет.") }
                } else {
                    items(snapshot.difficultUsers, key = { it.user.uid }) { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onOpenUserProfile(item.user.uid) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.user.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("@${item.user.username ?: "без username"}")
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Жалобы: ${item.reports}")
                                Text("Блокировки: ${item.blocks}")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
