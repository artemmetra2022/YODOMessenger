package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTimelineTest {

    @Test
    fun `timeline count includes one date separator for messages from the same day`() {
        val timestamp = System.currentTimeMillis()
        val messages = listOf(
            message(id = "first", timestamp = timestamp),
            message(id = "second", timestamp = timestamp + 1_000)
        )

        assertEquals(3, chatTimelineItemCount(messages))
    }

    @Test
    fun `timeline count includes each date separator across multiple days`() {
        val timestamp = System.currentTimeMillis()
        val messages = listOf(
            message(id = "first", timestamp = timestamp - 3 * DAY_MILLIS),
            message(id = "second", timestamp = timestamp)
        )

        assertEquals(4, chatTimelineItemCount(messages))
    }

    private fun message(id: String, timestamp: Long) = Message(
        id = id,
        chatId = "chat",
        senderId = "sender",
        text = id,
        timestamp = timestamp,
        status = MessageStatus.SENT
    )

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
