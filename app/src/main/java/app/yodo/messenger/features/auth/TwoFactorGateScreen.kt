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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError

/**
 * Показывается сразу после успешного входа (email/username, телефон или Google),
 * если у пользователя включена двухэтапная аутентификация (облачный пароль, как в Telegram).
 * Пропускает пользователя в приложение только после верного пароля.
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
        is TwoFactorGateUiState.AwaitingPassword -> {
            var password by remember { mutableStateOf("") }

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
                        text = stringResource(R.string.two_factor_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.two_factor_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.two_factor_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = state.error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, color = YodoError, style = MaterialTheme.typography.bodySmall)
                    }
                    state.hint?.takeIf { it.isNotBlank() }?.let { hint ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.two_factor_hint_prefix, hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.verify(password) },
                        enabled = password.isNotBlank() && !state.isVerifying,
                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.two_factor_continue))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
