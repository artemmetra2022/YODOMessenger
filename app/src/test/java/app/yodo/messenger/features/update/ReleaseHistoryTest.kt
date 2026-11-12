package app.yodo.messenger.features.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseHistoryTest {
    @Test fun `date uses Russian month and actual release day`() {
        assertEquals("17 октября 2026",releaseDateLabel("2026-10-17"))
    }
    @Test fun `invalid or missing date stays unknown`() {
        listOf(null,"","bad","2026-02-30","2026-10-17T10:11:33Z").forEach { assertNull(releaseDateLabel(it)) }
    }
    @Test fun `exact installed version date never latest version date`() {
        val h=listOf(ReleaseHistoryEntry("1.10.0","2026-10-17","new"),ReleaseHistoryEntry("1.7.6","2026-10-16","old"))
        assertEquals("16 октября 2026",installedReleaseDate("1.7.6","",h))
        assertNull(installedReleaseDate("0.1.0-mvp","",h))
    }
    @Test fun `CI release date works even outside short history`() {
        assertEquals("15 октября 2026",installedReleaseDate("1.0.0","2026-10-15",emptyList()))
    }
    @Test fun `bad CI date falls back to exact catalogue version`() {
        assertEquals("16 октября 2026",installedReleaseDate("v1.7.6","invalid",listOf(ReleaseHistoryEntry("1.7.6","2026-10-16","x"))))
    }
    @Test fun `malformed catalogue safely becomes empty`() {
        listOf("oops","{}","null").forEach { assertTrue(parseReleaseHistory(it).isEmpty()) }
    }
    @Test fun `catalogue sorts semantically and deduplicates`() {
        val h=parseReleaseHistory("""[{"version":"1.9.0","date":"2026-10-17","summary":"nine"},{"version":"1.10.0","date":"2026-10-17","summary":"ten"},{"version":"1.9.0","summary":"duplicate"}]""")
        assertEquals(listOf("1.10.0","1.9.0"),h.map { it.version })
    }
    @Test fun `bad entries skipped and dates not invented`() {
        val h=parseReleaseHistory("""[null,3,{"version":"1.0.0","summary":""},{"version":"1.1.0-rc","summary":"rc"},{"version":"1.2.0","date":"2026-02-30","summary":"ok"}]""")
        assertEquals(1,h.size);assertNull(h.single().date)
    }
    @Test fun `history and summaries stay short`() {
        val json=(1..12).joinToString(prefix="[",postfix="]") { """{"version":"1.$it.0","summary":"${"x".repeat(400)}"}""" }
        val h=parseReleaseHistory(json)
        assertEquals(8,h.size);assertEquals("1.12.0",h.first().version)
        assertTrue(h.all { it.summary.length==320 })
    }
}
