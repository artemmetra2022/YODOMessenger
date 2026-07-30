package app.yodo.messenger.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.data.local.PinRequirement
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.allColorThemes
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
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
    val pinRequirement by viewModel.pinRequirement.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
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
    val colorTheme = LocalColorTheme.current

    LaunchedEffect(accountDeleted) { if (accountDeleted) onLoggedOut() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeError() }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Удалить аккаунт?") },
            text = { Text("Это действие необратимо. Все данные будут потеряны.") },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorTheme.primary.copy(alpha = 0.10f), Color.Transparent),
                        endY = 400f
                    )
                )
                .padding(padding)
        ) {

            // ════════════════════════════════════════
            // ОФОРМЛЕНИЕ
            // ════════════════════════════════════════
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.ColorLens,
                    title = "Оформление",
                    colorTheme = colorTheme
                )
            }

            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Brightness6,
                        title = "Тёмная тема",
                        subtitle = "Тёмное оформление интерфейса",
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.setDarkTheme(it) },
                        colorTheme = colorTheme
                    )
                }
            }

            // ════════════════════════════════════════
            // КАСТОМИЗАЦИЯ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.BubbleChart,
                    title = "Кастомизация",
                    colorTheme = colorTheme
                )
            }

            // Цветовая тема
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.ColorLens,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Цветовая тема", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            allColorThemes.forEach { theme ->
                                val isSelected = colorThemeName == theme.name.name
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { viewModel.setColorTheme(theme.name.name) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape
                                                ) else Modifier
                                            )
                                    )
                                    Text(
                                        theme.name.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 3.dp),
                                        color = if (isSelected) colorTheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Размер шрифта
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.FormatSize,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Размер шрифта", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        var sliderPosition by remember(fontSize) { mutableFloatStateOf(fontSize.ordinal.toFloat()) }
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                viewModel.setFontSize(FontSize.entries[sliderPosition.roundToInt()])
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FontSize.entries.forEach { size ->
                                Text(
                                    text = size.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (fontSize == size) colorTheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            // ЧАТЫ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Chat,
                    title = "Чаты",
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Keyboard,
                        title = "Отправка по Enter",
                        subtitle = "Enter отправляет сообщение",
                        checked = sendOnEnter,
                        onCheckedChange = { viewModel.setSendOnEnter(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Keyboard,
                        title = "Скрывать клавиатуру",
                        subtitle = "Закрывается после отправки",
                        checked = hideKeyboardOnSend,
                        onCheckedChange = { viewModel.setHideKeyboardOnSend(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Image,
                        title = "Автозагрузка фото",
                        subtitle = "Показывать изображения сразу",
                        checked = autoDownloadImages,
                        onCheckedChange = { viewModel.setAutoDownloadImages(it) },
                        colorTheme = colorTheme
                    )
                }
            }

            // ════════════════════════════════════════
            // КОНФИДЕНЦИАЛЬНОСТЬ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Конфиденциальность",
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = "Статус «в сети»",
                        subtitle = "Другие видят, когда ты онлайн",
                        checked = showOnlineStatus,
                        onCheckedChange = { viewModel.setShowOnlineStatus(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = "Статус прочтения",
                        subtitle = "Собеседник видит двойную галочку",
                        checked = showReadReceipts,
                        onCheckedChange = { viewModel.setShowReadReceipts(it) },
                        colorTheme = colorTheme
                    )
                }
            }

            // PIN
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                var showPinDialog by remember { mutableStateOf(false) }
                SettingsCard {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Lock,
                        title = if (isPinSet) "PIN-код установлен" else "Установить PIN-код",
                        subtitle = when (pinRequirement) {
                            PinRequirement.NEVER -> "Защита не активна"
                            PinRequirement.ON_CLOSE -> "Запрашивается при закрытии"
                            PinRequirement.ON_BACKGROUND -> "Запрашивается при сворачивании"
                        },
                        colorTheme = colorTheme,
                        onClick = { showPinDialog = true }
                    )
                }
                if (showPinDialog) {
                    PinSetupDialog(
                        isPinSet = isPinSet,
                        currentRequirement = pinRequirement,
                        onDismiss = { showPinDialog = false },
                        onSavePin = { pin, requirement ->
                            viewModel.setPin(pin, requirement)
                            showPinDialog = false
                        },
                        onRequirementChanged = { viewModel.setPinRequirement(it) },
                        onDisablePin = { viewModel.clearPin(); showPinDialog = false }
                    )
                }
            }

            // Заблокированные
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Block,
                        title = "Заблокированные пользователи",
                        subtitle = "Управление блокировками",
                        colorTheme = colorTheme,
                        onClick = onOpenBlockedUsers
                    )
                }
            }

            // Расширенный профиль — видимость полей
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Расширенный профиль",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Что видят другие пользователи",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = "Заметки «О себе»",
                        subtitle = "Показывать текст в расширенном профиле",
                        checked = showAboutMe,
                        onCheckedChange = { viewModel.setShowAboutMe(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = "Дата рождения",
                        subtitle = "Показывать другим пользователям",
                        checked = showBirthDate,
                        onCheckedChange = { viewModel.setShowBirthDate(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = "Местоположение",
                        subtitle = "Показывать другим пользователям",
                        checked = showLocation,
                        onCheckedChange = { viewModel.setShowLocation(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = "Сайт / ссылка",
                        subtitle = "Показывать другим пользователям",
                        checked = showWebsite,
                        onCheckedChange = { viewModel.setShowWebsite(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = "Номер телефона",
                        subtitle = "Показывать в профиле другим",
                        checked = showPhoneNumber,
                        onCheckedChange = { viewModel.setShowPhoneNumber(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = "Email",
                        subtitle = "Показывать в профиле другим",
                        checked = showEmail,
                        onCheckedChange = { viewModel.setShowEmail(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                }
            }

            // ════════════════════════════════════════
            // УВЕДОМЛЕНИЯ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.VolumeUp,
                    title = "Уведомления",
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.NotificationsOff,
                        title = "Отключить все уведомления",
                        subtitle = "Полностью выключить push",
                        checked = muteAllNotifications,
                        onCheckedChange = { viewModel.setMuteAllNotifications(it) },
                        colorTheme = colorTheme
                    )
                    AnimatedVisibility(
                        visible = !muteAllNotifications,
                        enter = fadeIn(tween(200)) + slideInVertically(tween(200))
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsToggleRow(
                                icon = Icons.Filled.VolumeUp,
                                title = "Звук",
                                subtitle = "Звуковой сигнал при сообщении",
                                checked = notificationSound,
                                onCheckedChange = { viewModel.setNotificationSound(it) },
                                colorTheme = colorTheme
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsToggleRow(
                                icon = Icons.Filled.Vibration,
                                title = "Вибрация",
                                subtitle = "Вибросигнал при сообщении",
                                checked = notificationVibration,
                                onCheckedChange = { viewModel.setNotificationVibration(it) },
                                colorTheme = colorTheme
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            // АККАУНТ
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Person,
                    title = "Аккаунт",
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsNavigateRow(
                        icon = Icons.Filled.ExitToApp,
                        title = "Выйти из аккаунта",
                        subtitle = "Завершить текущую сессию",
                        colorTheme = colorTheme,
                        tintOverride = YodoError,
                        onClick = { viewModel.logout(); onLoggedOut() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Delete,
                        title = "Удалить аккаунт",
                        subtitle = "Необратимое действие",
                        colorTheme = colorTheme,
                        tintOverride = YodoError,
                        onClick = { showDeleteAccountDialog = true }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Переиспользуемые компоненты
// ══════════════════════════════════════════════════════════

/** Диалог настройки PIN-кода: ввод/смена PIN, выбор режима требования, отключение защиты. */
@Composable
private fun PinSetupDialog(
    isPinSet: Boolean,
    currentRequirement: PinRequirement,
    onDismiss: () -> Unit,
    onSavePin: (String, PinRequirement) -> Unit,
    onRequirementChanged: (PinRequirement) -> Unit,
    onDisablePin: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var selectedRequirement by remember { mutableStateOf(currentRequirement) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPinSet) "Изменить PIN-код" else "Установить PIN-код") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("Новый PIN-код") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Повторите PIN-код") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Когда запрашивать PIN", style = MaterialTheme.typography.labelLarge)
                PinRequirement.entries.forEach { requirement ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedRequirement == requirement,
                                onClick = {
                                    selectedRequirement = requirement
                                    onRequirementChanged(requirement)
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRequirement == requirement,
                            onClick = {
                                selectedRequirement = requirement
                                onRequirementChanged(requirement)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(requirement.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> errorMessage = "PIN должен содержать не менее 4 цифр"
                    pin != confirmPin -> errorMessage = "PIN-коды не совпадают"
                    else -> onSavePin(pin, selectedRequirement)
                }
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Row {
                if (isPinSet) {
                    TextButton(onClick = onDisablePin) {
                        Text("Отключить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}

/** Заголовок секции с иконкой и градиентной линией-акцентом. */
@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    colorTheme: ColorTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    Brush.verticalGradient(listOf(colorTheme.primary, colorTheme.accent)),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorTheme.primary
        )
    }
}

/** Карточка-контейнер для группы настроек (поднятая тень, скруглённые углы). */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

/** Строка настройки с переключателем. */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colorTheme: ColorTheme,
    enabled: Boolean = true,
    showIcon: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorTheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.4f
                )
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorTheme.primary)
        )
    }
}

/** Строка-навигация (тап → действие, стрелка справа). */
@Composable
private fun SettingsNavigateRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colorTheme: ColorTheme,
    onClick: () -> Unit,
    tintOverride: Color? = null
) {
    val iconTint = tintOverride ?: colorTheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = iconTint)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}
