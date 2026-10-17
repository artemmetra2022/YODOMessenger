package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.FaqQuestion
import app.yodo.messenger.domain.model.FaqSection
import app.yodo.messenger.domain.model.SupportFaqData

sealed class SupportFaqScreen {
    object SectionList : SupportFaqScreen()
    data class QuestionList(val sectionId: String) : SupportFaqScreen()
    data class Answer(val sectionId: String, val questionId: String) : SupportFaqScreen()
}

internal data class SupportFaqPage(val title: String, val section: FaqSection? = null,
    val question: FaqQuestion? = null, val missing: Boolean = false)

internal fun supportFaqPage(screen: SupportFaqScreen, sections: List<FaqSection>): SupportFaqPage = when (screen) {
    SupportFaqScreen.SectionList -> SupportFaqPage("С чего начнём?")
    is SupportFaqScreen.QuestionList -> {
        val section = sections.firstOrNull { it.id == screen.sectionId }
        if (screen.sectionId == SupportFaqData.OTHER_SECTION_ID) SupportFaqPage("Другой вопрос")
        else SupportFaqPage(section?.title ?: "Раздел недоступен", section, missing = section == null)
    }
    is SupportFaqScreen.Answer -> {
        val section = sections.firstOrNull { it.id == screen.sectionId }
        val question = section?.questions?.firstOrNull { it.id == screen.questionId }
        SupportFaqPage(section?.title ?: "Ответ недоступен", section, question, missing = question == null)
    }
}

internal fun supportFaqTopics(sections: List<FaqSection>): List<FaqSection> =
    sections.filter { it.id != SupportFaqData.OTHER_SECTION_ID }
