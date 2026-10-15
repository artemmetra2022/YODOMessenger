package app.yodo.messenger.domain.model

/**
 * Информация о доступной версии приложения, полученная из последнего релиза
 * на GitHub (см. UpdateRepository). В проект не подключён Google Play, поэтому
 * единственный канал доставки обновлений — GitHub Releases.
 */
data class AppUpdate(
    /** Версия из тега релиза («1.3.0»). */
    val versionName: String,
    /** Числовой код той же версии в формате CI: major*10000 + minor*100 + patch. */
    val versionCode: Int,
    /** Описание изменений (тело релиза), может быть пустым. */
    val releaseNotes: String,
    /**
     * Прямая ссылка на APK-файл во вложениях релиза. Null — APK к релизу не
     * прикреплён (тогда предлагаем открыть страницу релиза в браузере).
     */
    val apkUrl: String?,
    /** Страница релиза на GitHub — фолбэк, если APK во вложении отсутствует. */
    val releasePageUrl: String
)

/** Результат проверки обновлений. */
sealed interface UpdateCheckResult {
    /** Установлена последняя версия (или более новая). */
    data object UpToDate : UpdateCheckResult

    /** Доступна новая версия. */
    data class Available(val update: AppUpdate) : UpdateCheckResult

    /** Проверить не удалось (нет сети, ошибка API и т.п.). */
    data class Failed(val message: String) : UpdateCheckResult
}
