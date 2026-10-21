package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.util.extractFirstUrl

/** Сколько сообщений держим в «живом» окне и сколько читаем за одну страницу вверх. */
internal const val CHAT_HISTORY_PAGE_SIZE = 50

/**
 * НОВОЕ (пагинация истории): склеивает уже загруженные старые страницы с живым окном
 * последних сообщений. Живое окно всегда важнее: в нём актуальные статусы доставки,
 * правки и реакции, поэтому его запись перекрывает копию из старой страницы.
 * Результат — строго по возрастанию timestamp (как ждёт LazyColumn чата).
 */
internal fun mergeHistoryPages(older: List<Message>, window: List<Message>): List<Message> {
    if (older.isEmpty()) return window
    if (window.isEmpty()) return older.sortedBy { it.timestamp }
    val merged = LinkedHashMap<String, Message>(older.size + window.size)
    older.forEach { merged[it.id] = it }
    window.forEach { merged[it.id] = it }
    return merged.values.sortedBy { it.timestamp }
}

/**
 * Есть ли ещё история выше загруженного: короткая страница означает, что мы дошли
 * до начала чата. Пустая страница — тоже конец истории.
 */
internal fun hasOlderHistory(page: List<Message>, requestedLimit: Int): Boolean =
    page.size >= requestedLimit.coerceAtLeast(1)

/** Вкладки раздела «Медиа, файлы, ссылки». */
internal enum class ChatMediaTab { MEDIA, FILES, LINKS }

/**
 * Относится ли сообщение к выбранной вкладке. Правила намеренно повторяют то, что
 * чат считает вложением: фото/альбом/голосовое — медиа, документ — файл, текст
 * с http(s)-ссылкой — ссылка. Удалённые сообщения не попадают никуда.
 */
internal fun Message.belongsTo(tab: ChatMediaTab): Boolean {
    if (isDeleted) return false
    return when (tab) {
        ChatMediaTab.MEDIA ->
            (imageBase64 != null && !(isViewOnce && viewOnceOpened)) ||
                imagesBase64.isNotEmpty() || voiceBase64 != null
        ChatMediaTab.FILES -> fileBase64 != null || fileName != null
        ChatMediaTab.LINKS -> extractFirstUrl(text) != null
    }
}

/**
 * Картинка-превью для плитки медиа: первая доступная из альбома, либо одиночное фото.
 * Для голосовых и уже открытых одноразовых фото возвращает null — рисуем иконку.
 */
internal fun Message.mediaThumbnail(): String? {
    if (isViewOnce) return null
    return imageBase64 ?: imagesBase64.firstOrNull()
}

/** Текст ссылки для вкладки «Ссылки» (первая ссылка сообщения). */
internal fun Message.firstLink(): String? = extractFirstUrl(text)

/**
 * Позиция сообщения в LazyColumn чата с учётом разделителей дат: перед первым
 * сообщением каждого дня вставляется отдельный item. -1, если сообщения нет в списке.
 * Нужна, чтобы после подгрузки старых сообщений вернуть прокрутку на то же место.
 */
internal fun lazyItemIndexForMessage(messages: List<Message>, messageId: String, dateLabel: (Long) -> String): Int {
    var index = 0
    var previousLabel: String? = null
    for (message in messages) {
        val label = dateLabel(message.timestamp)
        if (label != previousLabel) {
            index++
            previousLabel = label
        }
        if (message.id == messageId) return index
        index++
    }
    return -1
}
