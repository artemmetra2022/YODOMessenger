package app.yodo.messenger.offline

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.YodoPrimary
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) viewModel.startSearching()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions())
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnect() }
    }

    val connectionState by viewModel.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Офлайн-чат (Bluetooth)", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissionsGranted -> {
                    Text(
                        text = "Нужны разрешения на Bluetooth и геолокацию (обязательна для поиска устройств поблизости на Android — данные никуда не отправляются).",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                connectionState == ConnectionState.CONNECTED -> {
                    ConnectedChatContent(viewModel)
                }

                else -> {
                    DeviceDiscoveryContent(viewModel, connectionState)
                }
            }
        }
    }
}

@Composable
private fun DeviceDiscoveryContent(viewModel: OfflineChatViewModel, connectionState: ConnectionState) {
    val devices by viewModel.discoveredDevices.collectAsState()
    var showRadar by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BluetoothSearching, contentDescription = null, tint = YodoPrimary)
            Text(
                text = "Ищем устройства поблизости...",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            TextButton(onClick = { showRadar = !showRadar }) {
                Text(if (showRadar) "Список" else "Радар")
            }
        }

        Text(
            text = "Работает без интернета — только с устройствами в радиусе Bluetooth/Wi-Fi (обычно 10-100 метров), у которых тоже открыт этот экран.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        if (connectionState == ConnectionState.CONNECTING) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Подключение...", modifier = Modifier.padding(start = 8.dp))
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
            text = "Пока никого не найдено рядом.",
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
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF22C55E)))
            Text(
                text = "Подключено: ${connectedDeviceName ?: "собеседник"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                OfflineMessageBubble(message)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Сообщение...") },
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
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = YodoPrimary)
            }
        }

        Button(
            onClick = { viewModel.disconnect() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Отключиться")
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
            Text(text = message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                color = textColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun RadarView(devices: List<NearbyDevice>, onDeviceClick: (NearbyDevice) -> Unit) {
    // Развёртка радара — бесконечное вращение линии по кругу для "живого" ощущения сканирования
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

            // Концентрические кольца радара
            for (ring in 1..3) {
                drawCircle(
                    color = YodoPrimary.copy(alpha = 0.2f),
                    radius = maxRadius * ring / 3,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            // Сектор развёртки (полупрозрачный "луч", вращающийся по кругу)
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

            // "Я" — в центре
            drawCircle(color = YodoPrimary, radius = 14f, center = center)

            // Устройства — равномерно по кругу на среднем кольце (нет реального RSSI-расстояния
            // от Nearby Connections API, поэтому размещение честно условное, не физически точное)
            devices.forEachIndexed { index, _ ->
                val angle = (360f / devices.size.coerceAtLeast(1)) * index
                val angleRad = Math.toRadians(angle.toDouble())
                val ringRadius = maxRadius * 2 / 3
                val x = center.x + ringRadius * kotlin.math.cos(angleRad).toFloat()
                val y = center.y + ringRadius * kotlin.math.sin(angleRad).toFloat()
                drawCircle(color = Color(0xFF22C55E), radius = 18f, center = Offset(x, y))
            }
        }

        // Кликабельные точки поверх Canvas (сам Canvas не кликабелен по отдельным нарисованным фигурам)
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
                text = "Сканирую эфир...",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
