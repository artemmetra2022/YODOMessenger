package app.yodo.messenger.features.update

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * НОВОЕ: описания релизов на GitHub пишутся в Markdown, а в приложении тело релиза
 * показывалось как обычный текст — заголовки «### Что нового», выделение «**жирным**»
 * и разделители «---» попадали в интерфейс вместе со служебными символами.
 *
 * Здесь минимальный разбор именно того, что встречается в описаниях релизов:
 * заголовки (делаем жирными, маркеры убираем), списки (маркер → «•»),
 * цитаты, горизонтальные разделители, ссылки и выделение «**жирным**».
 * Полноценный Markdown-парсер тут не нужен и намеренно не подключается.
 */
fun releaseNotesAnnotated(markdown: String): AnnotatedString = buildAnnotatedString {
    val lines = markdown.lines()
    lines.forEachIndexed { index, raw ->
        val line = raw.trim()
        // Разделитель «---» заменяем пустой строкой, а не выводим как есть.
        val isDivider = line.length >= 3 && line.all { it == '-' }
        val rendered = when {
            line.isEmpty() || isDivider -> ""
            line.startsWith("#") -> line.trimStart('#').trim()
            line.startsWith("> ") -> line.removePrefix("> ").trim()
            line.startsWith("- ") || line.startsWith("* ") -> "• " + line.drop(2).trim()
            else -> line
        }

        val isHeading = line.startsWith("#")
        if (index > 0) append('\n')
        if (rendered.isEmpty()) return@forEachIndexed

        if (isHeading) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(rendered) }
        } else {
            appendInline(rendered)
        }
    }
}

/**
 * Дописывает строку, превращая «**жирный**» в реально жирный участок, а также
 * убирая обратные кавычки и оставляя от ссылок «[текст](url)» только текст.
 */
private fun AnnotatedString.Builder.appendInline(line: String) {
    val withoutLinks = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(line) { it.groupValues[1] }
    val text = withoutLinks.replace("`", "")

    var position = 0
    while (position < text.length) {
        val start = text.indexOf("**", position)
        if (start < 0) {
            append(text.substring(position))
            break
        }
        val end = text.indexOf("**", start + 2)
        if (end < 0) {
            append(text.substring(position))
            break
        }
        append(text.substring(position, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(start + 2, end))
        }
        position = end + 2
    }
}
