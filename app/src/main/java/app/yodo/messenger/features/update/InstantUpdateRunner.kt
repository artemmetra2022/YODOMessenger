package app.yodo.messenger.features.update

import app.yodo.messenger.domain.model.AppUpdate
import app.yodo.messenger.domain.model.UpdateCheckResult
import app.yodo.messenger.domain.repository.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Testable polling loop; Android lifetime, notification and APK validation remain in the service. */
internal suspend fun awaitInstantUpdate(
    repository: UpdateRepository,
    selectedCode: Int,
    elapsedMillis: () -> Long,
    sleep: suspend (Long) -> Unit,
    waiting: (String) -> Unit,
    downloading: (AppUpdate) -> Unit,
    progress: (Long, Long) -> Unit
): Pair<AppUpdate, File> {
    val deadline = elapsedMillis() + INSTANT_WAIT_LIMIT_MS
    var downloadFailures = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        check(elapsedMillis() < deadline) { "APK не появился за 6 часов. Включите мгновенную загрузку повторно." }
        var delayMs = INSTANT_POLL_MS
        when (val result = repository.checkForUpdate(force = true)) {
            is UpdateCheckResult.Available -> {
                if (instantApkReady(result.update, selectedCode, repository.currentVersionCode)) {
                    downloading(result.update)
                    try {
                        val file = repository.downloadApk(result.update.apkUrl!!, result.update.versionName, progress)
                        currentCoroutineContext().ensureActive()
                        return result.update to file
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        downloadFailures++
                        if (downloadFailures >= 3) throw e
                        waiting("Не удалось скачать APK. Повторим через 30 секунд (попытка ${downloadFailures + 1} из 3).")
                    }
                } else waiting("APK ещё не опубликован. Проверка каждые 30 секунд.")
            }
            is UpdateCheckResult.Failed -> {
                delayMs = instantPollDelay(result.retryAfterMillis)
                waiting(if (delayMs > INSTANT_POLL_MS) "GitHub временно ограничил запросы. Проверки продолжатся автоматически."
                    else "Нет ответа от сервера. Повторная проверка через 30 секунд.")
            }
            UpdateCheckResult.UpToDate -> waiting("Ожидаем APK нового релиза. Проверка каждые 30 секунд.")
        }
        sleep(minOf(delayMs, (deadline - elapsedMillis()).coerceAtLeast(0)))
    }
}
