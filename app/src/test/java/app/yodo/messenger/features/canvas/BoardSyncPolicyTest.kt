package app.yodo.messenger.features.canvas

import org.junit.Assert.*
import org.junit.Test

class BoardSyncPolicyTest {
    @Test fun missingSettingKeepsRealtime() = assertEquals(0L, BoardSyncPolicy.normalize(null))
    @Test fun validIntervalsArePreserved() {
        for (value in listOf(0L, 1L, 250L, 1000L, 5000L)) assertEquals(value, BoardSyncPolicy.normalize(value))
    }
    @Test fun corruptedSettingsFallBackToRealtime() {
        for (value in listOf(-1L, 5001L, Long.MAX_VALUE, Long.MIN_VALUE)) assertEquals(0L, BoardSyncPolicy.normalize(value))
    }
}
