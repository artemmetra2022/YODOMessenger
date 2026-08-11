package app.yodo.messenger.features.profile

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.util.QrCardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScreen(
    onBackClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var savedOk by remember { mutableStateOf(false) }

    // Генерируем QR-карточку в фоновом потоке
    LaunchedEffect(uiState.user) {
        val user = uiState.user ?: return@LaunchedEffect
        isGenerating = true
        val bitmap = withContext(Dispatchers.IO) {
            QrCardGenerator.generate(
                content = "yodo://user/${user.uid}",
                displayName = user.displayName.ifBlank { "YODO User" },
                username = user.username,
                primaryArgb = android.graphics.Color.rgb(
                    (colorTheme.primary.red * 255).toInt(),
                    (colorTheme.primary.green * 255).toInt(),
                    (colorTheme.primary.blue * 255).toInt()
                )
            )
        }
        qrBitmap = bitmap
        isGenerating = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR-код") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Пояснение ──────────────────────────────────────────────
            Text(
                text = "Покажите этот код другу — он откроет ваш профиль прямо в YODO",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // ── QR-карточка ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating || qrBitmap == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(40.dp),
                        color = colorTheme.primary
                    )
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR-код профиля",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Ссылка под карточкой ───────────────────────────────────
            uiState.user?.let { user ->
                Text(
                    text = "yodo://user/${user.uid.take(8)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Кнопки действий ────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Сохранить в галерею
                Button(
                    onClick = {
                        val bmp = qrBitmap ?: return@Button
                        scope.launch {
                            isSaving = true
                            savedOk = false
                            val ok = withContext(Dispatchers.IO) {
                                saveBitmapToGallery(context, bmp)
                            }
                            isSaving = false
                            if (ok) {
                                savedOk = true
                                snackbarHostState.showSnackbar("Сохранено в галерею 📷")
                            } else {
                                snackbarHostState.showSnackbar("Не удалось сохранить")
                            }
                        }
                    },
                    enabled = qrBitmap != null && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            if (savedOk) Icons.Filled.Check else Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (savedOk) "Сохранено" else "Сохранить",
                        modifier = Modifier.padding(start = 6.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Поделиться
                FilledTonalButton(
                    onClick = {
                        val bmp = qrBitmap ?: return@FilledTonalButton
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                shareQrBitmap(context, bmp)
                            }
                        }
                    },
                    enabled = qrBitmap != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Поделиться", modifier = Modifier.padding(start = 6.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Утилиты сохранения / шаринга
// ────────────────────────────────────────────────────────────────────────────

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val fileName = "YODO_QR_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — через MediaStore, без разрешений
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YODO")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            // Android 8–9 — прямая запись, разрешение WRITE_EXTERNAL_STORAGE уже в манифесте
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "YODO"
            )
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            true
        }
    } catch (e: Exception) {
        android.util.Log.e("QrCodeScreen", "saveBitmapToGallery failed", e)
        false
    }
}

private fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    try {
        val dir = File(context.cacheDir, "shared_images")
        dir.mkdirs()
        val file = File(dir, "yodo_qr.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Мой профиль в YODO Messenger")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться QR-кодом"))
    } catch (e: Exception) {
        android.util.Log.e("QrCodeScreen", "shareQrBitmap failed", e)
    }
}
