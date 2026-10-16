package app.yodo.messenger.features.update

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTextTest {
    @Test fun knownSizeContainsPercentAndMegabytes() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("Загрузка… 45% · 18,2 МБ / 40,5 МБ", formatDownloadProgress(18_200_000, 40_500_000, 45))
        } finally { Locale.setDefault(old) }
    }
    @Test fun unknownTotalStillShowsDownloadedAmount() {
        val result = formatDownloadProgress(2_000_000, null, null)
        assertTrue(result.contains("МБ / размер неизвестен"))
        assertFalse(result.contains("%"))
    }
    @Test fun zeroOrNegativeTotalIsUnknown() {
        assertTrue(formatDownloadProgress(10, 0, null).contains("размер неизвестен"))
        assertTrue(formatDownloadProgress(10, -1, null).contains("размер неизвестен"))
    }
    @Test fun largeDownloadsAreNotConvertedToInt() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertTrue(formatDownloadProgress(5_000_000_000, 10_000_000_000, 50).contains("5000.0 МБ / 10000.0 МБ"))
        } finally { Locale.setDefault(old) }
    }
}
