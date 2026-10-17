package app.yodo.messenger.features.update

import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ReleaseHistoryEntry(val version: String, val date: String?, val summary: String)

/** Only genuine release dates, never build/install time or the date of a different version. */
internal fun releaseDateLabel(raw: String?): String? = try {
    raw?.takeIf { it.isNotBlank() }?.let {
        LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))
    }
} catch (_: Exception) { null }

internal fun installedReleaseDate(version: String, buildDate: String, history: List<ReleaseHistoryEntry>): String? =
    releaseDateLabel(buildDate) ?: history.firstOrNull { it.version == version.removePrefix("v") }?.date?.let(::releaseDateLabel)

internal fun parseReleaseHistory(text: String): List<ReleaseHistoryEntry> = try {
    Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
        val obj=element as? JsonObject ?: return@mapNotNull null
        val version=(obj["version"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val summary=(obj["summary"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        if(!version.matches(Regex("\\d+\\.\\d+\\.\\d+")) || summary.isBlank()) return@mapNotNull null
        val date=(obj["date"] as? JsonPrimitive)?.contentOrNull?.takeIf { releaseDateLabel(it)!=null }
        ReleaseHistoryEntry(version,date,summary.take(320))
    }.distinctBy { it.version }.sortedWith(compareByDescending<ReleaseHistoryEntry> { it.version.substringBefore('.').toLongOrNull() ?: 0 }
        .thenByDescending { it.version.split('.')[1].toLongOrNull() ?: 0 }
        .thenByDescending { it.version.substringAfterLast('.').toLongOrNull() ?: 0 }).take(8)
} catch (_: Exception) { emptyList() }
