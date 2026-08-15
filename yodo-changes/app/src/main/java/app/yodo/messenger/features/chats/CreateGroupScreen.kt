package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.features.profile.AvatarCropScreen
import app.yodo.messenger.ui.components.UserAvatar
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * Экран создания группы — расширенная версия в стиле CreateChannelScreen:
 * аватарка (с кропом), название, описание группы, выбор участников.
 *
 * ИСПРАВЛЕНО: весь экран теперь один прокручиваемый LazyColumn — раньше верхний
 * блок профиля занимал почти весь экран и окно выбора участников не было видно /
 * не прокручивалось. Теперь можно спокойно долистать вниз до списка людей.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(
    onBackClick: () -> Unit,
    onGroupCreated: (String) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // НОВОЕ (конфиденциальность групп): выбранный режим доступа (как у каналов).
    var accessMode by remember { mutableStateOf(app.yodo.messenger.domain.model.ChannelAccessMode.OPEN) }
    var query by remember { mutableStateOf("") }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val createdChatId by viewModel.createdChatId.collectAsState()
    val avatarBitmap by viewModel.avatarBitmap.collectAsState()
    val colorTheme = LocalColorTheme.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingCropUri = it } }

    LaunchedEffect(createdChatId) {
        createdChatId?.let {
            onGroupCreated(it)
            viewModel.consumeCreatedChatId()
        }
    }

    // Поток: выбор фото → кроп → bitmap в ViewModel (как в CreateChannelScreen)
    val cropUri = pendingCropUri
    if (cropUri != null) {
        AvatarCropScreen(
            imageUri = cropUri,
            onBackClick = { pendingCropUri = null },
            onCropped = { bitmap ->
                pendingCropUri = null
                viewModel.setAvatar(bitmap)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Новая группа", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Выберите минимум 2 участников",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        // Кнопка "Создать группу" в bottomBar — всегда видна, imePadding поднимает её над клавиатурой.
        bottomBar = {
            Button(
                onClick = { viewModel.createGroup(groupName, description, accessMode) },
                enabled = !uiState.isCreating && uiState.selectedUsers.size >= 2 && groupName.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp)
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать группу")
                }
            }
        }
    ) { padding ->
        // ════════════════════════════════════════════════════════════
        // Весь экран — единый прокручиваемый список. Верхний блок профиля
        // и блок выбора участников теперь листаются вместе.
        // ════════════════════════════════════════════════════════════
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ─── Профиль группы: аватарка + поля ───
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Аватарка группы — тап открывает галерею → кроп
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = avatarBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Аватарка группы",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .clickable { viewModel.clearAvatar() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Убрать фото",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(colorTheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (groupName.isBlank()) {
                                    Icon(
                                        Icons.Filled.Group,
                                        contentDescription = null,
                                        tint = colorTheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    Text(
                                        groupName.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = colorTheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(colorTheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.CameraAlt,
                                        contentDescription = "Выбрать фото",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        if (avatarBitmap == null) "Добавьте фото группы (необязательно)" else "Фото выбрано",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Название группы") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { if (it.length <= 200) description = it },
                        label = { Text("Описание (необязательно)") },
                        supportingText = { Text("${description.length}/200") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // НОВОЕ (конфиденциальность групп): выбор режима доступа — как у каналов.
                    Text(
                        "Конфиденциальность группы",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorTheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    app.yodo.messenger.domain.model.ChannelAccessMode.values().forEach { mode ->
                        AccessModeOption(
                            mode = mode,
                            selected = accessMode == mode,
                            colorTheme = colorTheme,
                            onClick = { accessMode = mode }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                HorizontalDivider()
            }

            // ─── Лента выбранных участников ───
            if (uiState.selectedUsers.isNotEmpty()) {
                item {
                    Text(
                        "Выбрано: ${uiState.selectedUsers.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorTheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.selectedUsers.forEach { user ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colorTheme.primary.copy(alpha = 0.12f))
                                    .clickable { viewModel.removeSelected(user) }
                                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                UserAvatar(
                                    displayName = user.displayName,
                                    photoUrl = user.photoUrl,
                                    avatarBase64 = user.avatarBase64,
                                    size = 28.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    user.displayName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Убрать",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }

            // ─── Поиск ───
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.onQueryChanged(it)
                    },
                    placeholder = { Text("Поиск по имени или @username") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; viewModel.onQueryChanged("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true
                )
            }

            // ─── Список пользователей / пустые состояния ───
            when {
                uiState.isSearching -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                query.isBlank() -> item {
                    Text(
                        "Начните вводить имя или @username, чтобы найти людей",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
                uiState.searchResults.isEmpty() -> item {
                    Text(
                        "Никого не нашли",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
                else -> items(uiState.searchResults, key = { it.uid }) { user ->
                    SelectableUserRow(
                        user = user,
                        colorTheme = colorTheme,
                        onClick = { viewModel.toggleUser(user) }
                    )
                }
            }

            // ─── Ошибка ───
            uiState.errorMessage?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableUserRow(
    user: YodoUser,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            displayName = user.displayName,
            photoUrl = user.photoUrl,
            avatarBase64 = user.avatarBase64,
            size = 48.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            val subtitle = user.username?.let { "@$it" }
                ?: user.bio?.takeIf { it.isNotBlank() }
                ?: "Нажмите, чтобы добавить в группу"
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(colorTheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Добавить",
                tint = colorTheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
