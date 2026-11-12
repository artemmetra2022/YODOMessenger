package app.yodo.messenger.domain.model

import org.junit.Assert.*
import org.junit.Test

class UnreadBadgeTest {
    private fun message(id: String, time: Long = 100L, sender: String = "bob") =
        Message(id, "chat", sender, text = "test", timestamp = time, status = MessageStatus.SENT)

    @Test fun `all Firestore numeric representations are supported`() {
        assertEquals(3, normalizedUnreadCount(3L))
        assertEquals(3, normalizedUnreadCount(3))
        assertEquals(3, normalizedUnreadCount(3.0))
    }
    @Test fun `invalid counters never produce a negative or overflow badge`() {
        assertEquals(0, normalizedUnreadCount(null))
        assertEquals(0, normalizedUnreadCount(-5L))
        assertEquals(0, normalizedUnreadCount("5"))
        assertEquals(Int.MAX_VALUE, normalizedUnreadCount(Long.MAX_VALUE))
    }
    @Test fun `current tail must actually be visible`() {
        val tail = UnreadTail(2, "newest", 100, "bob")
        assertFalse(isUnreadTailVisible(tail, listOf(message("older"))))
        assertTrue(isUnreadTailVisible(tail, listOf(message("older"), message("newest"))))
    }
    @Test fun `same timestamp never bypasses exact id check`() {
        assertFalse(isUnreadTailVisible(UnreadTail(1, "newest", 100, "bob"), listOf(message("old", 100))))
    }
    @Test fun `legacy chat without id can clear by verified time and sender`() {
        assertTrue(isUnreadTailVisible(UnreadTail(2, null, 100, "bob"), listOf(message("legacy"))))
        assertFalse(isUnreadTailVisible(UnreadTail(2, null, 100, "alice"), listOf(message("legacy"))))
        assertFalse(isUnreadTailVisible(UnreadTail(2, null, 101, "bob"), listOf(message("legacy"))))
    }
    @Test fun `empty history is not evidence that unseen messages were read`() {
        assertFalse(isUnreadTailVisible(UnreadTail(2, "newest", 100), emptyList()))
        assertFalse(isUnreadTailVisible(UnreadTail(2), listOf(message("old"))))
    }
    @Test fun `reading a topic keeps other topics unread`() {
        assertEquals(4, remainingUnreadAfterTopicRead(7, 3))
        assertEquals(0, remainingUnreadAfterTopicRead(3, 3))
        assertEquals(0, remainingUnreadAfterTopicRead(2, 5))
    }
    @Test fun `topic counters cannot increase badge or overflow`() {
        assertEquals(7, remainingUnreadAfterTopicRead(7, -1))
        assertEquals(0, remainingUnreadAfterTopicRead(-1, 5))
        assertEquals(0, remainingUnreadAfterTopicRead(Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
