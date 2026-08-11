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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.PinCheckResult
import app.yodo.messenger.ui.theme.LocalColorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Batch 7: локальный шлюз двухфакторной аутентификации. Показывается
 * при запуске приложения (см. MainActivity), если установлен второй пароль.
 * Поддерживает сброс пароля через 3 контрольных вопроса.
 */
@Composable
fun AppTwoFactorGateScreen(
    onUnlocked: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val scope = rememberCoroutineScope()
    val hint by viewModel.twoFactorHint.collectAsState(initial = "")
    val questions by viewModel.recoveryQuestions.collectAsState(initial = listOf("", "", ""))
    val isRecoverySet by viewModel.isRecoverySet.collectAsState(initial = false)

    var recoveryMode by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableStateOf(0L) }
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            remainingSeconds = (lockedUntil - System.currentTimeMillis()) / 1000 + 1
            delay(1000)
        }
        remainingSeconds = 0
    }

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

            if (!recoveryMode) {
                Text("Двухфакторная аутентификация", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Введите второй пароль, чтобы войти",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Подсказка: $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (remainingSeconds > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Слишком много попыток. Повтор через $remainingSeconds с", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        val toCheck = password
                        password = ""
                        scope.launch {
                            when (val r = viewModel.verifyTwoFactor(toCheck)) {
                                is PinCheckResult.Success -> onUnlocked()
                                is PinCheckResult.WrongPin -> error = "Неверный пароль. Осталось попыток: ${r.attemptsRemaining}"
                                is PinCheckResult.LockedOut -> { lockedUntil = r.unlockAtMillis; error = null }
                            }
                        }
                    },
                    enabled = password.isNotBlank() && remainingSeconds <= 0L,
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Войти") }
                if (isRecoverySet) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { recoveryMode = true; error = null }) { Text("Забыли пароль?") }
                }
            } else {
                var a1 by remember { mutableStateOf("") }
                var a2 by remember { mutableStateOf("") }
                var a3 by remember { mutableStateOf("") }
                var newPass by remember { mutableStateOf("") }
                Text("Сброс пароля", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ответьте на контрольные вопросы", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text(questions.getOrElse(0) { "" }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = a1, onValueChange = { a1 = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                Text(questions.getOrElse(1) { "" }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = a2, onValueChange = { a2 = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                Text(questions.getOrElse(2) { "" }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = a3, onValueChange = { a3 = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("Новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val ok = viewModel.resetTwoFactorWithAnswers(listOf(a1, a2, a3), newPass)
                            if (ok) onUnlocked() else error = "Ответы не совпадают"
                        }
                    },
                    enabled = a1.isNotBlank() && a2.isNotBlank() && a3.isNotBlank() && newPass.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сбросить и войти") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { recoveryMode = false; error = null }) { Text("Назад") }
            }
        }
    }
}
