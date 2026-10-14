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
enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY_TO_INSTALL, ERROR }

data class UpdateUiState(
    val currentVersionName: String = "",
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val update: AppUpdate? = null,
    /** Прогресс загрузки APK в процентах; null — размер неизвестен. */
    val progressPercent: Int? = null,
    val downloadedFile: File? = null,
    val message: String? = null
)

/** Разовые действия, которые экран/диалог выполняет сам (ему нужен Context). */
sealed interface UpdateEvent {
    data class InstallApk(val file: File) : UpdateEvent
    data class OpenUrl(val url: String) : UpdateEvent
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        UpdateUiState(currentVersionName = updateRepository.currentVersionName)
    )
    val state = _state.asStateFlow()

    private val _events = Channel<UpdateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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
        if (phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING) return
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
        if (_state.value.phase == UpdatePhase.DOWNLOADING) return

        viewModelScope.launch {
            _state.update { it.copy(phase = UpdatePhase.DOWNLOADING, progressPercent = 0, message = null) }
            try {
                val file = updateRepository.downloadApk(url, update.versionName) { done, total ->
                    val percent = if (total > 0L) ((done * 100) / total).toInt() else null
                    _state.update { it.copy(progressPercent = percent) }
                }
                _state.update {
                    it.copy(phase = UpdatePhase.READY_TO_INSTALL, progressPercent = null, downloadedFile = file)
                }
                _events.send(UpdateEvent.InstallApk(file))
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
        _state.value.downloadedFile?.let { _events.trySend(UpdateEvent.InstallApk(it)) }
    }

    /** Открыть произвольную ссылку в браузере (страница релиза, репозиторий). */
    fun openUrl(url: String) {
        _events.trySend(UpdateEvent.OpenUrl(url))
    }

    fun dismiss() {
        _state.update { it.copy(phase = UpdatePhase.IDLE, update = null, message = null) }
    }
}
