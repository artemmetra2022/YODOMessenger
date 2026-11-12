package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryPagingTest {

    private fun message(
        id: String,
        timestamp: Long,
        text: String = "",
        imageBase64: String? = null,
        images: List<String> = emptyList(),
        voice: String? = null,
        fileBase64: String? = null,
        fileName: String? = null,
        isViewOnce: Boolean = false,
        viewOnceOpened: Boolean = false,
        isDeleted: Boolean = false
    ) = Message(
        id = id,
        chatId = "chat",
        senderId = "sender",
        text = text,
        timestamp = timestamp,
        status = MessageStatus.SENT,
        imageBase64 = imageBase64,
        imagesBase64 = images,
        voiceBase64 = voice,
        fileBase64 = fileBase64,
        fileName = fileName,
        isViewOnce = isViewOnce,
        viewOnceOpened = viewOnceOpened,
        isDeleted = isDeleted
    )

    private val dateOf: (Long) -> String = { millis -> if (millis < 1_000L) "day1" else "day2" }

    @Test
    fun `merge keeps live window copy and chronological order`() {
        val older = listOf(
            message("m2", 200, text = "старое"),
            message("m1", 100, text = "самое старое")
        )
        val window = listOf(
            message("m2", 200, text = "исправленное"),
            message("m3", 300, text = "новое")
        )

        val merged = mergeHistoryPages(older, window)

        assertEquals(listOf("m1", "m2", "m3"), merged.map { it.id })
        assertEquals("исправленное", merged.first { it.id == "m2" }.text)
    }

    @Test
    fun `merge without older pages returns window untouched`() {
        val window = listOf(message("m2", 200), message("m1", 100))
        assertEquals(window, mergeHistoryPages(emptyList(), window))
    }

    @Test
    fun `full page means there may be more history`() {
        assertTrue(hasOlderHistory(List(CHAT_HISTORY_PAGE_SIZE) { message("m$it", it.toLong()) }, CHAT_HISTORY_PAGE_SIZE))
        assertFalse(hasOlderHistory(List(CHAT_HISTORY_PAGE_SIZE - 1) { message("m$it", it.toLong()) }, CHAT_HISTORY_PAGE_SIZE))
        assertFalse(hasOlderHistory(emptyList(), CHAT_HISTORY_PAGE_SIZE))
    }

    @Test
    fun `media tab includes photos albums and voice but not opened one time photos`() {
        assertTrue(message("photo", 1, imageBase64 = "img").belongsTo(ChatMediaTab.MEDIA))
        assertTrue(message("album", 1, images = listOf("a", "b")).belongsTo(ChatMediaTab.MEDIA))
        assertTrue(message("voice", 1, voice = "v").belongsTo(ChatMediaTab.MEDIA))
        assertFalse(message("opened", 1, imageBase64 = "img", isViewOnce = true, viewOnceOpened = true)
            .belongsTo(ChatMediaTab.MEDIA))
        assertTrue(message("viewonce", 1, imageBase64 = "img", isViewOnce = true).belongsTo(ChatMediaTab.MEDIA))
        assertFalse(message("text", 1, text = "привет").belongsTo(ChatMediaTab.MEDIA))
    }

    @Test
    fun `files and links tabs use their own fields`() {
        val document = message("file", 1, fileName = "report.pdf", fileBase64 = "data")
        val link = message("link", 1, text = "смотри https://yodo.app/release")
        val plain = message("plain", 1, text = "без ссылок")

        assertTrue(document.belongsTo(ChatMediaTab.FILES))
        assertFalse(document.belongsTo(ChatMediaTab.LINKS))
        assertTrue(link.belongsTo(ChatMediaTab.LINKS))
        assertFalse(link.belongsTo(ChatMediaTab.FILES))
        assertFalse(plain.belongsTo(ChatMediaTab.LINKS))
        assertFalse(plain.belongsTo(ChatMediaTab.FILES))
    }

    @Test
    fun `deleted messages belong to no tab`() {
        val deleted = message("deleted", 1, fileBase64 = "data", fileName = "a.txt", isDeleted = true, text = "https://x.yz")
        for (tab in ChatMediaTab.values()) assertFalse(deleted.belongsTo(tab))
    }

    @Test
    fun `lazy item index counts date separators`() {
        val messages = listOf(
            message("a", 100),
            message("b", 200),
            message("c", 1_500),
            message("d", 1_600)
        )

        assertEquals(1, lazyItemIndexForMessage(messages, "a", dateOf))
        assertEquals(2, lazyItemIndexForMessage(messages, "b", dateOf))
        // перед "c" появляется разделитель второй даты
        assertEquals(4, lazyItemIndexForMessage(messages, "c", dateOf))
        assertEquals(5, lazyItemIndexForMessage(messages, "d", dateOf))
        assertEquals(-1, lazyItemIndexForMessage(messages, "missing", dateOf))
    }
}
