package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.FaqQuestion
import app.yodo.messenger.domain.model.FaqSection
import app.yodo.messenger.domain.model.SupportFaqData
import org.junit.Assert.*
import org.junit.Test

class SupportFaqNavigationTest {
    private val section = FaqSection("remote", "Своя тема", "?", listOf(FaqQuestion("q", "Вопрос с сервера", "Ответ с сервера")))
    @Test fun `home exists even with an empty FAQ`() {
        assertFalse(supportFaqPage(SupportFaqScreen.SectionList, emptyList()).missing)
    }
    @Test fun `remote section title and questions are preserved`() {
        val page = supportFaqPage(SupportFaqScreen.QuestionList("remote"), listOf(section))
        assertEquals("Своя тема", page.title)
        assertEquals(section, page.section)
        assertFalse(page.missing)
    }
    @Test fun `answer uses both section and question identifiers`() {
        val page = supportFaqPage(SupportFaqScreen.Answer("remote", "q"), listOf(section))
        assertEquals("Ответ с сервера", page.question?.answer)
        assertFalse(page.missing)
    }
    @Test fun `removed section has an explicit missing state`() {
        assertTrue(supportFaqPage(SupportFaqScreen.QuestionList("removed"), listOf(section)).missing)
    }
    @Test fun `removed answer has an explicit missing state`() {
        assertTrue(supportFaqPage(SupportFaqScreen.Answer("remote", "removed"), listOf(section)).missing)
        assertTrue(supportFaqPage(SupportFaqScreen.Answer("removed", "q"), listOf(section)).missing)
    }
    @Test fun `other question is available without a server section`() {
        assertFalse(supportFaqPage(SupportFaqScreen.QuestionList(SupportFaqData.OTHER_SECTION_ID), emptyList()).missing)
    }
    @Test fun `reserved other section is not duplicated in topic cards`() {
        val other = section.copy(id = SupportFaqData.OTHER_SECTION_ID)
        assertEquals(listOf(section), supportFaqTopics(listOf(section, other)))
    }
    @Test fun `long answers are not truncated by navigation`() {
        val answer = "Длинный ответ\n".repeat(200)
        val longSection = section.copy(questions = listOf(FaqQuestion("q", "Вопрос", answer)))
        assertEquals(answer, supportFaqPage(SupportFaqScreen.Answer("remote", "q"), listOf(longSection)).question?.answer)
    }
}
