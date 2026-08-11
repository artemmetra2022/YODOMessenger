package app.yodo.messenger.features.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.features.profile.AvatarCropScreen
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (переработка каналов): создание канала с аватаркой.
 * Тап по кругу — выбор фото → кроп (AvatarCropScreen) → предпросмотр.
 */
@Composable
fun CreateChannelScreen(
    onBackClick: () -> Unit,
    onChannelCreated: (String) -> Unit,
    viewModel: CreateChannelViewModel = hiltViewModel()
) {
    var channelName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    // НОВОЕ (режимы доступа): выбранный режим доступа канала.
    var accessMode by remember { mutableStateOf(app.yodo.messenger.domain.model.ChannelAccessMode.OPEN) }
    val uiState by viewModel.uiState.collectAsState()
    val createdChatId by viewModel.createdChatId.collectAsState()
    val avatarBitmap by viewModel.avatarBitmap.collectAsState()
    val colorTheme = LocalColorTheme.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { pendingCropUri = it } }

    LaunchedEffect(createdChatId) {
        createdChatId?.let {
            onChannelCreated(it)
            viewModel.consumeCreatedChatId()
        }
    }

    // Поток: выбор фото → кроп → bitmap в ViewModel
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
                        Text("Новый канал", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Публикуйте посты для подписчиков",
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
        }
    ) { padding ->
        // ИСПРАВЛЕНО (кнопка «Создать» не была видна): раньше Column не прокручивался
        // и использовал Spacer(weight(1f)) — из-за блока выбора режима доступа кнопка
        // уезжала за нижний край экрана. Теперь экран прокручивается, кнопка всегда доступна.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ═══ Аватарка канала ═══
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier.size(96.dp).clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val bmp = avatarBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Аватарка канала",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                    // Крестик — убрать выбранное фото
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable { viewModel.clearAvatar() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Убрать фото",
                            tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                            .background(colorTheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (channelName.isBlank()) {
                            Icon(Icons.Filled.Campaign, contentDescription = null,
                                tint = colorTheme.primary, modifier = Modifier.size(36.dp))
                        } else {
                            Text(
                                channelName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = colorTheme.primary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(colorTheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Выбрать фото",
                                tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Text(
                if (avatarBitmap == null) "Добавьте аватарку (необязательно)" else "Аватарка выбрана",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = channelName,
                onValueChange = { channelName = it },
                label = { Text("Название канала") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание (необязательно)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minLines = 2,
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Вы будете владельцем канала: сможете публиковать посты, назначать админов " +
                        "и редактировать канал. Остальные пользователи найдут канал в поиске, " +
                        "подпишутся и смогут комментировать посты.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // НОВОЕ (режимы доступа): выбор режима доступа канала.
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Режим доступа",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            app.yodo.messenger.domain.model.ChannelAccessMode.values().forEach { mode ->
                AccessModeOption(
                    mode = mode,
                    selected = accessMode == mode,
                    colorTheme = colorTheme,
                    onClick = { accessMode = mode },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.createChannel(channelName, description, accessMode) },
                enabled = !uiState.isCreating && channelName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать канал")
                }
            }
        }
    }
}

/**
 * НОВОЕ (режимы доступа каналов): карточка выбора режима доступа (радио-стиль).
 * Используется и при создании, и при редактировании канала.
 */
@Composable
fun AccessModeOption(
    mode: app.yodo.messenger.domain.model.ChannelAccessMode,
    selected: Boolean,
    colorTheme: app.yodo.messenger.ui.theme.ColorTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (mode) {
        app.yodo.messenger.domain.model.ChannelAccessMode.OPEN -> Icons.Filled.Public
        app.yodo.messenger.domain.model.ChannelAccessMode.MODERATED -> Icons.Filled.HowToReg
        app.yodo.messenger.domain.model.ChannelAccessMode.HIDDEN -> Icons.Filled.Lock
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(
                if (selected) colorTheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (selected) colorTheme.primary else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mode.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colorTheme.primary)
        }
    }
}