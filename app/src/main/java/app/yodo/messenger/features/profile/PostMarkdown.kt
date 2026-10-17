package app.yodo.messenger.features.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * НОВОЕ: лёгкая markdown-подобная разметка для постов — без внешних зависимостей.
 * Поддерживается:
 *   **жирный**, *курсив*, ~~зачёркнутый~~, [текст](url)
 *   # / ## / ### Заголовок   (только в начале строки)
 *   - пункт   или   1. пункт   (простые списки)
 *   > цитата
 * Разметка хранится прямо в Post.text как обычный текст — совместима со старыми
 * постами (они просто не содержат спецсимволов и отрисуются как есть).
 */
object PostMarkdown {

    // Разметка, которую вставляет тулбар редактора при форматировании выделения.
    const val BOLD_MARK = "**"
    const val ITALIC_MARK = "*"
    const val STRIKE_MARK = "~~"
    const val QUOTE_PREFIX = "> "
    const val BULLET_PREFIX = "- "
    const val HEADING_PREFIX = "# "

    private val linkRegex = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
    private val boldRegex = Regex("""\*\*(.+?)\*\*""")
    private val strikeRegex = Regex("""~~(.+?)~~""")
    private val italicRegex = Regex("""(?<!\*)\*(?!\*)([^*]+?)\*(?!\*)""")

    /**
     * Рендерит одну строку (уже без ведущих markdown-маркеров заголовка/списка/цитаты)
     * с инлайн-разметкой (жирный/курсив/зачёркнутый/ссылка) в AnnotatedString.
     */
    private fun renderInline(
        builder: androidx.compose.ui.text.AnnotatedString.Builder,
        line: String,
        linkColor: Color
    ) {
        // Собираем все инлайн-совпадения (ссылки, жирный, курсив, зачёркнутый) и красим по порядку,
        // без вложенной рекурсии — этого достаточно для постов (без двойной вложенной разметки).
        data class Token(val range: IntRange, val render: () -> Unit)

        val tokens = mutableListOf<Token>()
        for (m in linkRegex.findAll(line)) {
            val label = m.groupValues[1]; val url = m.groupValues[2]
            tokens += Token(m.range) {
                builder.withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    builder.append(label)
                }
            }
        }
        for (m in boldRegex.findAll(line)) {
            if (tokens.any { it.range.first <= m.range.first && it.range.last >= m.range.last }) continue
            tokens += Token(m.range) {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { builder.append(m.groupValues[1]) }
            }
        }
        for (m in strikeRegex.findAll(line)) {
            if (tokens.any { it.range.first <= m.range.first && it.range.last >= m.range.last }) continue
            tokens += Token(m.range) {
                builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { builder.append(m.groupValues[1]) }
            }
        }
        for (m in italicRegex.findAll(line)) {
            if (tokens.any { it.range.first <= m.range.first && it.range.last >= m.range.last }) continue
            tokens += Token(m.range) {
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { builder.append(m.groupValues[1]) }
            }
        }
        tokens.sortBy { it.range.first }

        var cursor = 0
        for (t in tokens) {
            if (t.range.first < cursor) continue
            if (t.range.first > cursor) builder.append(line.substring(cursor, t.range.first))
            t.render()
            cursor = t.range.last + 1
        }
        if (cursor < line.length) builder.append(line.substring(cursor))
    }

    /** Разбирает текст поста построчно и строит готовый AnnotatedString для отображения. */
    fun render(text: String, linkColor: Color, quoteColor: Color): AnnotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, rawLine ->
            when {
                rawLine.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    renderInline(this, rawLine.removePrefix("### "), linkColor)
                }
                rawLine.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                    renderInline(this, rawLine.removePrefix("## "), linkColor)
                }
                rawLine.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                    renderInline(this, rawLine.removePrefix("# "), linkColor)
                }
                rawLine.startsWith("> ") -> withStyle(SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)) {
                    append("▍ ")
                    renderInline(this, rawLine.removePrefix("> "), linkColor)
                }
                rawLine.startsWith("- ") || rawLine.startsWith("* ") -> {
                    append("•  ")
                    renderInline(this, rawLine.drop(2), linkColor)
                }
                Regex("""^\d+\.\s""").containsMatchIn(rawLine) -> {
                    val m = Regex("""^(\d+)\.\s(.*)$""").find(rawLine)
                    if (m != null) {
                        append("${m.groupValues[1]}.  ")
                        renderInline(this, m.groupValues[2], linkColor)
                    } else renderInline(this, rawLine, linkColor)
                }
                else -> renderInline(this, rawLine, linkColor)
            }
            if (index != lines.lastIndex) append("\n")
        }
    }
}

/** Composable-хелпер: рендерит текст поста с учётом текущей темы (цвет ссылок/цитат). */
@Composable
fun rememberPostAnnotatedText(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(text, linkColor, quoteColor) { PostMarkdown.render(text, linkColor, quoteColor) }
}
