package app.yodo.messenger.features.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.yodo.messenger.ui.theme.LocalColorTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * НОВОЕ (офлайн обмен контактами по QR).
 * Экран сканирования QR-кода контакта: камера через CameraX + декодирование ZXing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContactScreen(
    onBackClick: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: ScanContactViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val state by viewModel.state.collectAsState()
    val openedChatId by viewModel.openedChatId.collectAsState()
    val isOpeningChat by viewModel.isOpeningChat.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Переход в чат после успешного создания/открытия чата.
    LaunchedEffect(openedChatId) {
        openedChatId?.let {
            viewModel.consumeOpenedChat()
            onOpenChat(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сканировать QR") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colorTheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Для сканирования QR-кода нужен доступ к камере",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary)
                    ) { Text("Разрешить доступ") }
                }
            } else {
                // Камера активна только пока сканируем.
                if (state is ScanState.Scanning) {
                    CameraPreview(
                        onQrDetected = { viewModel.onQrScanned(it) },
                        lifecycleOwner = lifecycleOwner
                    )
                    // Рамка-визир по центру
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(240.dp)
                            .border(3.dp, colorTheme.primary, RoundedCornerShape(20.dp))
                    )
                    Text(
                        "Наведите камеру на QR-код контакта YODO",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                when (val s = state) {
                    is ScanState.Success -> ResultCard(
                        name = s.contact.displayName.ifBlank { "Пользователь YODO" },
                        username = s.contact.username,
                        hasKey = s.contact.publicKey != null,
                        savedOffline = s.savedOffline,
                        isOpeningChat = isOpeningChat,
                        onWrite = { viewModel.openChat(s.contact.uid) },
                        onScanMore = { viewModel.resumeScanning() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    is ScanState.Error -> ResultCard(
                        name = null,
                        username = null,
                        hasKey = false,
                        savedOffline = false,
                        isOpeningChat = false,
                        errorMessage = s.message,
                        onWrite = null,
                        onScanMore = { viewModel.resumeScanning() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    ScanState.Scanning -> Unit
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    name: String?,
    username: String?,
    hasKey: Boolean,
    savedOffline: Boolean,
    isOpeningChat: Boolean,
    errorMessage: String? = null,
    onWrite: (() -> Unit)?,
    onScanMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorTheme = LocalColorTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorMessage != null) {
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        } else {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colorTheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                name ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!username.isNullOrBlank()) {
                Text(
                    "@$username",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (savedOffline) "Контакт добавлен офлайн" else "Контакт добавлен",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasKey) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colorTheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Ключ шифрования получен",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorTheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onScanMore, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Сканировать ещё", modifier = Modifier.padding(start = 6.dp))
            }
            if (onWrite != null) {
                Button(
                    onClick = onWrite,
                    enabled = !isOpeningChat,
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isOpeningChat) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Написать", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onQrDetected: (String) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, QrCodeAnalyzer(onQrDetected))
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

/** Анализатор кадров CameraX: декодирует QR из Y-плоскости (яркость) через ZXing. */
private class QrCodeAnalyzer(
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val rowStride = plane.rowStride
            val source = PlanarYUVLuminanceSource(
                bytes,
                rowStride,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            if (result != null && result.text.isNotBlank()) {
                onQrDetected(result.text)
            }
        } catch (_: Exception) {
            // Кадр без QR — нормально, просто ждём следующий.
        } finally {
            reader.reset()
            image.close()
        }
    }
}
