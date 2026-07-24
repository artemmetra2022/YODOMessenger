package app.yodo.messenger.features.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.ui.theme.YodoError

@Composable
fun PhoneLoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToEmailLogin: () -> Unit,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as? Activity

    var phone by remember { mutableStateOf("+7") }
    var code by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()
    val verificationId by viewModel.verificationId.collectAsState()

    val codeWasSent = verificationId != null

    LaunchedEffect(uiState) {
        if (uiState is PhoneAuthUiState.Success) onLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Вход по телефону", style = MaterialTheme.typography.headlineLarge)

        if (!codeWasSent) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Телефон, например +79001234567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                singleLine = true
            )

            Button(
                onClick = { activity?.let { viewModel.sendCode(phone, it) } },
                enabled = uiState !is PhoneAuthUiState.SendingCode,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (uiState is PhoneAuthUiState.SendingCode) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                } else {
                    Text("Получить код")
                }
            }
        } else {
            Text(text = "Код отправлен на $phone", modifier = Modifier.padding(top = 16.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(6) },
                label = { Text("Код из СМС") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )

            Button(
                onClick = { viewModel.verifyCode(code) },
                enabled = uiState !is PhoneAuthUiState.VerifyingCode,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (uiState is PhoneAuthUiState.VerifyingCode) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                } else {
                    Text("Подтвердить")
                }
            }

            TextButton(
                onClick = { activity?.let { viewModel.resendCode(it) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отправить код повторно")
            }
        }

        if (uiState is PhoneAuthUiState.Error) {
            Text(
                text = (uiState as PhoneAuthUiState.Error).message,
                color = YodoError,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        TextButton(
            onClick = onNavigateToEmailLogin,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Войти через email вместо этого")
        }
    }
}
