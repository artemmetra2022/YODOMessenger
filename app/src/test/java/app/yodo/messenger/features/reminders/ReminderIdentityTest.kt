package app.yodo.messenger.features.reminders

import org.junit.Assert.*
import org.junit.Test

class ReminderIdentityTest {
    @Test fun `same message replaces its own reminder`() {
        assertEquals(reminderKey("u", "g", "m"), reminderKey("u", "g", "m"))
    }
    @Test fun `identity is account chat and message scoped`() {
        val key = reminderKey("u", "g", "m")
        assertNotEquals(key, reminderKey("other", "g", "m"))
        assertNotEquals(key, reminderKey("u", "other", "m"))
        assertNotEquals(key, reminderKey("u", "g", "other"))
    }
    @Test fun `delimited components cannot collide by concatenation`() {
        assertNotEquals(reminderKey("ab", "c", "m"), reminderKey("a", "bc", "m"))
    }
    @Test fun `unique work name is opaque`() {
        assertTrue(reminderKey("secret-user", "secret-chat", "secret-message").matches(Regex("message-reminder-[0-9a-f]{64}")))
    }
}
