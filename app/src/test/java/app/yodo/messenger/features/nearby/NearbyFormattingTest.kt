package app.yodo.messenger.features.nearby

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NearbyFormattingTest {
    @Test fun `meters never rounded to kilometers early`() {
        assertEquals("0 м", formatNearbyDistance(0.0))
        assertEquals("999 м", formatNearbyDistance(999.9))
    }
    @Test fun `kilometers use one decimal and locale`() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("1.0 км", formatNearbyDistance(1000.0))
            assertEquals("12.3 км", formatNearbyDistance(12345.0))
        } finally { Locale.setDefault(old) }
    }
    @Test fun `invalid distance is unknown not zero`() {
        for (d in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertEquals("—", formatNearbyDistance(d))
    }
    @Test fun `cardinal directions`() {
        assertEquals("С", nearbyDirection(0.0, 0.0, 1.0, 0.0))
        assertEquals("В", nearbyDirection(0.0, 0.0, 0.0, 1.0))
        assertEquals("Ю", nearbyDirection(0.0, 0.0, -1.0, 0.0))
        assertEquals("З", nearbyDirection(0.0, 0.0, 0.0, -1.0))
    }
    @Test fun `diagonal directions`() {
        assertEquals("СВ", nearbyDirection(0.0, 0.0, 1.0, 1.0))
        assertEquals("ЮВ", nearbyDirection(0.0, 0.0, -1.0, 1.0))
        assertEquals("ЮЗ", nearbyDirection(0.0, 0.0, -1.0, -1.0))
        assertEquals("СЗ", nearbyDirection(0.0, 0.0, 1.0, -1.0))
    }
    @Test fun `coincident location is nearby`() { assertEquals("Рядом", nearbyDirection(55.0, 37.0, 55.0, 37.0)) }
    @Test fun `invalid coordinates do not produce a direction`() {
        assertEquals("—", nearbyDirection(Double.NaN, 0.0, 1.0, 1.0))
        assertEquals("—", nearbyDirection(0.0, 0.0, 91.0, 1.0))
    }
    @Test fun `coordinate validation respects world bounds`() {
        assertTrue(validNearbyCoordinates(90.0, -180.0))
        assertFalse(validNearbyCoordinates(90.1, 0.0))
        assertFalse(validNearbyCoordinates(0.0, 180.1))
        assertFalse(validNearbyCoordinates(0.0, Double.NEGATIVE_INFINITY))
    }
    @Test fun `initials tolerate whitespace and empty names`() {
        assertEquals("АП", nearbyInitials("  Артём   Петров "))
        assertEquals("А", nearbyInitials("Артём"))
        assertEquals("?", nearbyInitials("   "))
    }
    @Test fun `initials never split surrogate pair`() { assertEquals("😀А", nearbyInitials("😀 Алексей")) }
}
