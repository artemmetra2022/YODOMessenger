package app.yodo.messenger.features.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.domain.model.DeviceSession
import app.yodo.messenger.ui.theme.LocalColorTheme
import app.yodo.messenger.ui.theme.YodoError
import app.yodo.messenger.ui.theme.YodoSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DevicesScreen(
    onBackClick: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colorTheme = LocalColorTheme.current
    var showTerminateAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Устройства") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── Пояснение ─────────────────────────────────────────────────────
            Text(
                text = "Ниже перечислены все устройства, на которых выполнен вход в ваш аккаунт YODO.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ── Загрузка ──────────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorTheme.primary)
                }
                return@Column
            }

            // ── Ошибка ────────────────────────────────────────────────────────
            uiState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = YodoError,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // ── Текущее устройство ────────────────────────────────────────────
            val current = uiState.sessions.firstOrNull { it.isCurrent }
            if (current != null) {
                SectionTitle("Это устройство")
                DeviceCard(
                    session = current,
                    isCurrent = true,
                    isTerminating = false,
                    primaryColor = colorTheme.primary,
                    onTerminate = {}   // текущий сеанс нельзя завершить отсюда
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Другие устройства ─────────────────────────────────────────────
            val others = uiState.sessions.filter { !it.isCurrent }
            if (others.isNotEmpty()) {
                SectionTitle("Другие устройства (${others.size})")
                Spacer(modifier = Modifier.height(4.dp))
                others.forEachIndexed { index, session ->
                    DeviceCard(
                        session = session,
                        isCurrent = false,
                        isTerminating = uiState.terminatingId == session.sessionId,
                        primaryColor = colorTheme.primary,
                        onTerminate = { viewModel.terminateSession(session.sessionId) }
                    )
                    if (index < others.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Кнопка «Завершить все другие» ──────────────────────────
                OutlinedButton(
                    onClick = { showTerminateAllDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = YodoError),
                    border = androidx.compose.foundation.BorderStroke(1.dp, YodoError.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Завершить все другие сеансы")
                }
            } else if (!uiState.isLoading) {
                // Только текущее устройство — других нет
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Вы вошли только с этого устройства. Другие активные сеансы не обнаружены.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Диалог подтверждения «Завершить все» ──────────────────────────────
    if (showTerminateAllDialog) {
        AlertDialog(
            onDismissRequest = { showTerminateAllDialog = false },
            title = { Text("Завершить все другие сеансы?") },
            text = {
                Text("Все устройства, кроме текущего, будут отключены от вашего аккаунта.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.terminateAllOtherSessions()
                        showTerminateAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YodoError)
                ) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTerminateAllDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Карточка одного устройства
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeviceCard(
    session: DeviceSession,
    isCurrent: Boolean,
    isTerminating: Boolean,
    primaryColor: Color,
    onTerminate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Иконка платформы в кружке
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isCurrent) primaryColor.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = platformIcon(session.platform),
                contentDescription = null,
                tint = if (isCurrent) primaryColor
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(YodoSuccess.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Текущее",
                            style = MaterialTheme.typography.labelSmall,
                            color = YodoSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = session.platform,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "YODO ${session.appVersion} · ${formatLastActive(session.lastActiveAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Кнопка завершить (только для не-текущих)
        if (!isCurrent) {
            AnimatedVisibility(visible = isTerminating, enter = fadeIn(), exit = fadeOut()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = YodoError
                )
            }
            AnimatedVisibility(visible = !isTerminating, enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onTerminate) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Завершить сеанс",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// ────────────────────────────────────────────────────────────────────────────
// Вспомогательные функции
// ────────────────────────────────────────────────────────────────────────────

private fun platformIcon(platform: String): ImageVector {
    val p = platform.lowercase()
    return when {
        "android" in p || "phone" in p -> Icons.Filled.PhoneAndroid
        "tablet" in p || "ipad" in p   -> Icons.Filled.Tablet
        else                           -> Icons.Filled.Computer
    }
}

private fun formatLastActive(millis: Long): String {
    if (millis == 0L) return "никогда"
    val now = System.currentTimeMillis()
    val diff = now - millis

    return when {
        diff < TimeUnit.MINUTES.toMillis(1)  -> "только что"
        diff < TimeUnit.HOURS.toMillis(1)    -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} мин. назад"
        diff < TimeUnit.HOURS.toMillis(24)   -> "${TimeUnit.MILLISECONDS.toHours(diff)} ч. назад"
        diff < TimeUnit.DAYS.toMillis(2)     -> "вчера"
        diff < TimeUnit.DAYS.toMillis(7)     -> "${TimeUnit.MILLISECONDS.toDays(diff)} дн. назад"
        else -> SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(millis))
    }
}
