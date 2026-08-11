package app.yodo.messenger.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import app.yodo.messenger.ui.locale.LanguageSwitcher
import app.yodo.messenger.ui.theme.YodoAccent
import app.yodo.messenger.ui.theme.YodoPrimary

// Фирменный градиент бренда (фиолетово-синий) — используется на логотипе и главной кнопке
private val BrandGradient = Brush.linearGradient(listOf(YodoPrimary, YodoAccent))

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit,
    onRegisterClick: () -> Unit = onStartClick,
    onOfflineModeClick: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Переключатель языка в правом верхнем углу — ниже статус-бара через windowInsetsPadding
        LanguageSwitcher(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, end = 16.dp),
            accentColor = YodoPrimary
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(BrandGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = "Yodo Messenger",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )

            Button(
                onClick = onStartClick,
                colors = ButtonDefaults.buttonColors(containerColor = YodoPrimary),
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
            ) {
                Text(stringResource(R.string.welcome_start))
            }

            TextButton(onClick = onRegisterClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(R.string.welcome_no_account))
            }

            Text(
                text = stringResource(R.string.welcome_or),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )

            OutlinedButton(
                onClick = onOfflineModeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.WifiOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.welcome_offline_mode), modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(R.string.welcome_offline_description),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
