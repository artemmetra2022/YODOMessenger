package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageGroupingTest {
    @Test
    fun `group positions follow consecutive sender runs`() {
        val messages = listOf(message("a", "one"), message("a", "two"), message("b", "three"), message("a", "four"))

        assertEquals(MessageGroupPosition.FIRST, messageGroupPosition(messages, 0))
        assertEquals(MessageGroupPosition.LAST, messageGroupPosition(messages, 1))
        assertEquals(MessageGroupPosition.SINGLE, messageGroupPosition(messages, 2))
        assertEquals(MessageGroupPosition.SINGLE, messageGroupPosition(messages, 3))
    }

    @Test
    fun `spacing is compact only inside sender run`() {
        val messages = listOf(message("a", "one"), message("a", "two"), message("b", "three"))

        assertEquals(8, messageItemSpacing(messages, 0))
        assertEquals(2, messageItemSpacing(messages, 1))
        assertEquals(8, messageItemSpacing(messages, 2))
    }

    private fun message(senderId: String, id: String) = Message(
        id = id, chatId = "chat", senderId = senderId, text = id,
        timestamp = 0L, status = MessageStatus.SENT
    )
}
