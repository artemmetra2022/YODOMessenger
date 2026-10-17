package app.yodo.messenger.features.chats

import org.junit.Assert.*
import org.junit.Test

class ChatMenuPolicyTest {
    private fun ids(type: String, admin: Boolean = false, owner: Boolean = false,
        chatId: String = "chat", other: Boolean = false) = chatMenuIds(ChatMenuContext(chatId, type, admin, owner, other))
    @Test fun `common tools retained for all chat types`() {
        for (type in listOf("PRIVATE", "GROUP", "CHANNEL", "SUPPORT")) {
            assertTrue(ids(type).containsAll(listOf(ChatMenuId.SEARCH, ChatMenuId.BOARD, ChatMenuId.CHAT_STATS,
                ChatMenuId.EXPORT, ChatMenuId.SCREENSHOT, ChatMenuId.CLEAR)))
        }
    }
    @Test fun `private conversation has security and contact actions`() {
        assertTrue(ids("PRIVATE", other = true).containsAll(listOf(ChatMenuId.PROFILE, ChatMenuId.VERIFY_KEYS, ChatMenuId.SHARE_CONTACT, ChatMenuId.DISAPPEARING)))
    }
    @Test fun `support prefix and saved messages never show key verification`() {
        assertFalse(ChatMenuId.VERIFY_KEYS in ids("PRIVATE", chatId = "support_uid"))
        assertFalse(ChatMenuId.VERIFY_KEYS in ids("PRIVATE", chatId = "saved_uid"))
        assertFalse(ChatMenuId.VERIFY_KEYS in ids("SUPPORT"))
    }
    @Test fun `private contact action needs another user`() { assertFalse(ChatMenuId.SHARE_CONTACT in ids("PRIVATE")) }
    @Test fun `group has information but not channel administration`() {
        val ids = ids("GROUP", admin = true)
        assertTrue(ChatMenuId.PROFILE in ids)
        assertFalse(ChatMenuId.INVITE in ids)
        assertFalse(ChatMenuId.CHANNEL_STATS in ids)
        assertFalse(ChatMenuId.DELETE_CHANNEL in ids)
    }
    @Test fun `channel reader cannot access administration`() {
        val ids = ids("CHANNEL")
        assertFalse(ids.any { it in listOf(ChatMenuId.INVITE, ChatMenuId.SCHEDULED, ChatMenuId.CHANNEL_STATS, ChatMenuId.DELETE_CHANNEL) })
        assertTrue(ChatMenuId.SUBSCRIPTION in ids)
    }
    @Test fun `channel admin can manage but not delete channel unless owner`() {
        val ids = ids("CHANNEL", admin = true)
        assertTrue(ids.containsAll(listOf(ChatMenuId.INVITE, ChatMenuId.SCHEDULED, ChatMenuId.CHANNEL_STATS)))
        assertFalse(ChatMenuId.DELETE_CHANNEL in ids)
        assertTrue(ChatMenuId.DELETE_CHAT in ids)
    }
    @Test fun `channel owner has delete channel and no unsubscribe`() {
        val ids = ids("CHANNEL", admin = true, owner = true)
        assertTrue(ChatMenuId.DELETE_CHANNEL in ids)
        assertFalse(ChatMenuId.DELETE_CHAT in ids)
        assertFalse(ChatMenuId.SUBSCRIPTION in ids)
    }
    @Test fun `channel has no disappearing or contact actions`() {
        val ids = ids("CHANNEL", other = true)
        assertFalse(ChatMenuId.DISAPPEARING in ids)
        assertFalse(ChatMenuId.SHARE_CONTACT in ids)
    }
    @Test fun `inconsistent owner flag does not grant channel actions in private chat`() {
        assertFalse(ChatMenuId.DELETE_CHANNEL in ids("PRIVATE", owner = true))
    }
    @Test fun `menu entries are unique`() {
        for (type in listOf("PRIVATE", "GROUP", "CHANNEL", "SUPPORT")) {
            val menu = ids(type, admin = true, owner = true, other = true)
            assertEquals(menu.size, menu.distinct().size)
        }
    }
    @Test fun `most useful actions come before dangerous commands`() {
        for (type in listOf("PRIVATE", "GROUP", "CHANNEL")) {
            val menu = ids(type, admin = true, owner = true, other = true)
            assertTrue(menu.indexOf(ChatMenuId.SEARCH) < menu.indexOf(ChatMenuId.CLEAR))
            assertTrue(menu.indexOf(ChatMenuId.BOARD) < menu.indexOf(ChatMenuId.CLEAR))
            assertTrue(menu.last() in listOf(ChatMenuId.DELETE_CHAT, ChatMenuId.DELETE_CHANNEL))
        }
    }
}
