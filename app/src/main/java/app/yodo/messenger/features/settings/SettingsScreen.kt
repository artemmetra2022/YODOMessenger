package app.yodo.messenger.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLoggedOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsState()
    val showReadReceipts by viewModel.showReadReceipts.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val notificationVibration by viewModel.notificationVibration.collectAsState()
    val muteAllNotifications by viewModel.muteAllNotifications.collectAsState()
    val accountDeleted by viewModel.accountDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(accountDeleted) {
        if (accountDeleted) onLoggedOut()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Это действие необратимо. Твой аккаунт для входа будет удалён. История переписки у собеседников сохранится (как в большинстве мессенджеров).") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountDialog = false
                    viewModel.deleteAccount()
                }) { Text("Удалить", color = YodoError) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            item { SectionTitle("Оформление") }
            item {
                SettingsSwitchRow(
                    title = "Тёмная тема",
                    subtitle = "Оформление интерфейса Yodo Messenger",
                    checked = isDarkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) }
                )
            }
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Размер шрифта", style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        FontSize.entries.forEach { size ->
                            FilterChip(
                                selected = fontSize == size,
                                onClick = { viewModel.setFontSize(size) },
                                label = {
                                    Text(
                                        when (size) {
                                            FontSize.SMALL -> "Мелкий"
                                            FontSize.MEDIUM -> "Обычный"
                                            FontSize.LARGE -> "Крупный"
                                        }
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider() }

            item { SectionTitle("Чаты") }
            item {
                SettingsSwitchRow(
                    title = "Отправка по Enter",
                    subtitle = "Клавиша Enter отправляет сообщение вместо переноса строки",
                    checked = sendOnEnter,
                    onCheckedChange = { viewModel.setSendOnEnter(it) }
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Автозагрузка фото",
                    subtitle = "Показывать изображения сразу, а не только по тапу (экономия трафика)",
                    checked = autoDownloadImages,
                    onCheckedChange = { viewModel.setAutoDownloadImages(it) }
                )
            }
            item {
                TextButton(
                    onClick = { viewModel.clearAllDrafts() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Очистить все черновики") }
            }
            item { HorizontalDivider() }

            item { SectionTitle("Конфиденциальность") }
            item {
                SettingsSwitchRow(
                    title = "Показывать статус \"в сети\"",
                    subtitle = "Другие видят, когда ты онлайн и был(а) недавно",
                    checked = showOnlineStatus,
                    onCheckedChange = { viewModel.setShowOnlineStatus(it) }
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Показывать статус прочтения",
                    subtitle = "Собеседник видит двойную галочку, когда ты прочитал(а) сообщение",
                    checked = showReadReceipts,
                    onCheckedChange = { viewModel.setShowReadReceipts(it) }
                )
            }
            item { HorizontalDivider() }

            item { SectionTitle("Уведомления") }
            item {
                SettingsSwitchRow(
                    title = "Отключить все уведомления",
                    subtitle = "Полностью выключить push-уведомления",
                    checked = muteAllNotifications,
                    onCheckedChange = { viewModel.setMuteAllNotifications(it) }
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Звук",
                    subtitle = "Звуковой сигнал при новом сообщении",
                    checked = notificationSound,
                    onCheckedChange = { viewModel.setNotificationSound(it) },
                    enabled = !muteAllNotifications
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Вибрация",
                    subtitle = "Вибросигнал при новом сообщении",
                    checked = notificationVibration,
                    onCheckedChange = { viewModel.setNotificationVibration(it) },
                    enabled = !muteAllNotifications
                )
            }
            item { HorizontalDivider() }

            item { SectionTitle("Аккаунт") }
            item {
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = YodoError),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Выйти из аккаунта", color = Color.White)
                }
            }
            item {
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp)
                ) {
                    Text("Удалить аккаунт", color = YodoError)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
