package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.CommentPermission
import app.yodo.messenger.features.profile.AvatarCropScreen
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (переработка каналов): экран редактирования канала.
 * Название/описание — по кнопке «Сохранить», аватарка — сразу после кропа,
 * управление админами — только владелец.
 */
@Composable
fun EditChannelScreen(
    onBackClick: () -> Unit,
    viewModel: EditChannelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val didSave by viewModel.didSave.collectAsState()
    val colorTheme = LocalColorTheme.current

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var adminQuery by remember { mutableStateOf("") }
    // НОВОЕ (F5): категория/теги/обложка канала.
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var pendingCoverUri by remember { mutableStateOf<Uri?>(null) }
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingCoverUri = it } }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingCropUri = it } }

    LaunchedEffect(uiState.profile) {
        if (!initialized && uiState.profile != null) {
            title = uiState.profile!!.title
            description = uiState.profile!!.description
            category = uiState.profile!!.category.orEmpty()
            tags = uiState.profile!!.tags.joinToString(", ")
            initialized = true
        }
    }
    LaunchedEffect(didSave) {
        if (didSave) {
            viewModel.consumeSaved()
            onBackClick()
        }
    }

    // Поток кропа аватарки — тот же экран, что и для пользовательского профиля.
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

    // НОВОЕ (F5): поток кропа обложки — переиспользуем экран кропа.
    val coverCropUri = pendingCoverUri
    if (coverCropUri != null) {
        AvatarCropScreen(
            imageUri = coverCropUri,
            onBackClick = { pendingCoverUri = null },
            onCropped = { bitmap ->
                pendingCoverUri = null
                viewModel.uploadCover(bitmap)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать канал", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ═══ Аватарка ═══
            Box(
                modifier = Modifier.size(104.dp).clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    displayName = uiState.profile?.title.orEmpty().ifBlank { "К" },
                    photoUrl = null,
                    avatarBase64 = uiState.profile?.avatarBase64,
                    size = 104.dp
                )
                if (uiState.isUploadingAvatar) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(30.dp).clip(CircleShape).background(colorTheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Изменить фото",
                                tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Text(
                "Нажмите на фото, чтобы изменить аватарку",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название канала") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            }

            Button(
                onClick = { viewModel.save(title, description) },
                enabled = !uiState.isSaving && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Сохранить изменения")
                }
            }

            // НОВОЕ (F5): категория, теги и обложка канала.
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            Text(
                "Категория и теги",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Помогают находить канал в поиске.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Категория (напр. Новости, Музыка)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Теги через запятую") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = { viewModel.saveMeta(category, tags) },
                enabled = !uiState.isSavingMeta,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                if (uiState.isSavingMeta) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Сохранить категорию и теги")
                }
            }

            Text(
                "Обложка канала",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "Баннер вверху профиля канала.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Button(
                onClick = { coverPicker.launch("image/*") },
                enabled = !uiState.isUploadingCover,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                if (uiState.isUploadingCover) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text(if (uiState.profile?.coverBase64 != null) "Изменить обложку" else "Загрузить обложку")
                }
            }

            // ═══ НОВОЕ: кто может писать комментарии (владелец/админ канала) ═══
            if (uiState.canManage) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Text(
                    "Комментарии",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Разрешить комментарии", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Если выключено — комментировать посты нельзя вообще.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.restrictions.allowComments,
                        onCheckedChange = { checked ->
                            viewModel.updateRestrictions(uiState.restrictions.copy(allowComments = checked))
                        },
                        enabled = !uiState.isSavingRestrictions,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colorTheme.primary)
                    )
                }

                // Кто именно может писать — актуально только когда комментарии включены.
                if (uiState.restrictions.allowComments) {
                    Text(
                        "Кто может писать",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
                    )
                    CommentPermission.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.isSavingRestrictions) {
                                    viewModel.updateRestrictions(uiState.restrictions.copy(commentPermission = option))
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.restrictions.commentPermission == option,
                                onClick = { viewModel.updateRestrictions(uiState.restrictions.copy(commentPermission = option)) },
                                enabled = !uiState.isSavingRestrictions
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(option.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ═══ Админы (только владелец) ═══
            if (uiState.isOwner) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Text(
                    "Администраторы канала",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Админы могут публиковать посты от имени канала.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )

                uiState.owner?.let { owner ->
                    AdminRow(
                        name = owner.displayName,
                        photoUrl = owner.photoUrl,
                        avatarBase64 = owner.avatarBase64,
                        role = "Владелец",
                        canRemove = false,
                        onRemove = {}
                    )
                }
                uiState.admins.forEach { admin ->
                    AdminRow(
                        name = admin.displayName,
                        photoUrl = admin.photoUrl,
                        avatarBase64 = admin.avatarBase64,
                        role = "Администратор",
                        canRemove = true,
                        onRemove = { viewModel.removeAdmin(admin.uid) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = adminQuery,
                    onValueChange = { adminQuery = it; viewModel.searchAdminCandidates(it) },
                    placeholder = { Text("Найти пользователя по имени или @username") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp).size(22.dp))
                }
                uiState.adminSearchResults.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addAdmin(user); adminQuery = "" }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(user.displayName, user.photoUrl, user.avatarBase64, size = 40.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.bodyLarge)
                            user.username?.let {
                                Text("@$it", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                        Icon(Icons.Filled.Add, contentDescription = "Назначить админом",
                            tint = colorTheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdminRow(
    name: String, photoUrl: String?, avatarBase64: String?,
    role: String, canRemove: Boolean, onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name, photoUrl, avatarBase64, size = 44.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(role, style = MaterialTheme.typography.labelSmall,
                color = LocalColorTheme.current.primary)
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Снять админа",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}