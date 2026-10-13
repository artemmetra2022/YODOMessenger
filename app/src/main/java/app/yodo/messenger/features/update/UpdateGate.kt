package app.yodo.messenger.features.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import app.yodo.messenger.core.util.ApkInstaller

/**
 * Подключает разовые [UpdateEvent] к системным действиям (установка APK, браузер).
 * Вызывается и авто-проверкой при старте, и экраном «О приложении».
 */
@Composable
fun HandleUpdateEvents(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UpdateEvent.InstallApk -> {
                    if (ApkInstaller.canInstall(context)) {
                        ApkInstaller.install(context, event.file)
                    } else {
                        // Разрешение ещё не выдано — отправляем на нужный экран настроек.
                        // Диалог/экран остаётся открытым с кнопкой «Установить» для повтора.
                        ApkInstaller.openInstallPermissionSettings(context)
                    }
                }
                is UpdateEvent.OpenUrl -> ApkInstaller.openUrl(context, event.url)
            }
        }
    }
}

/**
 * НОВОЕ (OTA-обновления): невидимый хост авто-проверки обновлений при запуске
 * приложения. Показывается только при наличии новой версии — иначе не мешает.
 *
 * @param enabled проверять ли обновления (например, только для авторизованных).
 */
@Composable
fun UpdateGate(
    enabled: Boolean,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(enabled) { if (enabled) viewModel.autoCheck() }
    HandleUpdateEvents(viewModel)

    val showDialog = state.update != null && when (state.phase) {
        UpdatePhase.AVAILABLE,
        UpdatePhase.DOWNLOADING,
        UpdatePhase.READY_TO_INSTALL,
        UpdatePhase.ERROR -> true
        else -> false
    }
    if (showDialog) {
        UpdateAvailableDialog(
            state = state,
            onDownload = viewModel::downloadAndInstall,
            onInstall = viewModel::installDownloaded,
            onDismiss = viewModel::dismiss
        )
    }
}
