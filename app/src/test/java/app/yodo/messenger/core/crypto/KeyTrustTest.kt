package app.yodo.messenger.core.crypto

import org.junit.Assert.*
import org.junit.Test

class KeyTrustTest {
    @Test fun `first use is not verification`() { assertEquals(KeyTrustDecision.FIRST_USE, keyTrustDecision(null, "NEW")) }
    @Test fun `same key is unchanged`() { assertEquals(KeyTrustDecision.UNCHANGED, keyTrustDecision("KEY", "KEY")) }
    @Test fun `changed key is never accepted as first use`() { assertEquals(KeyTrustDecision.CHANGED, keyTrustDecision("OLD", "NEW")) }
    @Test fun `fingerprint is SHA256`() {
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", keyFingerprint("abc".toByteArray()))
    }
    @Test fun `display grouping does not lose digits`() {
        val raw = "A".repeat(64)
        assertEquals(raw, formattedFingerprint(raw).replace(" ", ""))
    }
    @Test fun `changed state includes prior fingerprint`() {
        val state = ConversationSecurity("OWN", "NEW", "OLD", false, true, "OWN")
        assertTrue(state.peerKeyChanged)
        assertFalse(state.peerVerified)
    }
}
