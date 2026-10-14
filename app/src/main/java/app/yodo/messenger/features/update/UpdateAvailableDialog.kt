package app.yodo.messenger.features.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yodo.messenger.R

/**
 * НОВОЕ (OTA-обновления): единый диалог о доступном обновлении. Используется и
 * экраном «О приложении», и авто-проверкой при старте (см. UpdateGate).
 *
 * Гибридная доставка: если APK прикреплён к релизу — предлагаем скачать и
 * установить его прямо в приложении, иначе (или по желанию пользователя) —
 * открываем страницу релиза на GitHub.
 */
@Composable
fun UpdateAvailableDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val update = state.update ?: return

    AlertDialog(
        onDismissRequest = { if (state.phase != UpdatePhase.DOWNLOADING) onDismiss() },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.update_version_format, update.versionName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.update_current_version_format, state.currentVersionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (update.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.about_release_notes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Разбираем Markdown описания релиза, чтобы в диалоге не было
                    // служебных «###» и «**» (см. releaseNotesAnnotated).
                    Text(releaseNotesAnnotated(update.releaseNotes), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        state.progressPercent?.let {
                            stringResource(R.string.update_downloading_format, it)
                        } ?: stringResource(R.string.update_downloading_unknown),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = state.progressPercent
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    } else {
                        LinearProgressIndicator()
                    }
                }
                if (state.phase == UpdatePhase.READY_TO_INSTALL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.update_ready_text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (state.phase == UpdatePhase.ERROR && state.message != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                UpdatePhase.DOWNLOADING -> TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.update_downloading_unknown))
                }
                UpdatePhase.READY_TO_INSTALL -> TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.update_btn_install))
                }
                else -> TextButton(onClick = onDownload) {
                    Text(
                        if (update.apkUrl.isNullOrBlank()) stringResource(R.string.update_btn_open_github)
                        else stringResource(R.string.update_btn_download)
                    )
                }
            }
        },
        dismissButton = {
            if (state.phase != UpdatePhase.DOWNLOADING) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_btn_later))
                }
            }
        }
    )
}
