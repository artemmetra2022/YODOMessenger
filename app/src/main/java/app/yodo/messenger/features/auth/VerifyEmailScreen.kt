package app.yodo.messenger.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * Экран показывается сразу после регистрации (или при логине неподтверждённым
 * аккаунтом): просим пользователя перейти по ссылке из письма, которое отправил
 * Firebase на [email]. Письмо и ссылку формирует и рассылает сам Firebase Auth —
 * дополнительный сервер/SMTP не нужен.
 */
@Composable
fun VerifyEmailScreen(
    email: String,
    onVerified: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val uiState by viewModel.uiState.collectAsState()
    val notVerifiedYet by viewModel.notVerifiedYetEvent.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onVerified()
            viewModel.resetState()
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colorTheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MarkEmailRead,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer1()

            Text(
                "Подтвердите почту",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer1()

            Text(
                "Мы отправили письмо со ссылкой для подтверждения на $email. " +
                    "Перейдите по ссылке из письма, затем вернитесь сюда и нажмите «Я подтвердил».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (notVerifiedYet) {
                Spacer1()
                Text(
                    "Почта пока не подтверждена. Проверьте письмо (в т.ч. папку «Спам») и перейдите по ссылке.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                LaunchedEffect(notVerifiedYet) {
                    kotlinx.coroutines.delay(4000)
                    viewModel.consumeNotVerifiedYetEvent()
                }
            }

            Spacer1()
            Spacer1()

            Button(
                onClick = { viewModel.checkEmailVerified() },
                enabled = uiState !is AuthUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("Я подтвердил почту")
                }
            }

            Spacer1()

            OutlinedButton(
                onClick = { viewModel.resendVerificationEmail() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Отправить письмо ещё раз")
            }

            Spacer1()

            TextButton(onClick = {
                viewModel.logoutAndReturnToLogin()
                onBackToLogin()
            }) {
                Text("Выйти и вернуться ко входу")
            }
        }
    }
}

@Composable
private fun Spacer1() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
}
