package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStatusResolverTest {
    @Test fun `new local write is sending until acknowledged`() {
        assertEquals(MessageStatus.SENDING, resolveMessageStatus("SENT", true))
        assertEquals(MessageStatus.SENT, resolveMessageStatus("SENT", false))
    }
    @Test fun `pending reaction does not regress acknowledged receipt`() {
        assertEquals(MessageStatus.DELIVERED, resolveMessageStatus("DELIVERED", true))
        assertEquals(MessageStatus.READ, resolveMessageStatus("READ", true))
        assertEquals(MessageStatus.SENT, resolveMessageStatus("SENT", true, serverAcknowledged = true))
    }
    @Test fun `incoming receipt write is never outgoing sending`() {
        assertEquals(MessageStatus.SENT, resolveMessageStatus("SENT", true, isOwnMessage = false))
    }
    @Test fun `unknown legacy status is safe`() {
        assertEquals(MessageStatus.SENT, resolveMessageStatus(null, false))
        assertEquals(MessageStatus.SENT, resolveMessageStatus("UNKNOWN", false))
        assertEquals(MessageStatus.FAILED, resolveMessageStatus("FAILED", false))
    }
    @Test fun `group receipt means every recipient not just first reader`() {
        val recipients = listOf("a", "b")
        assertEquals(MessageStatus.SENT, aggregateReceiptStatus(recipients, listOf("a"), listOf("a")))
        assertEquals(MessageStatus.DELIVERED, aggregateReceiptStatus(recipients, recipients, listOf("a")))
        assertEquals(MessageStatus.READ, aggregateReceiptStatus(recipients, recipients, recipients))
    }
    @Test fun `read also counts as delivery`() {
        assertEquals(MessageStatus.DELIVERED, aggregateReceiptStatus(listOf("a", "b"), listOf("a"), listOf("b")))
        assertEquals(MessageStatus.READ, aggregateReceiptStatus(listOf("a"), emptyList(), listOf("a")))
    }
    @Test fun `saved messages without recipients do not claim read`() {
        assertEquals(MessageStatus.SENT, aggregateReceiptStatus(emptyList(), emptyList(), emptyList()))
    }
}
