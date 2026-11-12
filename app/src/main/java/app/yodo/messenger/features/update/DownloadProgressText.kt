package app.yodo.messenger.features.update

import java.util.Locale

/** Decimal megabytes (1 MB = 1,000,000 bytes); unknown size is never shown as zero. */
internal fun formatDownloadProgress(downloadedBytes: Long, totalBytes: Long?, progressPercent: Int?): String {
    fun mb(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes.coerceAtLeast(0) / 1_000_000.0)
    val downloaded = mb(downloadedBytes)
    val total = totalBytes?.takeIf { it > 0 }
    return if (total != null) {
        val percent = progressPercent?.let { "$it% · " }.orEmpty()
        "Загрузка… $percent$downloaded МБ / ${mb(total)} МБ"
    } else "Загрузка… $downloaded МБ / размер неизвестен"
}
