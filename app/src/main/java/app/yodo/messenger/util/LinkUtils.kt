package app.yodo.messenger.util

import java.util.regex.Pattern

private val URL_PATTERN: Pattern = Pattern.compile(
    "(https?://[\\w\\-.]+(?:\\.[a-zA-Z]{2,})(?::\\d+)?(?:[/?#][^\\s]*)?)",
    Pattern.CASE_INSENSITIVE
)

/**
 * Находит первую http(s)-ссылку в тексте сообщения (для превью ссылок).
 * Как в большинстве мессенджеров, показываем превью только для первой ссылки,
 * даже если в сообщении их несколько.
 */
fun extractFirstUrl(text: String): String? {
    if (text.isBlank()) return null
    val matcher = URL_PATTERN.matcher(text)
    return if (matcher.find()) matcher.group(1) else null
}
