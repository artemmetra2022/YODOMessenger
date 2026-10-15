package app.yodo.messenger.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineProfileTest {

    @Test
    fun `initials use first two words`() {
        assertEquals("АС", OfflineProfile(displayName = "Анна Смирнова").initials)
    }

    @Test
    fun `empty name has stable fallback initial`() {
        assertEquals("Y", OfflineProfile(displayName = "").initials)
    }
}
