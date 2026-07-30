package app.yodo.messenger.features.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.util.BirthDateValidator
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
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current
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

    // п.35: навигация в чат "Избранное" из профиля
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
                title = { Text("Профиль", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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

            // Крупный аватар
            Box(
                modifier = Modifier.size(112.dp).clickable { imagePicker.launch("image/*") },
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
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Изменить фото", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = uiState.user?.displayName?.takeIf { it.isNotBlank() } ?: "Без имени",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            uiState.user?.username?.takeIf { it.isNotBlank() }?.let {
                Text("@$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("В сети", style = MaterialTheme.typography.bodyMedium, color = colorTheme.primary)

            Spacer(modifier = Modifier.height(16.dp))

            // Ряд кнопок: Изменить / История / QR-код / Ещё
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileQuickAction(Icons.Filled.Edit, "Изменить") { imagePicker.launch("image/*") }
                ProfileQuickAction(Icons.Filled.History, "История", onOpenHistory)
                ProfileQuickAction(Icons.Filled.QrCode, "QR-код", onOpenQrCode)
                ProfileQuickAction(Icons.Filled.MoreHoriz, "Ещё") { /* дополнительное меню появится позже */ }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Карточка "О себе"
            ProfileCard {
                Text("О себе", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = aboutMe,
                    onValueChange = { if (it.length <= 300) { aboutMe = it; autoSave("aboutMe") { viewModel.updateAboutMe(aboutMe) } } },
                    placeholder = { Text("Расскажите о себе") },
                    trailingIcon = if (uiState.isSavingAboutMe) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Карточка с именем пользователя и телефоном
            ProfileCard {
                ProfileField(
                    "Имя", name,
                    onValueChange = { name = it; if (name.isNotBlank()) autoSave("name") { viewModel.updateDisplayName(name) } },
                    isSaving = uiState.isSavingName
                )
                ProfileField(
                    "Имя пользователя", username,
                    onValueChange = { username = it; if (username.isNotBlank()) autoSave("username") { viewModel.updateUsername(username) } },
                    isSaving = uiState.isSavingUsername, prefix = "@"
                )
                uiState.user?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Номер телефона", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(phone, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Список действий: Избранное / Недавние звонки / Устройства / Выйти
            ProfileCard(padding = 0.dp) {
                ProfileListItem(Icons.Filled.Bookmark, "Избранное", colorTheme.primary, Color.Unspecified) { viewModel.openSavedMessages() }
                HorizontalDivider()
                ProfileListItem(Icons.Filled.Call, "Недавние звонки", app.yodo.messenger.ui.theme.YodoSuccess, Color.Unspecified, onOpenRecentCalls)
                HorizontalDivider()
                ProfileListItem(Icons.Filled.Devices, "Устройства", MaterialTheme.colorScheme.onSurfaceVariant, Color.Unspecified, onOpenDevices)
                HorizontalDivider()
                ProfileListItem(
                    Icons.AutoMirrored.Filled.Logout, "Выйти из аккаунта", YodoError, YodoError
                ) {
                    viewModel.logout()
                    onLoggedOut()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Расширенные поля — дата рождения, местоположение, сайт
            ProfileCard {
                Text("Дополнительно", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Дата рождения") },
                    placeholder = { Text("дд.мм.гггг") },
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
                            TextButton(onClick = { showBirthDatePicker = false }) { Text("Отмена") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                ProfileField(
                    "Местоположение", location,
                    onValueChange = { location = it; autoSave("location") { viewModel.updateLocation(location) } },
                    isSaving = uiState.isSavingLocation
                )
                ProfileField(
                    "Сайт / ссылка", website,
                    onValueChange = { website = it; autoSave("website") { viewModel.updateWebsite(website) } },
                    isSaving = uiState.isSavingWebsite
                )
                uiState.user?.email?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }

            uiState.errorMessage?.let { error ->
                Text(text = error, color = YodoError, modifier = Modifier.padding(top = 12.dp))
            }

            // ════════════════════════════════════════
            // Мои посты — видны всем пользователям, как во ВКонтакте
            // ════════════════════════════════════════
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
