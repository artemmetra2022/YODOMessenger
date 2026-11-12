package app.yodo.messenger.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LastSeenFormatterTest {
    private val now = 1_800_000_000_000L
    private fun ago(sec: Long) = LastSeenFormatter.format(now - sec * 1000, now)

    @Test fun underMinuteIsRecently() {
        assertEquals("недавно", ago(0)); assertEquals("недавно", ago(10)); assertEquals("недавно", ago(59))
    }
    @Test fun fewMinutes() { assertEquals("несколько минут назад", ago(60)); assertEquals("несколько минут назад", ago(299)) }
    @Test fun minutesRoundedToFive() {
        assertEquals("5 мин назад", ago(5 * 60)); assertEquals("5 мин назад", ago(9 * 60 + 59))
        assertEquals("10 мин назад", ago(12 * 60)); assertEquals("55 мин назад", ago(59 * 60))
    }
    @Test fun hours() { assertEquals("1 ч назад", ago(3600)); assertEquals("23 ч назад", ago(23 * 3600 + 59 * 60)) }
}
