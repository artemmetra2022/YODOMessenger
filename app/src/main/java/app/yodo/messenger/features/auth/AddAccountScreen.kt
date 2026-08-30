package app.yodo.messenger.features.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * НОВОЕ (Y): отдельное окно добавления аккаунта — компактный экран со вкладками
 * «Вход» / «Регистрация», визуально отличающийся от обычного экрана входа.
 * После успеха аккаунт сохраняется и становится текущим.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // НОВОЕ (баг 17): генератор и проверка пароля при добавлении второго аккаунта —
    // те же компоненты, что и в основной регистрации (PasswordStrength.kt/Indicator.kt).
    val passwordStrength = remember(password) { evaluatePasswordStrength(password) }
    val isPasswordAcceptable = passwordStrength.level == PasswordStrengthLevel.STRONG

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            viewModel.resetState()
            onAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить аккаунт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Отличающийся градиентный баннер — чтобы окно отличалось от обычного входа.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(40.dp)
                    )
                    Text(
                        "Новый аккаунт в YodoMessenger",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isRegister,
                    onClick = { isRegister = false; viewModel.resetState() },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Вход") }
                SegmentedButton(
                    selected = isRegister,
                    onClick = { isRegister = true; viewModel.resetState() },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Регистрация") }
            }
            Spacer(Modifier.height(16.dp))

            if (isRegister) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Имя") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text(if (isRegister) "Email" else "Email или username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Пароль") }, singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    // НОВОЕ (баг 17): генератор надёжного пароля + переключатель видимости —
                    // как в основной регистрации.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { password = generateStrongPassword(); passwordVisible = true }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Сгенерировать надёжный пароль")
                        }
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // НОВОЕ (баг 17): индикатор надёжности с чек-листом — только в режиме регистрации.
            if (isRegister && password.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                PasswordStrengthIndicator(result = passwordStrength)
            }

            (uiState as? AuthUiState.Error)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (isRegister) viewModel.register(name, username, email, password)
                    else viewModel.login(email, password)
                },
                // НОВОЕ (баг 17): при регистрации второго аккаунта кнопка активна только
                // с надёжным паролем (STRONG) — та же политика, что в основной регистрации.
                enabled = uiState !is AuthUiState.Loading &&
                    (!isRegister || isPasswordAcceptable),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState is AuthUiState.Loading) CircularProgressIndicator(modifier = Modifier.height(22.dp))
                else Text(if (isRegister) "Зарегистрироваться и войти" else "Войти в аккаунт")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Отмена") }
        }
    }
}
