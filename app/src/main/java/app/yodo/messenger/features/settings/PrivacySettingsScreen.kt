package app.yodo.messenger.features.settings

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.data.local.PinRequirement
import app.yodo.messenger.ui.theme.LocalColorTheme
import kotlin.math.roundToInt

/**
 * НОВОЕ (разделение настроек по категориям): «Конфиденциальность» — статус «в
 * сети», отметки о прочтении, автоудаление аккаунта, PIN/ложный PIN,
 * заблокированные, «кто может…», видимость расширенного профиля. Логика и
 * внешний вид перенесены из бывшего монолитного SettingsScreen.kt без
 * изменений поведения. PinCellsInput переиспользуется из PinCells.kt (тот же
 * пакет) — используется как раньше в PIN-диалогах.
 */
@Composable
fun PrivacySettingsScreen(
    onBackClick: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenPrivacyWho: () -> Unit,
    initialAnchorId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val showOnlineStatus by viewModel.showOnlineStatus.collectAsState()
    val showReadReceipts by viewModel.showReadReceipts.collectAsState()
    val pinRequirement by viewModel.pinRequirement.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
    val isDecoyPinSet by viewModel.isDecoyPinSet.collectAsState()
    val pinLockDelaySeconds by viewModel.pinLockDelaySeconds.collectAsState()
    val showBirthDate by viewModel.showBirthDate.collectAsState()
    val showAboutMe by viewModel.showAboutMe.collectAsState()
    val showLocation by viewModel.showLocation.collectAsState()
    val showWebsite by viewModel.showWebsite.collectAsState()
    val showPhoneNumber by viewModel.showPhoneNumber.collectAsState()
    val showEmail by viewModel.showEmail.collectAsState()
    val autoDeleteEnabled by viewModel.autoDeleteEnabled.collectAsState()
    val autoDeleteDays by viewModel.autoDeleteDays.collectAsState()

    var showAutoDeleteDialog by remember { mutableStateOf(false) }

    val colorTheme = LocalColorTheme.current
    val listState = rememberLazyListState()
    val anchorPositions = remember { mutableMapOf<String, Float>() }
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }

    suspend fun scrollAndHighlight(anchorId: String) {
        highlightedAnchor = anchorId
        anchorPositions[anchorId]?.let { listState.animateScrollBy(it - 24f) }
        kotlinx.coroutines.delay(SETTINGS_HIGHLIGHT_DURATION_MS)
        if (highlightedAnchor == anchorId) highlightedAnchor = null
    }

    LaunchedEffect(initialAnchorId) {
        if (initialAnchorId != null) {
            kotlinx.coroutines.delay(250)
            scrollAndHighlight(initialAnchorId)
        }
    }

    if (showAutoDeleteDialog) {
        AutoDeleteDialog(
            enabled = autoDeleteEnabled,
            days = autoDeleteDays,
            onDismiss = { showAutoDeleteDialog = false },
            onSave = { enabled, days ->
                viewModel.setAutoDeleteEnabled(enabled)
                viewModel.setAutoDeleteDays(days)
                showAutoDeleteDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Конфиденциальность", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                SettingsSectionHeader(
                    icon = Icons.Filled.PrivacyTip,
                    title = stringResource(R.string.settings_section_privacy),
                    modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_PRIVACY, anchorPositions, highlightedAnchor, colorTheme),
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

            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_AUTO_DELETE, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Timer,
                        title = "Автоудаление аккаунта",
                        subtitle = if (autoDeleteEnabled) "Через $autoDeleteDays дней неактивности" else "Выключено",
                        colorTheme = colorTheme,
                        onClick = { showAutoDeleteDialog = true }
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
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_PIN, anchorPositions, highlightedAnchor, colorTheme)) {
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
                        currentLockDelaySeconds = pinLockDelaySeconds,
                        onDismiss = { showPinDialog = false },
                        onSavePin = { pin, requirement ->
                            viewModel.setPin(pin, requirement)
                            showPinDialog = false
                        },
                        onRequirementChanged = { viewModel.setPinRequirement(it) },
                        onLockDelayChanged = { viewModel.setPinLockDelaySeconds(it) },
                        onDisablePin = { viewModel.clearPin(); showPinDialog = false }
                    )
                }

                // НОВОЕ (скрытые чаты): ложный (decoy) PIN — доступен только когда задан основной PIN.
                if (isPinSet) {
                    var showDecoyDialog by remember { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_DECOY_PIN, anchorPositions, highlightedAnchor, colorTheme)) {
                        SettingsNavigateRow(
                            icon = Icons.Filled.Lock,
                            title = if (isDecoyPinSet) "Ложный PIN задан" else "Настроить ложный PIN",
                            subtitle = "Вход по этому коду скрывает выбранные чаты",
                            colorTheme = colorTheme,
                            onClick = { showDecoyDialog = true }
                        )
                    }
                    if (showDecoyDialog) {
                        DecoyPinDialog(
                            isDecoyPinSet = isDecoyPinSet,
                            onDismiss = { showDecoyDialog = false },
                            onSavePin = { pin ->
                                viewModel.setDecoyPin(pin)
                                showDecoyDialog = false
                            },
                            onDisablePin = {
                                viewModel.clearDecoyPin()
                                showDecoyDialog = false
                            }
                        )
                    }
                }
            }

            // Заблокированные
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_BLOCKED_USERS, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.Block,
                        title = stringResource(R.string.settings_blocked_users),
                        subtitle = stringResource(R.string.settings_blocked_users_subtitle),
                        colorTheme = colorTheme,
                        onClick = onOpenBlockedUsers
                    )
                }
            }

            // НОВОЕ (п.15): настройки приватности «Кто может приглашать в группы / писать / смотреть профиль».
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_WHO_CAN, anchorPositions, highlightedAnchor, colorTheme)) {
                    SettingsNavigateRow(
                        icon = Icons.Filled.GroupAdd,
                        title = "Кто может…",
                        subtitle = "Приглашать в группы, писать вам, смотреть профиль",
                        colorTheme = colorTheme,
                        onClick = onOpenPrivacyWho
                    )
                }
            }

            // Расширенный профиль
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SettingsCard(modifier = Modifier.settingsSearchAnchor(SettingsSearchIndex.ANCHOR_PROFILE_VISIBILITY, anchorPositions, highlightedAnchor, colorTheme)) {
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
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ══════════════════════════════════════════════════════════
// НОВОЕ (п.18): диалог автоудаления аккаунта
// ══════════════════════════════════════════════════════════
@Composable
private fun AutoDeleteDialog(
    enabled: Boolean,
    days: Int,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int) -> Unit
) {
    var localEnabled by remember { mutableStateOf(enabled) }
    var localDays by remember { mutableFloatStateOf(days.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Автоудаление аккаунта") },
        text = {
            Column {
                Text(
                    "Если вы не будете заходить в приложение в течение указанного времени, аккаунт будет автоматически удалён.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Включить автоудаление", modifier = Modifier.weight(1f))
                    Switch(
                        checked = localEnabled,
                        onCheckedChange = { localEnabled = it }
                    )
                }
                if (localEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Период неактивности: ${localDays.roundToInt()} дней")
                    Slider(
                        value = localDays,
                        onValueChange = { localDays = it },
                        valueRange = 7f..365f,
                        steps = 51
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(localEnabled, localDays.roundToInt()) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ══════════════════════════════════════════════════════════
// Диалог настройки основного PIN-кода
// ══════════════════════════════════════════════════════════
@Composable
private fun PinSetupDialog(
    isPinSet: Boolean,
    currentRequirement: PinRequirement,
    onDismiss: () -> Unit,
    onSavePin: (String, PinRequirement) -> Unit,
    onRequirementChanged: (PinRequirement) -> Unit,
    onDisablePin: () -> Unit,
    currentLockDelaySeconds: Int = 0,
    onLockDelayChanged: (Int) -> Unit = {}
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
                Text(stringResource(R.string.pin_dialog_new_pin), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = pin, onPinChange = { pin = it; errorMessage = null }, length = 4)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.pin_dialog_repeat_pin), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = confirmPin, onPinChange = { confirmPin = it; errorMessage = null }, length = 4, isError = errorMessage != null)
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
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

                // НОВОЕ: выбор времени, через которое приложение блокируется после сворачивания.
                if (selectedRequirement == PinRequirement.ON_BACKGROUND) {
                    val presets = listOf(0 to "Сразу", 30 to "30 сек", 60 to "1 мин", 300 to "5 мин", 900 to "15 мин")
                    var showCustom by remember { mutableStateOf(presets.none { it.first == currentLockDelaySeconds }) }
                    var lockDelay by remember { mutableStateOf(currentLockDelaySeconds) }
                    var customText by remember { mutableStateOf(if (presets.none { it.first == currentLockDelaySeconds }) currentLockDelaySeconds.toString() else "") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Блокировать через", style = MaterialTheme.typography.labelLarge)
                    presets.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = !showCustom && lockDelay == seconds,
                                    onClick = { showCustom = false; lockDelay = seconds; onLockDelayChanged(seconds) }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !showCustom && lockDelay == seconds,
                                onClick = { showCustom = false; lockDelay = seconds; onLockDelayChanged(seconds) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = showCustom, onClick = { showCustom = true })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = showCustom, onClick = { showCustom = true })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Своё время (сек)", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (showCustom) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { new ->
                                val digits = new.filter(Char::isDigit).take(5)
                                customText = digits
                                digits.toIntOrNull()?.let { onLockDelayChanged(it) }
                            },
                            label = { Text("Секунд") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
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

// НОВОЕ (скрытые чаты): диалог настройки ложного (decoy) PIN-кода.
@Composable
private fun DecoyPinDialog(
    isDecoyPinSet: Boolean,
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit,
    onDisablePin: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDecoyPinSet) "Изменить ложный PIN" else "Ложный PIN") },
        text = {
            Column {
                Text(
                    "Введите отдельный PIN-код. При входе по нему приложение откроется без скрытых чатов — как будто их нет. Он должен отличаться от основного PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Ложный PIN", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = pin, onPinChange = { pin = it; errorMessage = null }, length = 4)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Повторите ложный PIN", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                PinCellsInput(pin = confirmPin, onPinChange = { confirmPin = it; errorMessage = null }, length = 4, isError = errorMessage != null)
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> errorMessage = "Минимум 4 цифры"
                    pin != confirmPin -> errorMessage = "PIN-коды не совпадают"
                    else -> onSavePin(pin)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (isDecoyPinSet) {
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
