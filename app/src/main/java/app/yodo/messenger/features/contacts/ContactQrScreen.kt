package app.yodo.messenger.features.contacts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.util.QrCardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * НОВОЕ (поделиться контактом абонента): экран QR-карточки контакта
 * собеседника. Кнопка «Поделиться контактом» в чате ведёт сюда и
 * делится контактом собеседника, а не своим.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactQrScreen(
    onBackClick: () -> Unit,
    viewModel: ContactQrViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current
    val context = LocalContext.current

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.user) {
        val user = uiState.user ?: return@LaunchedEffect
        isGenerating = true
        val bitmap = withContext(Dispatchers.IO) {
            QrCardGenerator.generate(
                content = buildContactQrContent(user),
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
                title = { Text("Контакт собеседника") },
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
                uiState.isLoading || isGenerating ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.notFound || uiState.user == null -> Text(
                    "Не удалось загрузить контакт",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center
                )
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Отсканируйте этот код в YODO, чтобы добавить контакт",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR контакта",
                                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { qrBitmap?.let { shareContactQrBitmap(context, it) } },
                            colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Поделиться", modifier = Modifier.padding(start = 8.dp), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// НОВОЕ (поделиться контактом абонента): тот же формат, что и в QrCodeScreen
// (yodo://c/<base64url(json{v,uid,n,u,pk})>) — чтобы сканер распознавал его офлайн.
private fun buildContactQrContent(user: YodoUser): String {
    val json = JSONObject().apply {
        put("v", 1)
        put("uid", user.uid)
        put("n", user.displayName)
        user.username?.takeIf { it.isNotBlank() }?.let { put("u", it) }
        user.publicKey?.takeIf { it.isNotBlank() }?.let { put("pk", it) }
    }
    val encoded = Base64.encodeToString(
        json.toString().toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE
    )
    return "yodo://c/$encoded"
}

private fun shareContactQrBitmap(context: Context, bitmap: Bitmap) {
    try {
        val dir = File(context.cacheDir, "shared_images")
        dir.mkdirs()
        val file = File(dir, "yodo_contact_qr.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться контактом"))
    } catch (e: Exception) {
        android.util.Log.e("ContactQrScreen", "share failed", e)
    }
}
