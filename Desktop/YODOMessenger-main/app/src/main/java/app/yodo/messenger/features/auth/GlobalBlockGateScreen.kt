package app.yodo.messenger.features.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.util.ImageUtils

/**
 * НОВОЕ (AD): экран «Вы заблокированы». Показывается поверх всего приложения,
 * когда админ глобально заблокировал аккаунт. Доступны только обжалование и выход.
 */
@Composable
fun GlobalBlockGateScreen(
    viewModel: GlobalBlockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val block by viewModel.globalBlock.collectAsState()
    val sending by viewModel.appealSending.collectAsState()
    val sent by viewModel.appealSent.collectAsState()
    val error by viewModel.appealError.collectAsState()

    var showAppeal by remember { mutableStateOf(false) }
    var appealText by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoBase64 = ImageUtils.compressChatImageToBase64(context, uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Block,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Вы заблокированы",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        val reason = block?.reason?.takeIf { it.isNotBlank() } ?: "Администрация заблокировала ваш аккаунт."
        Text(
            "Причина: $reason",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        if (sent) {
            Text(
                "Обжалование отправлено. Ожидайте решения администрации.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (!showAppeal) {
            Button(onClick = { showAppeal = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Обжалование")
            }
        } else {
            OutlinedTextField(
                value = appealText,
                onValueChange = { appealText = it },
                label = { Text("Текст обжалования") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            Spacer(Modifier.height(8.dp))
            photoBase64?.let { b64 ->
                val bmp = remember(b64) { ImageUtils.decodeBase64ToBitmap(b64) }
                bmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (photoBase64 == null) "Фото" else "Заменить")
                }
                Button(
                    onClick = { viewModel.sendAppeal(appealText, photoBase64) },
                    enabled = !sending && (appealText.isNotBlank() || photoBase64 != null),
                    modifier = Modifier.weight(1f)
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Отправить")
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { viewModel.logout() }) { Text("Выйти из аккаунта") }
    }
    }
}
