package app.yodo.messenger.features.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * Batch 7: «Центр безопасности» — настройка двухфакторной аутентификации,
 * контрольных вопросов, защиты от скриншотов и статусов профиля.
 */
@Composable
fun SecurityCenterScreen(
    onBackClick: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val is2faSet by viewModel.isTwoFactorSet.collectAsState(initial = false)
    val isRecoverySet by viewModel.isRecoverySet.collectAsState(initial = false)
    val screenshotProtection by viewModel.screenshotProtection.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Центр безопасности") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Двухфакторная аутентификация
            SectionCard("Двухфакторная аутентификация") {
                Text(
                    if (is2faSet) "Включена: при запуске приложения будет запрошен второй пароль."
                    else "Задайте второй пароль — он будет запрашиваться при каждом входе, помимо пароля аккаунта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (!is2faSet) {
                    var pass by remember { mutableStateOf("") }
                    var confirm by remember { mutableStateOf("") }
                    var hint by remember { mutableStateOf("") }
                    var localError by remember { mutableStateOf<String?>(null) }
                    OutlinedTextField(pass, { pass = it }, label = { Text("Новый пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Повторите пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(hint, { hint = it }, label = { Text("Подсказка (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    localError?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            localError = when {
                                pass.length < 4 -> "Пароль слишком короткий"
                                pass != confirm -> "Пароли не совпадают"
                                else -> null
                            }
                            if (localError == null) viewModel.enableTwoFactor(pass, hint.takeIf { it.isNotBlank() })
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Включить") }
                } else {
                    OutlinedButton(onClick = { viewModel.disableTwoFactor() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Отключить")
                    }
                }
            }

            // 2. Контрольные вопросы (сброс пароля)
            SectionCard("Сброс пароля: 3 контрольных вопроса") {
                Text(
                    if (isRecoverySet) "Вопросы заданы. С помощью ответов можно сбросить второй пароль."
                    else "Задайте 3 вопроса и ответа — они помогут восстановить доступ, если забудете второй пароль.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                var q1 by remember { mutableStateOf("") }
                var a1 by remember { mutableStateOf("") }
                var q2 by remember { mutableStateOf("") }
                var a2 by remember { mutableStateOf("") }
                var q3 by remember { mutableStateOf("") }
                var a3 by remember { mutableStateOf("") }
                var saved by remember { mutableStateOf(false) }
                OutlinedTextField(q1, { q1 = it }, label = { Text("Вопрос 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(a1, { a1 = it }, label = { Text("Ответ 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(q2, { q2 = it }, label = { Text("Вопрос 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(a2, { a2 = it }, label = { Text("Ответ 2") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(q3, { q3 = it }, label = { Text("Вопрос 3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(a3, { a3 = it }, label = { Text("Ответ 3") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.setRecoveryQuestions(listOf(q1, q2, q3), listOf(a1, a2, a3))
                        saved = true
                    },
                    enabled = q1.isNotBlank() && a1.isNotBlank() && q2.isNotBlank() && a2.isNotBlank() && q3.isNotBlank() && a3.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить вопросы") }
                if (saved) { Spacer(Modifier.height(6.dp)); Text("Сохранено ✓", color = colorTheme.primary, style = MaterialTheme.typography.bodySmall) }
            }

            // 3. Защита от скриншотов
            SectionCard("Защита от скриншотов") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Запретить скриншоты и запись экрана", style = MaterialTheme.typography.bodyMedium)
                        Text("Содержимое не видно в скриншотах и в меню недавних приложений.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = screenshotProtection, onCheckedChange = { viewModel.setScreenshotProtection(it) })
                }
            }

            // Эмодзи-статус и текстовый статус перенесены в экран профиля (рядом с аватаркой).

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
