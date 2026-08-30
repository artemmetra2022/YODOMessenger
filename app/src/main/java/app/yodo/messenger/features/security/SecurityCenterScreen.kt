package app.yodo.messenger.features.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.repository.TwoFactorEmailSendResult
import app.yodo.messenger.ui.theme.LocalColorTheme
import kotlinx.coroutines.launch

/**
 * Batch 7: «Центр безопасности» — 2FA по email-коду, защита от скриншотов
 * и статусы профиля. Второй пароль и контрольные вопросы для сброса убраны:
 * единственный дополнительный шаг при входе теперь — код на почту.
 */
@Composable
fun SecurityCenterScreen(
    onBackClick: () -> Unit,
    onOpenQrLogin: () -> Unit = {},
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val scope = rememberCoroutineScope()
    val is2faSet by viewModel.isTwoFactorSet.collectAsState(initial = false)
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val screenshotProtection by viewModel.screenshotProtection.collectAsState(initial = false)
    val requireEmailVerification by viewModel.requireEmailVerification.collectAsState(initial = true)

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
            // Двухфакторная аутентификация по email-коду
            SectionCard("Двухфакторная аутентификация") {
                if (!is2faSet) {
                    Text(
                        "Включите — и при входе в аккаунт на новом устройстве мы будем присылать 6-значный код на вашу почту.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.enableTwoFactor() },
                        colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Включить") }
                } else {
                    var awaitingCode by remember { mutableStateOf(false) }
                    var maskedEmail by remember { mutableStateOf("") }
                    var code by remember { mutableStateOf("") }
                    var error by remember { mutableStateOf<String?>(null) }

                    if (!awaitingCode) {
                        Text(
                            "Включена: при входе в аккаунт на новом устройстве мы пришлём код на вашу почту.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                error = null
                                scope.launch {
                                    when (val result = viewModel.requestDisableCode()) {
                                        is TwoFactorEmailSendResult.Success -> {
                                            maskedEmail = result.maskedEmail
                                            awaitingCode = true
                                        }
                                        is TwoFactorEmailSendResult.Error -> error = result.message
                                    }
                                }
                            },
                            enabled = !isSendingCode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            } else {
                                Text("Отключить")
                            }
                        }
                        error?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            "Мы отправили код на почту: $maskedEmail. Введите его, чтобы подтвердить отключение.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) code = new },
                            label = { Text("Код из письма") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = error != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        error?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val ok = viewModel.disableTwoFactor(code)
                                    if (ok) {
                                        awaitingCode = false
                                        code = ""
                                    } else {
                                        error = "Неверный или устаревший код"
                                    }
                                }
                            },
                            enabled = code.length == 6,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Подтвердить отключение") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { awaitingCode = false; code = ""; error = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Отмена") }
                    }
                }
            }

            // НОВОЕ (вход по QR-коду): сканирование QR с веб-версии для быстрого
            // входа на сайте без набора пароля на клавиатуре компьютера.
            SectionCard("Вход по QR-коду") {
                Text(
                    "Откройте YODO в браузере и отсканируйте показанный там QR-код, " +
                        "чтобы войти в аккаунт на компьютере.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenQrLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Сканировать QR-код")
                }
            }

            // Защита от скриншотов
            SectionCard("Защита от скриншотов") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Запретить скриншоты и запись экрана", style = MaterialTheme.typography.bodyMedium)
                        Text("Содержимое не видно в скриншотах и в меню недавних приложений.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = screenshotProtection, onCheckedChange = { viewModel.setScreenshotProtection(it) })
                }
            }

            // НОВОЕ: видно только двум доверенным email (создатели приложения).
            // Глобальный переключатель — применяется ко ВСЕМ пользователям приложения.
            if (viewModel.isAppAdmin) {
                SectionCard("Подтверждение почты при входе (для всех пользователей)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Требовать подтверждённый email для входа", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Если включено, пользователи с неподтверждённой почтой не смогут войти, пока не перейдут по ссылке из письма. На уже подтверждённые email не влияет.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = requireEmailVerification,
                            onCheckedChange = { viewModel.setRequireEmailVerification(it) }
                        )
                    }
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
