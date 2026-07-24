package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.ui.theme.allColorThemes
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    onOpenBlockedUsers: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val colorThemeName by viewModel.colorThemeName.collectAsState()
    val sendOnEnter by viewModel.sendOnEnter.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsState()
    val showReadReceipts by viewModel.showReadReceipts.collectAsState()
    val showBirthDate by viewModel.showBirthDate.collectAsState()
    val showAboutMe by viewModel.showAboutMe.collectAsState()
    val showLocation by viewModel.showLocation.collectAsState()
    val showWebsite by viewModel.showWebsite.collectAsState()
    val showPhoneNumber by viewModel.showPhoneNumber.collectAsState()
    val showEmail by viewModel.showEmail.collectAsState()
    val autoDownloadImages by viewModel.autoDownloadImages.collectAsState()
    val hideKeyboardOnSend by viewModel.hideKeyboardOnSend.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val notificationVibration by viewModel.notificationVibration.collectAsState()
    val muteAllNotifications by viewModel.muteAllNotifications.collectAsState()
    val accountDeleted by viewModel.accountDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(accountDeleted) { if (accountDeleted) onLoggedOut() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeError() }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Это действие необратимо.") },
            confirmButton = {
                TextButton(onClick = { showDeleteAccountDialog = false; viewModel.deleteAccount() }) {
                    Text("Удалить", color = YodoError)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Отмена") } }
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
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = "Профиль")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item { SectionTitle("Оформление") }
            item { SettingsSwitchRow(
                    title = "Тёмная тема",
                    subtitle = "Оформление интерфейса",
                    checked = isDarkTheme,
                    onCheckedChange = { v -> viewModel.setDarkTheme(v) }
                ) }
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Цветовая тема", style = MaterialTheme.typography.bodyLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        allColorThemes.forEach { theme ->
                            val isSelected = colorThemeName == theme.name.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.setColorTheme(theme.name.name) }
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(theme.primary)
                                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                )
                                Text(theme.name.displayName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
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
                                    Text(when (size) {
                                        FontSize.SMALL -> "Мелкий"
                                        FontSize.MEDIUM -> "Обычный"
                                        FontSize.LARGE -> "Крупный"
                                    })
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider() }
            item { SectionTitle("Чаты") }
            item { SettingsSwitchRow(
                    title = "Отправка по Enter",
                    subtitle = "Enter отправляет сообщение",
                    checked = sendOnEnter,
                    onCheckedChange = { v -> viewModel.setSendOnEnter(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Скрывать клавиатуру после отправки",
                    subtitle = "Клавиатура закрывается автоматически",
                    checked = hideKeyboardOnSend,
                    onCheckedChange = { v -> viewModel.setHideKeyboardOnSend(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Автозагрузка фото",
                    subtitle = "Показывать изображения сразу",
                    checked = autoDownloadImages,
                    onCheckedChange = { v -> viewModel.setAutoDownloadImages(v) }
                ) }
            item { HorizontalDivider() }
            item { SectionTitle("Конфиденциальность") }
            item { SettingsSwitchRow(
                    title = "Показывать статус «в сети»",
                    subtitle = "Другие видят, когда ты онлайн. Работает в обе стороны: если ты скроешь свой статус, ты также перестанешь видеть статус «в сети» у других",
                    checked = showOnlineStatus,
                    onCheckedChange = { v -> viewModel.setShowOnlineStatus(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Показывать статус прочтения",
                    subtitle = "Собеседник видит двойную галочку",
                    checked = showReadReceipts,
                    onCheckedChange = { v -> viewModel.setShowReadReceipts(v) }
                ) }
            item {
                Text(
                    "Расширенный профиль — что видят другие",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            item { SettingsSwitchRow(
                    title = "Заметки «О себе»",
                    subtitle = "Показывать текст в расширенном профиле",
                    checked = showAboutMe,
                    onCheckedChange = { v -> viewModel.setShowAboutMe(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Дата рождения",
                    subtitle = "Показывать другим пользователям",
                    checked = showBirthDate,
                    onCheckedChange = { v -> viewModel.setShowBirthDate(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Местоположение",
                    subtitle = "Показывать другим пользователям",
                    checked = showLocation,
                    onCheckedChange = { v -> viewModel.setShowLocation(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Сайт / ссылка",
                    subtitle = "Показывать другим пользователям",
                    checked = showWebsite,
                    onCheckedChange = { v -> viewModel.setShowWebsite(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Номер телефона",
                    subtitle = "Показывать в профиле другим пользователям",
                    checked = showPhoneNumber,
                    onCheckedChange = { v -> viewModel.setShowPhoneNumber(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Email",
                    subtitle = "Показывать в профиле другим пользователям",
                    checked = showEmail,
                    onCheckedChange = { v -> viewModel.setShowEmail(v) }
                ) }
            item { HorizontalDivider() }
            item { SectionTitle("Уведомления") }
            item { SettingsSwitchRow(
                    title = "Отключить все уведомления",
                    subtitle = "Полностью выключить push",
                    checked = muteAllNotifications,
                    onCheckedChange = { v -> viewModel.setMuteAllNotifications(v) }
                ) }
            item { SettingsSwitchRow(
                    title = "Звук",
                    subtitle = "Звуковой сигнал",
                    checked = notificationSound,
                    onCheckedChange = { v -> viewModel.setNotificationSound(v) },
                    enabled = !muteAllNotifications
                ) }
            item { SettingsSwitchRow(
                    title = "Вибрация",
                    subtitle = "Вибросигнал",
                    checked = notificationVibration,
                    onCheckedChange = { v -> viewModel.setNotificationVibration(v) },
                    enabled = !muteAllNotifications
                ) }
            item { HorizontalDivider() }
            item { SectionTitle("Аккаунт") }
            item {
                Button(
                    onClick = {
                        // Раньше кнопка вызывала viewModel.logout(), которая честно делала
                        // firebaseAuth.signOut(), но экран настроек никак на это не реагировал —
                        // не было ни навигации, ни колбэка, поэтому пользователь просто оставался
                        // на месте и выглядело, будто кнопка не работает.
                        viewModel.logout()
                        onLoggedOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YodoError),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text("Выйти из аккаунта", color = Color.White) }
            }
            item {
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp)
                ) { Text("Удалить аккаунт", color = YodoError) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun SettingsSwitchRow(
    title: String, subtitle: String, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true
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
