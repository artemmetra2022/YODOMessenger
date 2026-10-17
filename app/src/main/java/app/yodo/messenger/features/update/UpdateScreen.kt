package app.yodo.messenger.features.update

import app.yodo.messenger.ui.components.Button
import app.yodo.messenger.ui.components.OutlinedButton
import app.yodo.messenger.ui.components.IconButton
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import app.yodo.messenger.ui.components.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.BuildConfig
import app.yodo.messenger.R
import app.yodo.messenger.features.settings.SettingsCard
import app.yodo.messenger.ui.theme.ColorTheme
import app.yodo.messenger.ui.theme.LocalColorTheme

/**
 * НОВОЕ (OTA-обновления): экран «О приложении» — текущая версия и проверка
 * обновлений из GitHub Releases. Пункт входа — Настройки → Аккаунт.
 * Использует собственный экземпляр UpdateViewModel (в отличие от авто-проверки
 * в UpdateGate), поэтому ручная проверка всегда идёт в сеть.
 */
@Composable
fun UpdateScreen(
    onBackClick: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colorTheme = LocalColorTheme.current
    val releaseHistory = rememberReleaseHistory()

    HandleUpdateEvents(viewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AboutIdentityCard(state.currentVersionName, releaseHistory)

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCard {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    StatusLine(state = state, colorTheme = colorTheme)

                    if (state.phase == UpdatePhase.DOWNLOADING) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = state.progressPercent
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    state.update?.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.about_release_notes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Разбираем Markdown описания релиза (см. releaseNotesAnnotated).
                        Text(releaseNotesAnnotated(notes), style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButtons(state = state, viewModel = viewModel)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AboutReleaseHistory(releaseHistory, state.currentVersionName)

            Spacer(modifier = Modifier.height(12.dp))

            SettingsCard {
                OutlinedButton(
                    onClick = { viewModel.openUrl("https://github.com/${BuildConfig.GITHUB_REPO}") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.about_open_github))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusLine(state: UpdateUiState, colorTheme: ColorTheme) {
    when (state.phase) {
        UpdatePhase.IDLE -> Text(
            stringResource(R.string.about_check_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        UpdatePhase.CHECKING -> Text(
            stringResource(R.string.about_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = colorTheme.primary
        )
        UpdatePhase.UP_TO_DATE -> Text(
            stringResource(R.string.about_up_to_date),
            style = MaterialTheme.typography.bodyMedium,
            color = colorTheme.primary,
            fontWeight = FontWeight.Medium
        )
        UpdatePhase.AVAILABLE -> Text(
            stringResource(R.string.update_version_format, state.update?.versionName.orEmpty()),
            style = MaterialTheme.typography.titleMedium,
            color = colorTheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        UpdatePhase.WAITING_FOR_APK -> Column {
            Text("Мгновенная загрузка включена", style = MaterialTheme.typography.titleMedium, color = colorTheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(state.message ?: "Ждём APK релиза ${state.update?.versionName.orEmpty()}. Проверяем каждые 30 секунд, затем скачаем автоматически.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Ожидание работает в фоне до 6 часов. Установку нужно подтвердить вручную.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        UpdatePhase.DOWNLOADING -> Text(
            formatDownloadProgress(state.downloadedBytes, state.totalBytes, state.progressPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = colorTheme.primary
        )
        UpdatePhase.READY_TO_INSTALL -> Text(
            stringResource(R.string.update_ready_text),
            style = MaterialTheme.typography.bodyMedium,
            color = colorTheme.primary
        )
        UpdatePhase.ERROR -> Text(
            state.message?.let { stringResource(R.string.about_error_format, it) }
                ?: stringResource(R.string.about_error_generic),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ActionButtons(state: UpdateUiState, viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        viewModel.startInstantDownload()
    }
    fun startInstant() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else viewModel.startInstantDownload()
    }
    val notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (state.instantDownload && !notificationsEnabled) {
        Text("Уведомления отключены. Готовый APK будет доступен здесь; для уведомления об установке разрешите уведомления в настройках Android.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    when (state.phase) {
        UpdatePhase.WAITING_FOR_APK -> OutlinedButton(onClick = viewModel::cancelInstantDownload, modifier = Modifier.fillMaxWidth()) {
            Text("Остановить ожидание")
        }
        UpdatePhase.AVAILABLE -> Column {
            if (state.update?.apkUrl.isNullOrBlank()) {
                Text("Релиз опубликован, APK ещё готовится. Можно включить автоматическое ожидание и загрузку.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { startInstant() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.NewReleases, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Мгновенная загрузка")
                }
                TextButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) { Text("Проверить снова") }
                OutlinedButton(onClick = viewModel::downloadAndInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_btn_open_github)) }
            } else Button(onClick = viewModel::downloadAndInstall, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.NewReleases, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.update_btn_download))
            }
        }
        UpdatePhase.READY_TO_INSTALL -> Button(onClick = viewModel::installDownloaded, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.update_btn_install))
        }
        UpdatePhase.DOWNLOADING -> Column {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Загрузка APK…") }
            if (state.instantDownload) TextButton(onClick = viewModel::cancelInstantDownload, modifier = Modifier.fillMaxWidth()) { Text("Остановить загрузку") }
        }
        UpdatePhase.CHECKING -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_checking)) }
        else -> Button(onClick = {
            if (state.instantDownload) viewModel.cancelInstantDownload()
            viewModel.checkForUpdates()
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_check_button)) }
    }
}
