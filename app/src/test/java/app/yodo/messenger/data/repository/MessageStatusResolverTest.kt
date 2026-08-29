package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStatusResolverTest {

    @Test
    fun `pending Firestore write is shown as sending`() {
        assertEquals(MessageStatus.SENDING, resolveMessageStatus("SENT", hasPendingWrites = true))
    }

    @Test
    fun `acknowledged Firestore write uses stored status`() {
        assertEquals(MessageStatus.SENT, resolveMessageStatus("SENT", hasPendingWrites = false))
        assertEquals(MessageStatus.DELIVERED, resolveMessageStatus("DELIVERED", hasPendingWrites = false))
        assertEquals(MessageStatus.READ, resolveMessageStatus("READ", hasPendingWrites = false))
    }

    @Test
    fun `pending write always shows sending even if stored status is more advanced`() {
        assertEquals(MessageStatus.SENDING, resolveMessageStatus("DELIVERED", hasPendingWrites = true))
        assertEquals(MessageStatus.SENDING, resolveMessageStatus("READ", hasPendingWrites = true))
    }

    @Test
    fun `missing or unknown stored status falls back to sent`() {
        assertEquals(MessageStatus.SENT, resolveMessageStatus(null, hasPendingWrites = false))
        assertEquals(MessageStatus.SENT, resolveMessageStatus("UNKNOWN", hasPendingWrites = false))
    }
}
