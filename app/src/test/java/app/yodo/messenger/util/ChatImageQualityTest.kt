package app.yodo.messenger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImageQualityTest {

    @Test
    fun `profiles use progressively larger dimensions and quality`() {
        val qualities = ChatImageQuality.entries

        assertEquals(3, qualities.size)
        assertTrue(qualities.zipWithNext().all { (first, second) ->
            first.maxDimension < second.maxDimension &&
                first.startingQuality < second.startingQuality
        })
    }

    @Test
    fun `high quality preserves the existing chat image settings`() {
        assertEquals(1600, ChatImageQuality.HIGH.maxDimension)
        assertEquals(92, ChatImageQuality.HIGH.startingQuality)
    }
}
