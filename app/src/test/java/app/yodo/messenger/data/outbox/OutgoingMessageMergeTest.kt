package app.yodo.messenger.data.outbox

import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import org.junit.Assert.*
import org.junit.Test

class OutgoingMessageMergeTest {
    private fun msg(id: String, time: Long, status: MessageStatus = MessageStatus.SENDING) =
        Message(id, "chat", "alice", text = id, timestamp = time, status = status)

    @Test fun `burst retains all pending messages in order`() {
        val local = (1..16).map { msg("id$it", it.toLong()) }
        assertEquals(local, mergeOutgoingMessages(emptyList(), local))
    }
    @Test fun `acknowledgement replaces pending bubble without duplicate`() {
        val acknowledged = msg("one", 2, MessageStatus.SENT)
        assertEquals(listOf(acknowledged), mergeOutgoingMessages(listOf(acknowledged), listOf(msg("one", 1))))
    }
    @Test fun `local error wins over unconfirmed cache write`() {
        val failed = msg("one", 1, MessageStatus.FAILED)
        assertEquals(listOf(failed), mergeOutgoingMessages(listOf(msg("one", 1)), listOf(failed)))
    }
    @Test fun `remote read wins over stale failed request`() {
        val read = msg("one", 1, MessageStatus.READ)
        assertEquals(listOf(read), mergeOutgoingMessages(listOf(read), listOf(msg("one", 1, MessageStatus.FAILED))))
    }
    @Test fun `failed first message never hides following queued text`() {
        val local = listOf(msg("one", 1, MessageStatus.FAILED), msg("two", 2), msg("three", 3))
        assertEquals(local, mergeOutgoingMessages(emptyList(), local))
    }
    @Test fun `equal timestamps have deterministic order`() {
        assertEquals(listOf("a", "b", "c"), mergeOutgoingMessages(listOf(msg("c", 1)), listOf(msg("b", 1), msg("a", 1))).map { it.id })
    }
    @Test fun `scroll target counts date headers`() {
        val messages = listOf(msg("one", 1), msg("two", 1), msg("three", 2))
        assertEquals(4, lastMessageItemIndex(messages) { "day$it" })
        assertEquals(1, lastMessageItemIndex(messages.take(1)) { "day$it" })
        assertEquals(-1, lastMessageItemIndex(emptyList()) { "day$it" })
    }
}
