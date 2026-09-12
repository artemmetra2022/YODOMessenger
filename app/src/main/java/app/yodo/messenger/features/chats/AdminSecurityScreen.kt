package app.yodo.messenger.features.chats

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.launch

@Composable
fun AdminSecurityScreen(
    onBack: () -> Unit,
    viewModel: AdminSecurityViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var code by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Безопасность админа") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("Двухфакторная аутентификация (TOTP)", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Подключите Google Authenticator, Aegis, Microsoft Authenticator или другое TOTP-приложение.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                Text(if (state.enabled) "2FA включена" else "2FA выключена")
            }
            if (!state.enabled) {
                item {
                    Button(onClick = {
                        scope.launch {
                            viewModel.beginSetup().onSuccess { secret = it }
                                .onFailure { message = it.message }
                        }
                    }) { Text("Создать QR-код") }
                }
                secret?.let { value ->
                    item {
                        val qr = remember(value) { makeQr(value) }
                        Image(bitmap = qr.asImageBitmap(), contentDescription = "QR-код", modifier = Modifier.size(230.dp))
                    }
                    item {
                        Text("Если QR не сканируется, введите этот ключ вручную:", style = MaterialTheme.typography.bodyMedium)
                        SelectionContainer { Text(value) }
                    }
                    item {
                        OutlinedTextField(
                            value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                            label = { Text("Код из приложения") }, singleLine = true
                        )
                    }
                    item {
                        Button(enabled = code.length == 6, onClick = {
                            scope.launch {
                                viewModel.enable(code).onSuccess {
                                    secret = null; code = ""; message = "2FA успешно включена"
                                }.onFailure { message = it.message }
                            }
                        }) { Text("Включить 2FA") }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("Текущий код для отключения") }, singleLine = true
                    )
                }
                item {
                    Button(enabled = code.length == 6, onClick = {
                        scope.launch {
                            viewModel.disable(code).onSuccess {
                                code = ""; message = "2FA отключена"
                            }.onFailure { message = it.message }
                        }
                    }) { Text("Отключить 2FA") }
                }
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

private fun makeQr(secret: String): Bitmap {
    val issuer = "YODO Messenger"
    val account = "admin@yodo"
    val uri = "otpauth://totp/${java.net.URLEncoder.encode(issuer, "UTF-8")}:${java.net.URLEncoder.encode(account, "UTF-8")}" +
        "?secret=$secret&issuer=${java.net.URLEncoder.encode(issuer, "UTF-8")}&algorithm=SHA1&digits=6&period=30"
    val matrix = MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, 640, 640)
    val bmp = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
    for (x in 0 until 640) for (y in 0 until 640) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}
