package app.yodo.messenger.features.chats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yodo.messenger.domain.model.FaqSection
import app.yodo.messenger.domain.model.SupportFaqData
import app.yodo.messenger.ui.components.SupportBotAvatar
import app.yodo.messenger.ui.components.supportAccentContent
import app.yodo.messenger.ui.theme.ColorTheme

@Composable
internal fun SupportFaqPanel(
    screen: SupportFaqScreen,
    sections: List<FaqSection>,
    colorTheme: ColorTheme,
    onSelectSection: (String) -> Unit,
    onSelectQuestion: (String, String) -> Unit,
    onBackToSections: () -> Unit,
    onBackToQuestions: (String) -> Unit,
    onContactOperator: () -> Unit,
    onCollapse: () -> Unit
) {
    val page = supportFaqPage(screen, sections)
    val listState = remember(screen) { LazyListState() }
    val config = LocalConfiguration.current
    val fraction = if (config.screenHeightDp < 500 || config.fontScale > 1.3f) .86f else .64f
    val maximum = (config.screenHeightDp * fraction).dp.coerceAtMost(640.dp)
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().fillMaxWidth().heightIn(max = maximum)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportBotAvatar(size = 44.dp, accent = colorTheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Помощник Yodo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Ответы на частые вопросы", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onCollapse) { Icon(Icons.Default.Close, "Свернуть помощника") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            // The whole content scrolls; long remote answers and large fonts cannot hide actions.
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (screen !is SupportFaqScreen.SectionList) {
                            TextButton(onClick = {
                                when (screen) {
                                    is SupportFaqScreen.Answer -> onBackToQuestions(screen.sectionId)
                                    else -> onBackToSections()
                                }
                            }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (screen is SupportFaqScreen.Answer) "К вопросам" else "Все темы")
                            }
                        }
                        Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (screen is SupportFaqScreen.SectionList) Text("Выберите тему — покажем готовые ответы. Если их недостаточно, можно написать оператору.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when {
                    page.missing -> item(key = "missing") {
                        Text("Этот материал больше недоступен. Вернитесь к темам или опишите вопрос оператору.",
                            style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onBackToSections) { Text("Открыть темы") }
                    }
                    screen is SupportFaqScreen.SectionList -> {
                        items(supportFaqTopics(sections), key = { "section_${it.id}" }) { section ->
                            SupportTopicCard(section.emoji, section.title,
                                "Вопросов: ${section.questions.size}") { onSelectSection(section.id) }
                        }
                        item(key = "other") {
                            SupportTopicCard("?", "Другой вопрос", "Если не нашли нужную тему") {
                                onSelectSection(SupportFaqData.OTHER_SECTION_ID)
                            }
                        }
                    }
                    screen is SupportFaqScreen.QuestionList -> {
                        if (screen.sectionId == SupportFaqData.OTHER_SECTION_ID) {
                            item(key = "operator_info") {
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text("Опишите проблему своими словами. Оператор ответит в этом же чате. Не отправляйте пароли и коды входа.",
                                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        } else if (page.section?.questions.isNullOrEmpty()) {
                            item(key = "empty") { Text("В этом разделе пока нет вопросов. Можно обратиться к оператору.", style = MaterialTheme.typography.bodyLarge) }
                        } else {
                            items(page.section!!.questions, key = { "question_${it.id}" }) { question ->
                                SupportTopicCard(null, question.question, "Открыть ответ") {
                                    onSelectQuestion(screen.sectionId, question.id)
                                }
                            }
                        }
                    }
                    screen is SupportFaqScreen.Answer -> {
                        item(key = "answer") {
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(page.question?.question.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    SelectionContainer {
                                        Text(page.question?.answer.orEmpty(), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Always reachable, including when the FAQ has a long answer.
            Button(onClick = onContactOperator, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 12.dp).heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = colorTheme.primary, contentColor = supportAccentContent(colorTheme.primary))) {
                Icon(Icons.Default.SupportAgent, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (screen is SupportFaqScreen.Answer) "Ответ не помог — написать оператору" else "Написать оператору", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SupportTopicCard(symbol: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (symbol != null) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(symbol, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun SupportWelcomeCard(modifier: Modifier = Modifier, operatorMode: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxHeight < 200.dp) {
            Text("Опишите вопрос оператору в поле ниже", modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportBotAvatar(size = 64.dp)
                Text("Центр помощи Yodo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(if (operatorMode) "Опишите вопрос в поле ниже. Оператор ответит в этом чате."
                    else "Готовые ответы в помощнике — или связь с оператором, если нужна помощь.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
