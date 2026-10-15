package app.yodo.messenger.domain.model

import org.junit.Assert.*
import org.junit.Test

class SharedChecklistTest {
    @Test fun `normalizes input`() {
        val list = SharedChecklist.create(" Shopping ", listOf(" Bread ", "Milk"))
        assertEquals("Shopping", list.title)
        assertEquals(listOf("Bread", "Milk"), list.items)
    }
    @Test fun `counts only known completed items`() {
        val list = SharedChecklist.fromFields("List", listOf("A", "B"), mapOf("0" to "alice", "1" to "", "99" to "bob", "01" to "mallory"))!!
        assertEquals(1, list.completedCount)
        assertEquals("alice", list.completedBy(0))
        assertNull(list.completedBy(1))
    }
    @Test fun `rejects malformed input`() {
        assertNull(SharedChecklist.fromFields("", listOf("A"), emptyMap<String, String>()))
        assertNull(SharedChecklist.fromFields("Title", listOf(3), null))
        assertNull(SharedChecklist.fromFields("Title", emptyList<String>(), null))
        assertNull(SharedChecklist.fromFields("Title", List(21) { "A" }, null))
        assertNull(SharedChecklist.fromFields("Title", listOf("x".repeat(201)), null))
    }
    @Test fun `supports exactly twenty items`() {
        assertEquals(20, SharedChecklist.create("Title", List(20) { "Item $it" }).items.size)
    }
    @Test fun `message preview includes progress`() {
        val message = Message("m", "g", "u", text = "", timestamp = 1L, status = MessageStatus.SENT,
            checklist = SharedChecklist("Tasks", listOf("A", "B"), mapOf("1" to "u")))
        assertEquals("☑ Tasks (1/2)", message.previewText())
        assertEquals("Сообщение удалено", message.copy(isDeleted = true).previewText())
    }
}
