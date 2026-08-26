package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun TwoFactorScreen(
    onBackClick: () -> Unit,
    viewModel: TwoFactorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        (uiState.errorMessage ?: uiState.successMessage)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.two_factor_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colorTheme.primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colorTheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (uiState.state.enabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = colorTheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (uiState.state.enabled)
                    stringResource(R.string.two_factor_settings_enabled)
                else
                    stringResource(R.string.two_factor_settings_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (uiState.state.enabled) {
                EnabledContent(
                    uiState = uiState,
                    onStartDisable = viewModel::startDisable,
                    onConfirmDisable = viewModel::confirmDisable,
                    onCancelDisable = viewModel::cancelDisable
                )
            } else {
                OutlinedButton(
                    onClick = viewModel::enable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.two_factor_settings_enable_btn))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EnabledContent(
    uiState: TwoFactorUiState,
    onStartDisable: () -> Unit,
    onConfirmDisable: (String) -> Unit,
    onCancelDisable: () -> Unit
) {
    val colorTheme = LocalColorTheme.current

    if (!uiState.awaitingDisableCode) {
        OutlinedButton(
            onClick = onStartDisable,
            enabled = !uiState.isSendingCode,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = YodoError),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isSendingCode) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = YodoError)
            } else {
                Text(stringResource(R.string.two_factor_settings_disable_btn))
            }
        }
        return
    }

    var code by remember { mutableStateOf("") }

    Text(
        text = stringResource(R.string.two_factor_email_subtitle, uiState.maskedEmail.orEmpty()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    OutlinedTextField(
        value = code,
        onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) code = new },
        label = { Text(stringResource(R.string.two_factor_email_code_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = { onConfirmDisable(code) },
        enabled = code.length == 6,
        colors = ButtonDefaults.buttonColors(containerColor = YodoError),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.two_factor_settings_disable_password_btn))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = onCancelDisable, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.two_factor_settings_cancel))
    }
}
