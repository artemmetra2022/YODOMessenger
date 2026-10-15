package app.yodo.messenger.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.R
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError

/**
 * Экран сброса пароля: пользователь вводит email или username, мы резолвим email
 * (та же логика, что и при входе — см. AuthRepositoryImpl.resolveEmailByUsername)
 * и отправляем письмо со ссылкой для сброса через Firebase Auth.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var emailOrUsername by remember { mutableStateOf("") }
    val colorTheme = LocalColorTheme.current
    val state by viewModel.resetPasswordState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (state is ResetPasswordUiState.Success) {
                // Экран успеха: подтверждение отправки письма.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
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

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    stringResource(R.string.reset_password_success),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(stringResource(R.string.reset_password_back_to_login), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                // Логотип, как на экране входа — для визуальной согласованности.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colorTheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(R.string.reset_password_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(R.string.reset_password_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(28.dp))

                OutlinedTextField(
                    value = emailOrUsername,
                    onValueChange = { emailOrUsername = it },
                    label = { Text(stringResource(R.string.reset_password_input_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state is ResetPasswordUiState.Error) {
                    Text(
                        text = (state as ResetPasswordUiState.Error).message,
                        color = YodoError,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.resetPassword(emailOrUsername) },
                    enabled = state !is ResetPasswordUiState.Loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorTheme.primary),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state is ResetPasswordUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.reset_password_submit), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(R.string.reset_password_back_to_login),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorTheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onBack() }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}
