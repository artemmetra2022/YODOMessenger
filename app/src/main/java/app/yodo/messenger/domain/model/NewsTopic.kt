package app.yodo.messenger.domain.model

/**
 * НОВОЕ (лента новостей официального канала): тема новости («Плитка»).
 *
 * Чтобы не менять всю цепочку отправки сообщений (репозиторий, Firestore-схему),
 * тема кодируется лёгким префиксом в начале текста сообщения:
 *   ⟦news:Новые функции⟧Текст новости...
 * При рендере в официальном канале префикс разбирается: тема показывается
 * отдельной цветной плиткой, а остальной текст — как тело новости.
 */
object NewsTopic {
    // Доступные темы новостей для плиток официального канала.
    val TOPICS = listOf(
        "Новые функции",
        "Исправления",
        "Анонсы",
        "Важное",
        "Обновление"
    )

    private const val PREFIX = "\u27E6news:"
    private const val SUFFIX = "\u27E7"

    // Собирает текст сообщения с закодированной темой (или без неё).
    fun encode(topic: String?, body: String): String =
        if (topic.isNullOrBlank()) body else "$PREFIX$topic$SUFFIX$body"

    // Разбирает текст: возвращает (тема или null, тело новости без префикса).
    fun decode(text: String): Pair<String?, String> {
        if (text.startsWith(PREFIX)) {
            val end = text.indexOf(SUFFIX)
            if (end > PREFIX.length) {
                val topic = text.substring(PREFIX.length, end)
                val body = text.substring(end + SUFFIX.length)
                return topic to body
            }
        }
        return null to text
    }
}
