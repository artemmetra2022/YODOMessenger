package app.yodo.messenger.features.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.repository.TwoFactorEmailSendResult
import app.yodo.messenger.ui.theme.LocalColorTheme
import kotlinx.coroutines.launch

/**
 * Batch 7: шлюз двухфакторной аутентификации при запуске приложения (см.
 * MainActivity). Единственный шаг — 6-значный код, который приходит на
 * почту, к которой привязан аккаунт; код отправляется автоматически, как
 * только открывается этот экран. Второй пароль и контрольные вопросы
 * убраны — это не PIN-блокировка приложения (та отдельно, см.
 * PinLockScreen), а именно 2FA-шаг из «Центра безопасности».
 */
@Composable
fun AppTwoFactorGateScreen(
    onUnlocked: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val scope = rememberCoroutineScope()

    var maskedEmail by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    suspend fun requestCode(isResend: Boolean) {
        when (val result = viewModel.sendLoginCode()) {
            is TwoFactorEmailSendResult.Success -> {
                maskedEmail = result.maskedEmail
                error = null
                infoMessage = if (isResend) "Мы отправили новый код" else null
            }
            is TwoFactorEmailSendResult.Error -> error = result.message
        }
    }

    LaunchedEffect(Unit) { requestCode(isResend = false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Text("У вас включена двухфакторная аутентификация 🔒", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (maskedEmail != null) "Мы отправили код на почту: $maskedEmail. Введите его тут" else "Отправляем код на вашу почту…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) { code = new; error = null } },
                label = { Text("Код из письма") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            infoMessage?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = colorTheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    isVerifying = true
                    scope.launch {
                        val ok = viewModel.verifyLoginCode(code)
                        isVerifying = false
                        if (ok) onUnlocked() else error = "Неверный или устаревший код"
                    }
                },
                enabled = code.length == 6 && !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Подтвердить")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    isResending = true
                    scope.launch {
                        requestCode(isResend = true)
                        isResending = false
                    }
                },
                enabled = !isResending
            ) { Text("Отправить код ещё раз") }
        }
    }
}
