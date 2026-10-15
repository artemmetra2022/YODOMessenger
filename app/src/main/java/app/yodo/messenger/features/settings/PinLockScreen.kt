package app.yodo.messenger.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.data.local.PinCheckResult
import app.yodo.messenger.ui.theme.LocalColorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * п.6: полноэкранная блокировка вводом PIN-кода. Показывается поверх остального
 * приложения (см. MainActivity) при запуске/возврате из фона, если этого требует
 * выбранный пользователем режим (после закрытия / после сворачивания).
 */
@Composable
fun PinLockScreen(
    onUnlocked: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colorTheme = LocalColorTheme.current
    val coroutineScope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableStateOf(0L) }
    var remainingLockSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            remainingLockSeconds = (lockedUntil - System.currentTimeMillis()) / 1000 + 1
            delay(1000)
        }
        remainingLockSeconds = 0
    }

    val pinWrongTemplate = stringResource(R.string.pin_lock_wrong, 0).substringBefore("0")
    val pinTooMany = stringResource(R.string.pin_lock_too_many)
    fun submitPin() {
        val toCheck = enteredPin
        enteredPin = ""
        coroutineScope.launch {
            when (val result = viewModel.verifyPin(toCheck)) {
                is PinCheckResult.Success -> onUnlocked()
                is PinCheckResult.WrongPin -> {
                    errorText = "$pinWrongTemplate${result.attemptsRemaining}"
                }
                is PinCheckResult.LockedOut -> {
                    lockedUntil = result.unlockAtMillis
                    errorText = pinTooMany
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = colorTheme.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.pin_lock_enter), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                            .background(if (filled) colorTheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val isLocked = remainingLockSeconds > 0
            if (isLocked) {
                Text(
                    stringResource(R.string.pin_lock_retry_seconds, remainingLockSeconds),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                digits.chunked(3).forEach { rowDigits ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        rowDigits.forEach { digit ->
                            PinKey(
                                label = digit,
                                enabled = !isLocked,
                                onClick = {
                                    when {
                                        digit == "⌫" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        digit.isNotEmpty() && enteredPin.length < 4 -> {
                                            enteredPin += digit
                                            errorText = null
                                            if (enteredPin.length == 4) submitPin()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(64.dp)
            .let { if (label.isNotEmpty()) it.clip(CircleShape).clickable(enabled = enabled, onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        if (label == "⌫") {
            Icon(Icons.Filled.Backspace, contentDescription = stringResource(R.string.pin_lock_backspace_cd), tint = if (enabled) Color.Gray else Color.LightGray)
        } else if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }
    }
}
