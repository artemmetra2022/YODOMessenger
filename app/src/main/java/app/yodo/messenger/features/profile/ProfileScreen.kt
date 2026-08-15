package app.yodo.messenger.features.profile


import app.yodo.messenger.ui.components.DeveloperVerifiedBadge
import app.yodo.messenger.ui.components.isDeveloperAccount
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Call
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.features.security.SecurityViewModel
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.YodoSuccess
import app.yodo.messenger.util.BirthDateValidator
import app.yodo.messenger.util.EmojiOnlyValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onOpenSavedMessages: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenQrCode: () -> Unit = {},
    onOpenRecentCalls: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
    securityViewModel: SecurityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current

    // НОВОЕ: эмодзи-статус и текстовый статус теперь настраиваются прямо в профиле, рядом с аватаркой.
    val emojiStatus by securityViewModel.emojiStatus.collectAsState(initial = "")
    val customStatus by securityViewModel.customStatus.collectAsState(initial = "")
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showStatusEditor by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var aboutMe by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var showBirthDatePicker by remember { mutableStateOf(false) }
    var birthDateError by remember { mutableStateOf<String?>(null) }
    // НОВОЕ (кнопка «Ещё»): меню быстрых действий профиля.
    var showMoreMenu by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val updateJobs = remember { mutableMapOf<String, Job>() }

    fun autoSave(key: String, delayMs: Long = 800L, action: () -> Unit) {
        updateJobs[key]?.cancel()
        updateJobs[key] = coroutineScope.launch {
            delay(delayMs)
            action()
        }
    }

    LaunchedEffect(uiState.user) {
        if (!initialized && uiState.user != null) {
            name = uiState.user?.displayName.orEmpty()
            username = uiState.user?.username.orEmpty()
            bio = uiState.user?.bio.orEmpty()
            aboutMe = uiState.user?.aboutMe.orEmpty()
            birthDate = uiState.user?.birthDate.orEmpty()
            location = uiState.user?.location.orEmpty()
            website = uiState.user?.website.orEmpty()
            initialized = true
        }
    }

    val savedChatId by viewModel.savedChatId.collectAsState()
    LaunchedEffect(savedChatId) {
        savedChatId?.let {
            onOpenSavedMessages(it)
            viewModel.consumeSavedChatId()
        }
    }

    var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingCropUri = it } }

    val cropUri = pendingCropUri
    if (cropUri != null) {
        AvatarCropScreen(
            imageUri = cropUri,
            onBackClick = { pendingCropUri = null },
            onCropped = { bitmap ->
                pendingCropUri = null
                viewModel.uploadAvatar(bitmap)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                UserAvatar(
                    displayName = uiState.user?.displayName.orEmpty(),
                    photoUrl = uiState.user?.photoUrl,
                    avatarBase64 = uiState.user?.avatarBase64,
                    size = 112.dp
                )
                if (uiState.isUploadingAvatar) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(colorTheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.profile_change_photo_cd), tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                }

                // НОВОЕ: эмодзи-статус — бейдж поверх аватарки, нажатие открывает выбор смайлика.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showEmojiPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (emojiStatus.isNotBlank()) {
                        Text(emojiStatus, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Добавить эмодзи-статус",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = uiState.user?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_no_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isDeveloperAccount(uiState.user?.email)) {
                    Spacer(modifier = Modifier.width(6.dp))
                    DeveloperVerifiedBadge(size = 22.dp)
                }
            }
            // НОВОЕ (AE): публичный ID — можно нажать, чтобы скопировать.
            uiState.user?.publicId?.takeIf { it.isNotBlank() }?.let { pid ->
                val clipboard = LocalClipboardManager.current
                Text(
                    "ID: $pid",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { clipboard.setText(AnnotatedString(pid)) }
                )
            }
            Text(stringResource(R.string.profile_online), style = MaterialTheme.typography.bodyMedium, color = colorTheme.primary)

            // НОВОЕ: текстовый статус — тоже рядом с аватаркой/именем, нажатие открывает редактор.
            Text(
                text = customStatus.takeIf { it.isNotBlank() } ?: "Добавить статус",
                style = MaterialTheme.typography.bodySmall,
                color = if (customStatus.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { showStatusEditor = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ИСПРАВЛЕНО: "QR-код" → stringResource
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileQuickAction(Icons.Filled.Edit, stringResource(R.string.profile_edit_cd)) { imagePicker.launch("image/*") }
                ProfileQuickAction(Icons.Filled.History, stringResource(R.string.profile_history_cd), onOpenHistory)
                ProfileQuickAction(Icons.Filled.QrCode, stringResource(R.string.profile_qr_code), onOpenQrCode)
                Box {
                    ProfileQuickAction(Icons.Filled.MoreHoriz, stringResource(R.string.profile_more_cd)) { showMoreMenu = true }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_favorites)) },
                            leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null, tint = colorTheme.primary) },
                            onClick = { showMoreMenu = false; viewModel.openSavedMessages() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_history_cd)) },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                            onClick = { showMoreMenu = false; onOpenHistory() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_qr_code)) },
                            leadingIcon = { Icon(Icons.Filled.QrCode, contentDescription = null) },
                            onClick = { showMoreMenu = false; onOpenQrCode() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_devices)) },
                            leadingIcon = { Icon(Icons.Filled.Devices, contentDescription = null) },
                            onClick = { showMoreMenu = false; onOpenDevices() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_recent_calls)) },
                            leadingIcon = { Icon(Icons.Filled.Call, contentDescription = null) },
                            onClick = { showMoreMenu = false; onOpenRecentCalls() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ProfileCard {
                Text(stringResource(R.string.profile_about_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = aboutMe,
                    onValueChange = { if (it.length <= 300) { aboutMe = it; autoSave("aboutMe") { viewModel.updateAboutMe(aboutMe) } } },
                    placeholder = { Text(stringResource(R.string.profile_about_placeholder)) },
                    trailingIcon = if (uiState.isSavingAboutMe) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProfileCard {
                ProfileField(
                    stringResource(R.string.profile_name_field), name,
                    onValueChange = { name = it; if (name.isNotBlank()) autoSave("name") { viewModel.updateDisplayName(name) } },
                    isSaving = uiState.isSavingName
                )
                ProfileField(
                    stringResource(R.string.profile_username_field), username,
                    onValueChange = { username = it; if (username.isNotBlank()) autoSave("username") { viewModel.updateUsername(username) } },
                    isSaving = uiState.isSavingUsername, prefix = "@"
                )
                uiState.user?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.profile_phone_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(phone, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProfileCard(padding = 0.dp) {
                ProfileListItem(Icons.Filled.Bookmark, stringResource(R.string.profile_favorites), colorTheme.primary, Color.Unspecified) { viewModel.openSavedMessages() }
                HorizontalDivider()
                ProfileListItem(Icons.Filled.Call, stringResource(R.string.profile_recent_calls), app.yodo.messenger.ui.theme.YodoSuccess, Color.Unspecified, onOpenRecentCalls)
                HorizontalDivider()
                ProfileListItem(Icons.Filled.Devices, stringResource(R.string.profile_devices), MaterialTheme.colorScheme.onSurfaceVariant, Color.Unspecified, onOpenDevices)
                HorizontalDivider()
                ProfileListItem(
                    Icons.AutoMirrored.Filled.Logout, stringResource(R.string.profile_logout), YodoError, YodoError
                ) {
                    viewModel.logout()
                    onLoggedOut()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ProfileCard {
                Text(stringResource(R.string.profile_more_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = birthDate,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.profile_birth_date_label)) },
                    placeholder = { Text(stringResource(R.string.profile_birth_date_placeholder)) },
                    supportingText = birthDateError?.let { { Text(it, color = YodoError) } },
                    isError = birthDateError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            birthDateError = null
                            showBirthDatePicker = true
                        },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                if (uiState.isSavingBirthDate) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp).size(18.dp))
                }

                if (showBirthDatePicker) {
                    val initialMillis = BirthDateValidator.displayStringToMillis(birthDate)
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = initialMillis,
                        selectableDates = object : androidx.compose.material3.SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                                utcTimeMillis <= System.currentTimeMillis()
                        }
                    )
                    DatePickerDialog(
                        onDismissRequest = { showBirthDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val selectedMillis = datePickerState.selectedDateMillis
                                if (selectedMillis == null) {
                                    birthDateError = "Выберите дату"
                                    return@TextButton
                                }
                                val validationError = BirthDateValidator.validateMillis(selectedMillis)
                                if (validationError != null) {
                                    birthDateError = validationError
                                    return@TextButton
                                }
                                birthDate = BirthDateValidator.millisToDisplayString(selectedMillis)
                                birthDateError = null
                                showBirthDatePicker = false
                                viewModel.updateBirthDate(birthDate)
                            }) { Text("ОК") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBirthDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                // ИСПРАВЛЕНО: "Местоположение" → stringResource
                ProfileField(
                    stringResource(R.string.profile_location_field), location,
                    onValueChange = { location = it; autoSave("location") { viewModel.updateLocation(location) } },
                    isSaving = uiState.isSavingLocation
                )
                // ИСПРАВЛЕНО: "Сайт / ссылка" → stringResource
                ProfileField(
                    stringResource(R.string.profile_website_field), website,
                    onValueChange = { website = it; autoSave("website") { viewModel.updateWebsite(website) } },
                    isSaving = uiState.isSavingWebsite
                )

                // НОВОЕ (email-статус): рядом с почтой — бейдж "подтверждена/не подтверждена".
                uiState.user?.email?.let { email ->
                    val isVerified = uiState.user?.isEmailVerified == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(email, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isVerified) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (isVerified) YodoSuccess else YodoError,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVerified) "Подтверждена" else "Не подтверждена",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isVerified) YodoSuccess else YodoError
                        )
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(text = error, color = YodoError, modifier = Modifier.padding(top = 12.dp))
            }

            uiState.user?.uid?.let { uid ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                ProfilePostsSection(
                    userId = uid,
                    isOwnProfile = true,
                    colorTheme = colorTheme,
                    viewModel = hiltViewModel()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEmojiPicker) {
        EmojiStatusPickerDialog(
            current = emojiStatus,
            onPick = { picked -> securityViewModel.setEmojiStatus(picked) },
            onDismiss = { showEmojiPicker = false }
        )
    }

    if (showStatusEditor) {
        TextStatusEditorDialog(
            current = customStatus,
            onSave = { text -> securityViewModel.setCustomStatus(text) },
            onDismiss = { showStatusEditor = false }
        )
    }
}

// НОВОЕ: выбор эмодзи-статуса — только один смайлик, текст вводить нельзя.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EmojiStatusPickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    val presets = listOf("🔥", "🚀", "💼", "🌴", "❤️", "😴", "🎯", "☕", "📚", "🎮")

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Эмодзи-статус") },
        text = {
            Column {
                Text(
                    "Один смайлик рядом с вашей аватаркой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (draft == e) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { draft = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, style = MaterialTheme.typography.titleLarge) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Своё эмодзи: поле принимает только один смайлик, остальной ввод (текст) обрезается.
                OutlinedTextField(
                    value = draft,
                    onValueChange = { input -> draft = EmojiOnlyValidator.sanitize(input) },
                    label = { Text("Свой эмодзи") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(draft.trim()); onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(""); onDismiss() }) { Text("Убрать") }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

// НОВОЕ: редактор текстового статуса ("На встрече", "В отпуске" и т.п.).
@Composable
private fun TextStatusEditorDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(current) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Текстовый статус") },
        text = {
            Column {
                Text(
                    "Например: «На встрече», «В отпуске», «Не беспокоить».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Статус") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.trim()); onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(""); onDismiss() }) { Text("Убрать") }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

@Composable
private fun ProfileQuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProfileCard(padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(padding),
        content = content
    )
}

@Composable
private fun ProfileListItem(
    icon: ImageVector,
    label: String,
    iconColor: Color,
    textColor: Color = Color.Unspecified,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f).padding(start = 14.dp)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileField(
    label: String, value: String, onValueChange: (String) -> Unit,
    isSaving: Boolean, prefix: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = prefix?.let { { Text(it) } },
        trailingIcon = if (isSaving) {
            { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
        } else null,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true
    )
}