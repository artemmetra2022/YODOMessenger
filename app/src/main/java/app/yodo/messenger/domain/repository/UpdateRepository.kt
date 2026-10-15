package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.UpdateCheckResult
import java.io.File

/**
 * Проверка и загрузка обновлений приложения из GitHub Releases.
 * Google Play в проекте не используется — это единственный канал доставки.
 */
interface UpdateRepository {

    /** Версия приложения, установленного на этом устройстве (versionName). */
    val currentVersionName: String

    /** Числовой код установленной версии (versionCode). */
    val currentVersionCode: Int

    /**
     * Сравнивает установленную версию с последним релизом на GitHub.
     *
     * @param force игнорировать суточный троттлинг (используется при ручной
     * проверке из настроек; авто-проверка при старте троттлится, чтобы не
     * дёргать API при каждом запуске).
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult

    /**
     * Скачивает APK во внутренний кэш приложения и возвращает готовый файл.
     * Файл отдаётся установщику через FileProvider (см. ApkInstaller).
     */
    suspend fun downloadApk(
        url: String,
        versionName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File
}
