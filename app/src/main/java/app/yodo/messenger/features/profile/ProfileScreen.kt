package app.yodo.messenger.features.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material.icons.filled.CloudDone
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

    // Автосохранение с задержкой (debounce): каждое поле само планирует
    // сохранение через updateJobs, чтобы не делать запрос на каждый символ.
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
                title = {
                    Column {
                        Text("Профиль", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Изменения сохраняются автоматически",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                },
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
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(96.dp).clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    displayName = uiState.user?.displayName.orEmpty(),
                    photoUrl = uiState.user?.photoUrl,
                    avatarBase64 = uiState.user?.avatarBase64,
                    size = 96.dp
                )
                if (uiState.isUploadingAvatar) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(colorTheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Изменить фото", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            ProfileField(
                "Имя", name,
                onValueChange = { name = it; if (name.isNotBlank()) autoSave("name") { viewModel.updateDisplayName(name) } },
                isSaving = uiState.isSavingName
            )
            ProfileField(
                "Username", username,
                onValueChange = { username = it; if (username.isNotBlank()) autoSave("username") { viewModel.updateUsername(username) } },
                isSaving = uiState.isSavingUsername, prefix = "@"
            )
            ProfileField(
                "О себе (кратко)", bio,
                onValueChange = { if (it.length <= 150) { bio = it; autoSave("bio") { viewModel.updateBio(bio) } } },
                isSaving = uiState.isSavingBio
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Расширенный профиль", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())

            ProfileField(
                "Заметки «О себе»", aboutMe,
                onValueChange = { if (it.length <= 300) { aboutMe = it; autoSave("aboutMe") { viewModel.updateAboutMe(aboutMe) } } },
                isSaving = uiState.isSavingAboutMe
            )

            // Дата рождения выбирается только через календарь — исключает некорректный
            // ручной ввод (несуществующие даты, неверный формат, дату в будущем и т.п.).
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
                    .padding(top = 8.dp)
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
                    // Дополнительно блокируем будущие даты прямо в UI календаря
                    // (выбор "не по дням" всё равно перепроверяется в validateMillis при подтверждении).
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

            uiState.user?.phoneNumber?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            uiState.user?.email?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }

            uiState.errorMessage?.let { error ->
                Text(text = error, color = YodoError, modifier = Modifier.padding(top = 8.dp))
            }
        }
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
