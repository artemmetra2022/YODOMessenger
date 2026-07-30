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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                title = { Text("Двухэтапная аутентификация") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                    "Дополнительный пароль включён. Он потребуется каждый раз при входе в аккаунт на новом устройстве."
                else
                    "Установите дополнительный пароль. Он будет запрашиваться при входе в аккаунт — так же, как в Telegram — и защитит доступ, даже если кто-то узнает пароль от почты.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (uiState.state.enabled) {
                EnabledContent(uiState = uiState, onChange = viewModel::changePassword, onDisable = viewModel::disable)
            } else {
                SetupContent(onEnable = viewModel::enablePassword)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupContent(onEnable: (String, String, String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    val colorTheme = LocalColorTheme.current

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Новый пароль") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it },
        label = { Text("Повторите пароль") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = hint,
        onValueChange = { hint = it },
        label = { Text("Подсказка (необязательно)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = { onEnable(password, confirmPassword, hint.takeIf { it.isNotBlank() }) },
        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Включить пароль")
    }
}

@Composable
private fun EnabledContent(
    uiState: TwoFactorUiState,
    onChange: (String, String, String, String?) -> Unit,
    onDisable: (String) -> Unit
) {
    val colorTheme = LocalColorTheme.current
    var mode by remember { mutableStateOf(EnabledMode.NONE) }

    uiState.state.hint?.let { hint ->
        Text(
            text = "Подсказка: $hint",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    if (mode == EnabledMode.NONE) {
        OutlinedButton(onClick = { mode = EnabledMode.CHANGE }, modifier = Modifier.fillMaxWidth()) {
            Text("Изменить пароль")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { mode = EnabledMode.DISABLE },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = YodoError),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отключить")
        }
        return
    }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    OutlinedTextField(
        value = currentPassword,
        onValueChange = { currentPassword = it },
        label = { Text("Текущий пароль") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    if (mode == EnabledMode.CHANGE) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Новый пароль") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Повторите новый пароль") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = hint,
            onValueChange = { hint = it },
            label = { Text("Подсказка (необязательно)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Button(
        onClick = {
            if (mode == EnabledMode.CHANGE) {
                onChange(currentPassword, newPassword, confirmPassword, hint.takeIf { it.isNotBlank() })
            } else {
                onDisable(currentPassword)
            }
            mode = EnabledMode.NONE
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (mode == EnabledMode.DISABLE) YodoError else colorTheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (mode == EnabledMode.CHANGE) "Сохранить" else "Отключить пароль")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = { mode = EnabledMode.NONE }, modifier = Modifier.fillMaxWidth()) {
        Text("Отмена")
    }
}

private enum class EnabledMode { NONE, CHANGE, DISABLE }
