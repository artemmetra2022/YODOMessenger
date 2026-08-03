package app.yodo.messenger.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Poll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.data.local.AppLanguage
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
    val currentLanguage by viewModel.currentLanguage.collectAsState()
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
    // НОВОЕ (расширенные опросы): переключатель также доступен на экране регистрации (общее значение).
    val advancedPollsEnabled by viewModel.advancedPollsEnabled.collectAsState()
    val notificationSound by viewModel.notificationSound.collectAsState()
    val notificationVibration by viewModel.notificationVibration.collectAsState()
    val muteAllNotifications by viewModel.muteAllNotifications.collectAsState()
    val accountDeleted by viewModel.accountDeleted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val errorMessageResId by viewModel.errorMessageResId.collectAsState()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val colorTheme = LocalColorTheme.current

    // Резолвим ресурсные строки ошибок здесь (в composable-контексте), т.к. ViewModel
    // хранит только resId — сам текст зависит от текущего выбранного языка.
    val notAuthorizedText = stringResource(R.string.settings_not_authorized)

    LaunchedEffect(accountDeleted) { if (accountDeleted) onLoggedOut() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeError() }
    }
    LaunchedEffect(errorMessageResId) {
        errorMessageResId?.let { resId ->
            val text = if (resId == R.string.settings_not_authorized) notAuthorizedText else ""
            if (text.isNotEmpty()) snackbarHostState.showSnackbar(text)
            viewModel.consumeError()
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_account_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showDeleteAccountDialog = false; viewModel.deleteAccount() }) {
                    Text(stringResource(R.string.settings_delete_account), color = YodoError)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.settings_profile))
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
                    title = stringResource(R.string.settings_section_appearance),
                    colorTheme = colorTheme
                )
            }

            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Brightness6,
                        title = stringResource(R.string.settings_dark_theme),
                        subtitle = stringResource(R.string.settings_dark_theme_subtitle),
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
                    title = stringResource(R.string.settings_section_customization),
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
                            Text(stringResource(R.string.settings_color_theme), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
                            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
            // ЯЗЫК
            // ════════════════════════════════════════
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_section_language),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Language,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppLanguage.entries.forEach { language ->
                                val isSelected = currentLanguage == language
                                Text(
                                    text = stringResource(language.labelResId),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colorTheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.setLanguage(language) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                    title = stringResource(R.string.settings_section_chats),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Keyboard,
                        title = stringResource(R.string.settings_send_on_enter),
                        subtitle = stringResource(R.string.settings_send_on_enter_subtitle),
                        checked = sendOnEnter,
                        onCheckedChange = { viewModel.setSendOnEnter(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Keyboard,
                        title = stringResource(R.string.settings_hide_keyboard),
                        subtitle = stringResource(R.string.settings_hide_keyboard_subtitle),
                        checked = hideKeyboardOnSend,
                        onCheckedChange = { viewModel.setHideKeyboardOnSend(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Image,
                        title = stringResource(R.string.settings_auto_download),
                        subtitle = stringResource(R.string.settings_auto_download_subtitle),
                        checked = autoDownloadImages,
                        onCheckedChange = { viewModel.setAutoDownloadImages(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    // НОВОЕ (расширенные опросы): включает доп. параметры при создании опроса —
                    // множественный выбор ответов и дату автоматического закрытия голосования.
                    // Тот же переключатель показывается и на экране регистрации.
                    SettingsToggleRow(
                        icon = Icons.Filled.Poll,
                        title = stringResource(R.string.register_advanced_polls_title),
                        subtitle = stringResource(R.string.register_advanced_polls_subtitle),
                        checked = advancedPollsEnabled,
                        onCheckedChange = { viewModel.setAdvancedPollsEnabled(it) },
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
                    title = stringResource(R.string.settings_section_privacy),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = stringResource(R.string.settings_online_status),
                        subtitle = stringResource(R.string.settings_online_status_subtitle),
                        checked = showOnlineStatus,
                        onCheckedChange = { viewModel.setShowOnlineStatus(it) },
                        colorTheme = colorTheme
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.RemoveRedEye,
                        title = stringResource(R.string.settings_read_receipts),
                        subtitle = stringResource(R.string.settings_read_receipts_subtitle),
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
                val pinSetTitle = stringResource(R.string.settings_pin_set)
                val pinSetUpTitle = stringResource(R.string.settings_pin_set_up)
                val pinNeverSubtitle = stringResource(R.string.settings_pin_never)
                val pinOnCloseSubtitle = stringResource(R.string.settings_pin_on_close)
                val pinOnBackgroundSubtitle = stringResource(R.string.settings_pin_on_background)
                SettingsCard {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Lock,
                        title = if (isPinSet) pinSetTitle else pinSetUpTitle,
                        subtitle = when (pinRequirement) {
                            PinRequirement.NEVER -> pinNeverSubtitle
                            PinRequirement.ON_CLOSE -> pinOnCloseSubtitle
                            PinRequirement.ON_BACKGROUND -> pinOnBackgroundSubtitle
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
                        title = stringResource(R.string.settings_blocked_users),
                        subtitle = stringResource(R.string.settings_blocked_users_subtitle),
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
                                    stringResource(R.string.settings_extended_profile),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(R.string.settings_extended_profile_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_about_me),
                        subtitle = stringResource(R.string.settings_about_me_subtitle),
                        checked = showAboutMe,
                        onCheckedChange = { viewModel.setShowAboutMe(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_birth_date),
                        subtitle = stringResource(R.string.settings_show_to_others),
                        checked = showBirthDate,
                        onCheckedChange = { viewModel.setShowBirthDate(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_location),
                        subtitle = stringResource(R.string.settings_show_to_others),
                        checked = showLocation,
                        onCheckedChange = { viewModel.setShowLocation(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_website),
                        subtitle = stringResource(R.string.settings_show_to_others),
                        checked = showWebsite,
                        onCheckedChange = { viewModel.setShowWebsite(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_phone_number),
                        subtitle = stringResource(R.string.settings_show_in_profile),
                        checked = showPhoneNumber,
                        onCheckedChange = { viewModel.setShowPhoneNumber(it) },
                        colorTheme = colorTheme,
                        showIcon = false
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_email),
                        subtitle = stringResource(R.string.settings_show_in_profile),
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
                    title = stringResource(R.string.settings_section_notifications),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.NotificationsOff,
                        title = stringResource(R.string.settings_mute_all),
                        subtitle = stringResource(R.string.settings_mute_all_subtitle),
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
                                title = stringResource(R.string.settings_sound),
                                subtitle = stringResource(R.string.settings_sound_subtitle),
                                checked = notificationSound,
                                onCheckedChange = { viewModel.setNotificationSound(it) },
                                colorTheme = colorTheme
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                            SettingsToggleRow(
                                icon = Icons.Filled.Vibration,
                                title = stringResource(R.string.settings_vibration),
                                subtitle = stringResource(R.string.settings_vibration_subtitle),
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
                    title = stringResource(R.string.settings_section_account),
                    colorTheme = colorTheme
                )
            }
            item {
                SettingsCard {
                    SettingsNavigateRow(
                        icon = Icons.Filled.ExitToApp,
                        title = stringResource(R.string.settings_logout),
                        subtitle = stringResource(R.string.settings_logout_subtitle),
                        colorTheme = colorTheme,
                        tintOverride = YodoError,
                        onClick = { viewModel.logout(); onLoggedOut() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    SettingsNavigateRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.settings_delete_account),
                        subtitle = stringResource(R.string.settings_delete_account_subtitle),
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

    val minDigitsError = stringResource(R.string.pin_dialog_min_digits)
    val mismatchError = stringResource(R.string.pin_dialog_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPinSet) stringResource(R.string.pin_dialog_change_title) else stringResource(R.string.pin_dialog_set_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text(stringResource(R.string.pin_dialog_new_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text(stringResource(R.string.pin_dialog_repeat_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.pin_dialog_when_ask), style = MaterialTheme.typography.labelLarge)
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
                    pin.length < 4 -> errorMessage = minDigitsError
                    pin != confirmPin -> errorMessage = mismatchError
                    else -> onSavePin(pin, selectedRequirement)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (isPinSet) {
                    TextButton(onClick = onDisablePin) {
                        Text(stringResource(R.string.pin_dialog_disable), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
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
