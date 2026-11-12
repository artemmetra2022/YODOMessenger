package app.yodo.messenger.features.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.AppUpdate
import app.yodo.messenger.domain.model.UpdateCheckResult
import app.yodo.messenger.domain.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Фазы проверки/загрузки обновления — управляют тем, что показывать в UI. */
enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, WAITING_FOR_APK, DOWNLOADING, READY_TO_INSTALL, ERROR }

data class UpdateUiState(
    val currentVersionName: String = "",
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val update: AppUpdate? = null,
    /** Прогресс загрузки APK в процентах; null — размер неизвестен. */
    val progressPercent: Int? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val downloadedFile: File? = null,
    val instantDownload: Boolean = false,
    val message: String? = null
)

/** Разовые действия, которые экран/диалог выполняет сам (ему нужен Context). */
sealed interface UpdateEvent {
    data class InstallApk(val file: File) : UpdateEvent
    data class OpenUrl(val url: String) : UpdateEvent
}

@HiltViewModel
class UpdateViewModel @Inject internal constructor(
    private val instantStore: InstantUpdateStore,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        UpdateUiState(currentVersionName = updateRepository.currentVersionName)
    )
    val state = _state.asStateFlow()

    private val _events = Channel<UpdateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            instantStore.state.collect { snapshot ->
                if (snapshot.phase == InstantPhase.IDLE) {
                    if (_state.value.instantDownload) _state.update { it.copy(phase = UpdatePhase.IDLE,
                        instantDownload = false, downloadedFile = null, message = null) }
                    return@collect
                }
                val phase = when (snapshot.phase) {
                    InstantPhase.WAITING -> UpdatePhase.WAITING_FOR_APK
                    InstantPhase.DOWNLOADING -> UpdatePhase.DOWNLOADING
                    InstantPhase.READY -> UpdatePhase.READY_TO_INSTALL
                    else -> UpdatePhase.ERROR
                }
                _state.update { current -> current.copy(phase = phase, instantDownload = true,
                    progressPercent = snapshot.totalBytes?.let { ((snapshot.downloadedBytes.toDouble() / it) * 100).toInt().coerceIn(0,100) },
                    downloadedBytes = snapshot.downloadedBytes, totalBytes = snapshot.totalBytes,
                    downloadedFile = snapshot.file, message = snapshot.message,
                    update = current.update?.takeIf { it.versionCode == snapshot.versionCode } ?: snapshot.versionCode.takeIf { it > 0 }?.let {
                        AppUpdate(snapshot.versionName, it, "", null, "https://github.com/${app.yodo.messenger.BuildConfig.GITHUB_REPO}/releases")
                    }) }
            }
        }
    }

    fun startInstantDownload() {
        val update = _state.value.update ?: return
        if (!canStartInstantDownload(update, updateRepository.currentVersionCode) || instantStore.state.value.active) return
        instantStore.start(update.versionName, update.versionCode)
    }

    fun cancelInstantDownload() = instantStore.cancel()

    // Авто-проверка выполняется один раз за жизнь Activity-скоупной ViewModel,
    // чтобы не дёргать GitHub при каждой рекомпозиции/повороте.
    private var autoCheckStarted = false

    /** Проверка при старте: троттлится сутками на стороне репозитория. */
    fun autoCheck() {
        if (autoCheckStarted) return
        autoCheckStarted = true
        check(force = false)
    }

    /** Ручная проверка из настроек — всегда идёт в сеть. */
    fun checkForUpdates() = check(force = true)

    private fun check(force: Boolean) {
        val phase = _state.value.phase
        if (instantStore.state.value.active || instantStore.state.value.phase == InstantPhase.READY || phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) return
        viewModelScope.launch {
            _state.update { it.copy(phase = UpdatePhase.CHECKING, message = null, update = null) }
            when (val result = updateRepository.checkForUpdate(force = force)) {
                is UpdateCheckResult.Available ->
                    _state.update { it.copy(phase = UpdatePhase.AVAILABLE, update = result.update) }
                UpdateCheckResult.UpToDate ->
                    _state.update { it.copy(phase = UpdatePhase.UP_TO_DATE, update = null) }
                is UpdateCheckResult.Failed ->
                    _state.update { it.copy(phase = UpdatePhase.ERROR, message = result.message) }
            }
        }
    }

    /** Скачивает APK и сразу пытается запустить установку. */
    fun downloadAndInstall() {
        val update = _state.value.update ?: return
        val url = update.apkUrl
        if (url.isNullOrBlank()) {
            // APK к релизу не прикреплён — открываем страницу релиза (гибридный режим).
            _events.trySend(UpdateEvent.OpenUrl(update.releasePageUrl))
            return
        }
        if (instantStore.state.value.active || _state.value.phase == UpdatePhase.DOWNLOADING) return

        viewModelScope.launch {
            _state.update { it.copy(phase = UpdatePhase.DOWNLOADING, progressPercent = null, downloadedBytes = 0L, totalBytes = null, downloadedFile = null, message = null) }
            try {
                val file = updateRepository.downloadApk(url, update.versionName) { done, total ->
                    val percent = if (total > 0L) ((done.toDouble() / total) * 100).toInt().coerceIn(0, 100) else null
                    _state.update { it.copy(progressPercent = percent, downloadedBytes = done.coerceAtLeast(0), totalBytes = total.takeIf { size -> size > 0 }) }
                }
                _state.update {
                    it.copy(phase = UpdatePhase.READY_TO_INSTALL, progressPercent = null, downloadedFile = file)
                }
                _events.send(UpdateEvent.InstallApk(file))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = UpdatePhase.ERROR,
                        progressPercent = null,
                        message = e.message ?: "Не удалось скачать обновление"
                    )
                }
            }
        }
    }

    /** Повторная попытка установки уже скачанного файла (после выдачи разрешения). */
    fun installDownloaded() {
        val file = _state.value.downloadedFile
        if (file != null && file.isFile && file.length() > 0) _events.trySend(UpdateEvent.InstallApk(file))
        else _state.update { it.copy(phase = UpdatePhase.ERROR, message = "APK больше недоступен. Проверьте обновления и скачайте его повторно.") }
    }

    /** Открыть произвольную ссылку в браузере (страница релиза, репозиторий). */
    fun openUrl(url: String) {
        _events.trySend(UpdateEvent.OpenUrl(url))
    }

    fun dismiss() {
        _state.update { it.copy(phase = UpdatePhase.IDLE, update = null, message = null) }
    }
}
