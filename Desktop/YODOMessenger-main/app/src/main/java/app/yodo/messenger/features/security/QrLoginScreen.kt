package app.yodo.messenger.features.security

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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
 * НОВОЕ (вход по QR-коду): экран сканирования QR, показанного на веб-версии
 * (страница входа → «Войти по QR-коду»). После скана требует явного подтверждения
 * пользователя, затем передаёт e2e-зашифрованные учётные данные через Firestore —
 * см. QrLoginViewModel и firestore.rules (match /qrLogins).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrLoginScreen(
    onBackClick: () -> Unit,
    viewModel: QrLoginViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()

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

    // Автоматически закрываем экран через небольшую паузу после успешного входа.
    LaunchedEffect(state) {
        if (state is QrLoginState.Success) {
            kotlinx.coroutines.delay(1500)
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Вход по QR-коду") },
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
                        Icons.Filled.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colorTheme.primary
                    )
                    Spacer()
                    Text(
                        "Для сканирования QR-кода нужен доступ к камере",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer()
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary)
                    ) { Text("Разрешить доступ") }
                }
            } else {
                val showCamera = state is QrLoginState.Scanning
                if (showCamera) {
                    CameraPreview(
                        onQrDetected = { viewModel.onQrScanned(it) },
                        lifecycleOwner = lifecycleOwner
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(240.dp)
                            .border(3.dp, colorTheme.primary, RoundedCornerShape(20.dp))
                    )
                    Text(
                        "Наведите камеру на QR-код на экране сайта YODO",
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
                    is QrLoginState.Approving -> StatusCard(
                        modifier = Modifier.align(Alignment.Center),
                        content = {
                            CircularProgressIndicator(color = colorTheme.primary)
                            Spacer()
                            Text("Подтверждаем вход…", style = MaterialTheme.typography.bodyMedium)
                        }
                    )
                    is QrLoginState.Success -> StatusCard(
                        modifier = Modifier.align(Alignment.Center),
                        content = {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = colorTheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer()
                            Text(
                                "Готово! На сайте выполнен вход",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                    is QrLoginState.Error -> StatusCard(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        content = {
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer()
                            OutlinedButton(onClick = { viewModel.resumeScanning() }) {
                                Text("Сканировать ещё раз")
                            }
                        }
                    )
                    else -> Unit
                }
            }
        }
    }

    val confirmState = state
    if (confirmState is QrLoginState.AwaitingConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelConfirmation() },
            icon = { Icon(Icons.Filled.Computer, contentDescription = null) },
            title = { Text("Войти в аккаунт на сайте?") },
            text = {
                Text(
                    "Кто-то показывает QR-код для входа в YODO на компьютере. Если это " +
                        "вы — подтвердите вход. Если нет, отмените: кто-то другой пытается " +
                        "войти в ваш аккаунт."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmLogin() },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalColorTheme.current.primary)
                ) { Text("Это я, войти") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelConfirmation() }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun Spacer() = androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
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
                analysis.setAnalyzer(executor, QrLoginAnalyzer(onQrDetected))
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
private class QrLoginAnalyzer(
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
