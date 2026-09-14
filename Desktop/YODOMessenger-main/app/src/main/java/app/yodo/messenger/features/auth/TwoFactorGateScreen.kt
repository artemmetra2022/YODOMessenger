package app.yodo.messenger.features.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError

/**
 * Показывается сразу после успешного входа (email/username, телефон или Google),
 * если у пользователя включена 2FA по email. Единственный шаг — ввод 6-значного
 * кода, отправленного на почту, к которой привязан аккаунт. Код отправляется
 * автоматически, как только экран открывается. Пропускает пользователя в
 * приложение только после успешной проверки кода.
 */
@Composable
fun TwoFactorGateScreen(
    onPassed: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: TwoFactorGateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current

    LaunchedEffect(uiState) {
        when (uiState) {
            is TwoFactorGateUiState.NotRequired, is TwoFactorGateUiState.Verified -> onPassed()
            else -> Unit
        }
    }

    when (val state = uiState) {
        is TwoFactorGateUiState.Checking, is TwoFactorGateUiState.NotRequired, is TwoFactorGateUiState.Verified -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorTheme.primary)
            }
        }
        is TwoFactorGateUiState.AwaitingEmailCode -> {
            var code by remember { mutableStateOf("") }

            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.two_factor_email_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.two_factor_email_subtitle, state.maskedEmail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { new ->
                            if (new.length <= 6 && new.all { it.isDigit() }) code = new
                        },
                        label = { Text(stringResource(R.string.two_factor_email_code_label)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        singleLine = true,
                        isError = state.error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, color = YodoError, style = MaterialTheme.typography.bodySmall)
                    }
                    state.infoMessage?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, color = colorTheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.verifyEmailCode(code) },
                        enabled = code.length == 6 && !state.isVerifying,
                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.two_factor_email_continue))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.resendEmailCode() },
                        enabled = !state.isResending
                    ) {
                        Text(stringResource(R.string.two_factor_email_resend))
                    }
                    TextButton(onClick = {
                        viewModel.cancelAndLogout()
                        onCancelled()
                    }) {
                        Text(stringResource(R.string.two_factor_logout))
                    }
                }
            }
        }
    }
}
