package app.yodo.messenger.offline

import android.Manifest
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.YodoPrimary
import app.yodo.messenger.util.AudioUtils
import app.yodo.messenger.util.ChatImageQuality
import app.yodo.messenger.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        permissions += Manifest.permission.BLUETOOTH_CONNECT
        permissions += Manifest.permission.BLUETOOTH_SCAN
    } else {
        permissions += Manifest.permission.BLUETOOTH
        permissions += Manifest.permission.BLUETOOTH_ADMIN
    }
    permissions += Manifest.permission.ACCESS_FINE_LOCATION
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    return permissions.toTypedArray()
}

@Composable
fun OfflineChatScreen(
    onBackClick: () -> Unit,
    viewModel: OfflineChatViewModel = hiltViewModel()
) {
    var permissionsGranted by remember { mutableStateOf(false) }

    val identityState by viewModel.identityState.collectAsState()
    val offlineProfile by viewModel.offlineProfile.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var showProfileEditor by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        // Автостарт обрабатывается в LaunchedEffect(permissionsGranted, identityState),
        // чтобы избежать гонки между разрешениями и загрузкой identityState.
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions())
    }

    // Автостарт поиска когда разрешения выданы и имя уже известно.
    // Используем LaunchedEffect вместо колбэка permissionLauncher,
    // чтобы не было гонки: identityState может ещё не эмитить значение
    // в момент колбэка, а здесь реагируем на оба изменения одновременно.
    LaunchedEffect(permissionsGranted, identityState) {
        if (permissionsGranted && identityState is OfflineIdentityState.Online) {
            viewModel.startSearching()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnect() }
    }

    if (showProfileEditor) {
        OfflineProfileEditor(
            profile = offlineProfile,
            onDismiss = { showProfileEditor = false },
            onSave = {
                viewModel.updateOfflineProfile(it)
                showProfileEditor = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_chat_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = { showProfileEditor = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.offline_profile_edit))
                    }
                    // НОВОЕ (батч 7): очистить историю чата.
                    IconButton(onClick = { viewModel.clearMessages() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Очистить чат")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissionsGranted -> {
                    // Разрешения ещё не выданы
                    Text(
                        text = stringResource(R.string.offline_chat_permissions),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                connectionState == ConnectionState.CONNECTED -> {
                    ConnectedChatContent(viewModel)
                }

                identityState is OfflineIdentityState.NeedsName -> {
                    // Не авторизован и ещё не начал поиск — показываем экран ввода имени
                    NameEntryContent(
                        savedName = (identityState as OfflineIdentityState.NeedsName).savedName,
                        onStartSearching = { name ->
                            viewModel.startSearchingWithCustomName(name)
                        }
                    )
                }

                else -> {
                    DeviceDiscoveryContent(
                        viewModel = viewModel,
                        connectionState = connectionState,
                        onEditProfile = { showProfileEditor = true }
                    )
                }
            }
        }
    }
}

/**
 * Экран ввода имени — показывается только гостям (не авторизованным пользователям).
 * Авторизованные пользователи этот экран не видят — их имя подтягивается автоматически.
 */
@Composable
private fun NameEntryContent(
    savedName: String,
    onStartSearching: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(savedName) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = YodoPrimary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.offline_name_question),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.offline_name_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text(stringResource(R.string.offline_name_label)) },
            placeholder = { Text(stringResource(R.string.offline_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                if (nameInput.isNotBlank()) onStartSearching(nameInput)
            })
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                keyboardController?.hide()
                onStartSearching(nameInput)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = true // Пустое имя — получит случайный "Гость-XXXX" автоматически
        ) {
            Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.offline_start_search))
        }
    }
}

@Composable
private fun OfflineProfileEditor(
    profile: OfflineProfile,
    onDismiss: () -> Unit,
    onSave: (OfflineProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var status by remember { mutableStateOf(profile.status) }
    var emoji by remember { mutableStateOf(profile.emoji) }
    var colorIndex by remember { mutableStateOf(profile.colorIndex) }
    val colors = listOf(
        Color(0xFF2F80ED), Color(0xFF00A884), Color(0xFFE66A2C),
        Color(0xFF8E5BD9), Color(0xFFD14D72), Color(0xFF138A9B)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.offline_profile_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.offline_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text(stringResource(R.string.offline_profile_emoji)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it.take(40) },
                    label = { Text(stringResource(R.string.offline_profile_status)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(160) },
                    label = { Text(stringResource(R.string.offline_profile_bio)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.offline_profile_color), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier.size(30.dp).clip(CircleShape).background(color)
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            if (index == colorIndex) Text("✓", color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(OfflineProfile(name, bio, status, emoji, colorIndex)) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.offline_profile_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.offline_profile_cancel)) } }
    )
}

@Composable
private fun OfflineProfileCard(
    profile: OfflineProfile,
    shortId: String,
    onEdit: () -> Unit
) {
    val colors = listOf(
        Color(0xFF2F80ED), Color(0xFF00A884), Color(0xFFE66A2C),
        Color(0xFF8E5BD9), Color(0xFFD14D72), Color(0xFF138A9B)
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors[profile.colorIndex.coerceIn(colors.indices)]
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) { Text(profile.emoji.ifBlank { profile.initials }, color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(profile.displayName.ifBlank { stringResource(R.string.offline_guest_default) }, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.offline_profile_id, shortId), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                Text(profile.status.ifBlank { stringResource(R.string.offline_profile_available) }, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                if (profile.bio.isNotBlank()) Text(profile.bio, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White) }
        }
    }
}

@Composable
private fun DeviceDiscoveryContent(
    viewModel: OfflineChatViewModel,
    connectionState: ConnectionState,
    onEditProfile: () -> Unit
) {
    val devices by viewModel.discoveredDevices.collectAsState()
    val identityState by viewModel.identityState.collectAsState()
    val offlineProfile by viewModel.offlineProfile.collectAsState()
    val myShortId by viewModel.myShortId.collectAsState()
    var showRadar by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OfflineProfileCard(profile = offlineProfile, shortId = myShortId, onEdit = onEditProfile)
        val myName = when (val s = identityState) {
            is OfflineIdentityState.Online -> s.displayName
            is OfflineIdentityState.NeedsName -> s.savedName.ifBlank { stringResource(R.string.offline_guest_default) }
            is OfflineIdentityState.Searching -> s.displayName
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(YodoPrimary)
            )
            Text(
                text = "  Вы видны как: $myName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // НОВОЕ (mesh): мой шестизначный номер — его можно сообщить другу.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(YodoPrimary.copy(alpha = 0.10f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(YodoPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Hub, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Ваш номер в офлайн-чате",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "#$myShortId",
                    style = MaterialTheme.typography.headlineSmall,
                    color = YodoPrimary
                )
                Text(
                    text = "Сообщите этот номер другу — и он напишет именно вам",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BluetoothSearching, contentDescription = null, tint = YodoPrimary)
            Text(
                text = stringResource(R.string.offline_searching),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            TextButton(onClick = { showRadar = !showRadar }) {
                Text(if (showRadar) stringResource(R.string.offline_list_tab) else stringResource(R.string.offline_radar_tab))
            }
        }

        Text(
            text = stringResource(R.string.offline_range_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        if (connectionState == ConnectionState.CONNECTING) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.offline_connecting), modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (showRadar) {
            RadarView(devices = devices, onDeviceClick = { viewModel.connectTo(it) })
        } else {
            DeviceList(devices = devices, onDeviceClick = { viewModel.connectTo(it) })
        }
    }
}

@Composable
private fun DeviceList(devices: List<NearbyDevice>, onDeviceClick: (NearbyDevice) -> Unit) {
    if (devices.isEmpty()) {
        Text(
            text = stringResource(R.string.offline_nobody_nearby),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    } else {
        LazyColumn {
            items(devices, key = { it.endpointId }) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeviceClick(device) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(YodoPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(device.displayName.take(1).uppercase(), color = YodoPrimary)
                    }
                    Text(
                        text = device.displayName,
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedChatContent(viewModel: OfflineChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val meshNodes by viewModel.meshNodes.collectAsState()
    val neighborCount by viewModel.neighborCount.collectAsState()
    val myShortId by viewModel.myShortId.collectAsState()
    val selectedTargetNodeId by viewModel.selectedTargetNodeId.collectAsState()
    val selectedNode = meshNodes.firstOrNull { it.nodeId == selectedTargetNodeId }
    var inputText by remember { mutableStateOf("") }
    var mediaError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val images = withContext(Dispatchers.Default) {
                uris.take(6).mapNotNull {
                    ImageUtils.compressChatImageToBase64(context, it, ChatImageQuality.DATA_SAVER)
                }
            }
            mediaError = when {
                images.isEmpty() -> "Не удалось обработать фото"
                viewModel.sendPhotos(images) -> null
                else -> "Медиа слишком большое для офлайн-сети (максимум 3 МБ)"
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                runCatching {
                    val size = context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        it.statSize
                    } ?: -1L
                    if (size < 0 || size > MAX_OFFLINE_MEDIA_BYTES) return@runCatching null
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    retriever.release()
                    Base64.encodeToString(bytes, Base64.NO_WRAP) to duration
                }.getOrNull()
            }
            mediaError = when {
                prepared == null -> "Не удалось прочитать аудио"
                viewModel.sendAudio(prepared.first, prepared.second) -> null
                else -> "Аудио слишком большое для офлайн-сети (максимум 3 МБ)"
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MeshInfoBar(
            myShortId = myShortId,
            neighborCount = neighborCount,
            nodes = meshNodes,
            selectedNodeId = selectedTargetNodeId,
            onSelectTarget = { viewModel.selectTarget(it) },
            onSelectByShort = { viewModel.selectTargetByShort(it) }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                OfflineMessageBubble(message)
            }
        }

        // Чип текущего адресата личного сообщения.
        if (selectedNode != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(YodoPrimary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔒 Лично: ${selectedNode.name} (#${selectedNode.shortId})",
                    style = MaterialTheme.typography.labelLarge,
                    color = YodoPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.selectTarget(null) }) { Text("Писать всем") }
            }
        }

        mediaError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { photoPicker.launch("image/*") }) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Отправить фото или альбом", tint = YodoPrimary)
            }
            IconButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Filled.Audiotrack, contentDescription = "Отправить аудио", tint = YodoPrimary)
            }
            // НОВОЕ (батч 7): кнопка SOS — экстренный сигнал на всю mesh-сеть.
            IconButton(onClick = { viewModel.sendSos(inputText); inputText = "" }) {
                Icon(Icons.Filled.Warning, contentDescription = "Отправить SOS", tint = Color(0xFFD32F2F))
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        if (selectedNode != null) "Личное сообщение для #${selectedNode.shortId}"
                        else stringResource(R.string.offline_message_placeholder)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.offline_send_cd), tint = YodoPrimary)
            }
        }

        Button(
            onClick = { viewModel.disconnect() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(stringResource(R.string.offline_disconnect))
        }
    }
}

@Composable
private fun OfflineMessageBubble(message: OfflineMessage) {
    val bubbleColor = if (message.isOutgoing) YodoPrimary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Имя (и номер) отправителя для входящих mesh-сообщений.
            if (!message.isOutgoing && !message.senderName.isNullOrBlank()) {
                val senderLabel = if (!message.senderShort.isNullOrBlank()) {
                    message.senderName + "  #" + message.senderShort
                } else {
                    message.senderName
                }
                Text(
                    text = if (!message.isBroadcast) "🔒 " + senderLabel else senderLabel,
                    color = YodoPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OfflineMediaContent(message, textColor)
            if (message.text.isNotBlank()) {
                Text(text = message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                // Сколько прыжков прошло сообщение через сеть.
                if (!message.isOutgoing && message.hops > 0) {
                    Text(
                        text = "🔗 " + hopsLabel(message.hops) + "  ·  ",
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium
                )
                // Статус доставки для исходящих личных сообщений.
                if (message.isOutgoing && !message.isBroadcast) {
                    Text(
                        text = if (message.delivered) "  ✓✓" else "  ✓",
                        color = textColor.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineMediaContent(message: OfflineMessage, contentColor: Color) {
    when (message.mediaType) {
        OfflineMediaType.PHOTO, OfflineMediaType.ALBUM -> {
            val columns = if (message.mediaItemsBase64.size > 1) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                message.mediaItemsBase64.chunked(columns).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowItems.forEach { encoded ->
                            val bitmap = remember(encoded) { ImageUtils.decodeBase64ToBitmap(encoded) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Офлайн-фото",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(if (columns == 1) 240.dp else 116.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
        OfflineMediaType.AUDIO -> OfflineAudioPlayer(message, contentColor)
        null -> Unit
    }
}

@Composable
private fun OfflineAudioPlayer(message: OfflineMessage, contentColor: Color) {
    val context = LocalContext.current
    var player by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    DisposableEffect(message.id) {
        onDispose { player?.release() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            val active = player
            if (active != null && active.isPlaying) {
                active.pause()
                isPlaying = false
            } else {
                val encoded = message.mediaItemsBase64.firstOrNull() ?: return@IconButton
                val file = AudioUtils.base64ToTempFile(context, encoded, "offline_${message.id}")
                    ?: return@IconButton
                val mediaPlayer = active ?: MediaPlayer().also {
                    it.setDataSource(file.absolutePath)
                    it.prepare()
                    it.setOnCompletionListener { completed ->
                        isPlaying = false
                        completed.seekTo(0)
                    }
                    player = it
                }
                mediaPlayer.start()
                isPlaying = true
            }
        }) {
            Icon(
                if (isPlaying) Icons.Filled.Audiotrack else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести аудио",
                tint = contentColor
            )
        }
        Text(AudioUtils.formatDuration(message.audioDurationMs), color = contentColor)
    }
}

/** Склонение слова «прыжок» для числа прыжков. */
private fun hopsLabel(hops: Int): String {
    val n = hops % 100
    if (n in 11..14) return "$hops прыжков"
    return when (n % 10) {
        1 -> "$hops прыжок"
        2, 3, 4 -> "$hops прыжка"
        else -> "$hops прыжков"
    }
}

/**
 * НОВОЕ (mesh). Панель состояния ячеистой сети: сколько прямых соседей,
 * сколько всего узлов достижимо и через сколько прыжков.
 */
@Composable
private fun MeshInfoBar(
    myShortId: String,
    neighborCount: Int,
    nodes: List<MeshNode>,
    selectedNodeId: String?,
    onSelectTarget: (String?) -> Unit,
    onSelectByShort: (String) -> MeshNode?
) {
    var expanded by remember { mutableStateOf(true) }
    var numberQuery by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Шапка: мой номер + сводка по сети.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(YodoPrimary.copy(alpha = 0.10f))
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(YodoPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Hub, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = "Ваш номер",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "#$myShortId",
                    style = MaterialTheme.typography.titleLarge,
                    color = YodoPrimary
                )
                Text(
                    text = "Соседей: $neighborCount · Всего узлов: ${nodes.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Скрыть" else "Узлы")
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            // Поиск собеседника по номеру.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = numberQuery,
                    onValueChange = {
                        numberQuery = it.filter { c -> c.isDigit() }.take(6)
                        notFound = false
                    },
                    singleLine = true,
                    placeholder = { Text("Номер друга, напр. 123456") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val node = onSelectByShort(numberQuery)
                        if (node == null) notFound = true else numberQuery = ""
                    },
                    enabled = numberQuery.length == 6
                ) {
                    Text("Найти")
                }
            }
            if (notFound) {
                Text(
                    text = "Узел с таким номером пока не виден в сети",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFDC2626),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (nodes.isEmpty()) {
                Text(
                    text = "Пока никого не видно. Попросите друга открыть офлайн-чат.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                nodes.forEach { node ->
                    MeshNodeCard(
                        node = node,
                        selected = node.nodeId == selectedNodeId,
                        onWrite = { onSelectTarget(node.nodeId) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/** Красивая карточка узла mesh-сети в списке «Узлы». */
@Composable
private fun MeshNodeCard(node: MeshNode, selected: Boolean, onWrite: () -> Unit) {
    val bg = if (selected) YodoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onWrite() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (node.isNeighbor) YodoPrimary.copy(alpha = 0.20f)
                    else Color(0xFFF59E0B).copy(alpha = 0.20f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = node.name.take(1).uppercase().ifBlank { "?" },
                color = if (node.isNeighbor) YodoPrimary else Color(0xFFB45309),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                text = node.name.ifBlank { "Узел" },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (node.shortId.isBlank()) "№ неизвестен" else "#${node.shortId}",
                style = MaterialTheme.typography.labelMedium,
                color = YodoPrimary
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (node.isNeighbor) Color(0xFF22C55E).copy(alpha = 0.18f)
                    else Color(0xFFF59E0B).copy(alpha = 0.18f)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (node.isNeighbor) "рядом" else hopsLabel(node.hopCount),
                style = MaterialTheme.typography.labelMedium,
                color = if (node.isNeighbor) Color(0xFF15803D) else Color(0xFFB45309)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(onClick = onWrite) { Text("Написать") }
    }
}

@Composable
private fun RadarView(devices: List<NearbyDevice>, onDeviceClick: (NearbyDevice) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing)),
        label = "sweep_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2

            for (ring in 1..3) {
                drawCircle(
                    color = YodoPrimary.copy(alpha = 0.2f),
                    radius = maxRadius * ring / 3,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, YodoPrimary.copy(alpha = 0.35f))
                ),
                startAngle = sweepAngle,
                sweepAngle = 60f,
                useCenter = true,
                topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                size = Size(maxRadius * 2, maxRadius * 2)
            )

            drawCircle(color = YodoPrimary, radius = 14f, center = center)

            devices.forEachIndexed { index, _ ->
                val angle = (360f / devices.size.coerceAtLeast(1)) * index
                val angleRad = Math.toRadians(angle.toDouble())
                val ringRadius = maxRadius * 2 / 3
                val x = center.x + ringRadius * kotlin.math.cos(angleRad).toFloat()
                val y = center.y + ringRadius * kotlin.math.sin(angleRad).toFloat()
                drawCircle(color = Color(0xFF22C55E), radius = 18f, center = Offset(x, y))
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val maxRadiusPx = with(LocalDensity.current) { maxWidth.toPx() / 2 }
            devices.forEachIndexed { index, device ->
                val angle = (360f / devices.size.coerceAtLeast(1)) * index
                val angleRad = Math.toRadians(angle.toDouble())
                val ringRadius = maxRadiusPx * 2 / 3
                val xDp = with(LocalDensity.current) { (ringRadius * kotlin.math.cos(angleRad).toFloat()).toDp() }
                val yDp = with(LocalDensity.current) { (ringRadius * kotlin.math.sin(angleRad).toFloat()).toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = maxWidth / 2 + xDp - 24.dp, y = maxWidth / 2 + yDp - 24.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onDeviceClick(device) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = device.displayName.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            Text(
                text = stringResource(R.string.offline_scanning),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
