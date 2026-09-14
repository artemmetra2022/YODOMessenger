package app.yodo.messenger.features.chats

import app.yodo.messenger.domain.model.Message
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compose Navigation передаёт между экранами только строки — а не сложные объекты.
 * Вместо кодирования всего сообщения в route (или похода в Firestore за ним же второй раз),
 * временно кладём его сюда перед навигацией на экран пересылки и сразу забираем обратно.
 */
@Singleton
class PendingForwardHolder @Inject constructor() {
    data class Item(val message: Message, val originSenderName: String)

    private var items: List<Item> = emptyList()
    // ИСПРАВЛЕНИЕ (баг «в Переслано от.. пишется имя человека, а не канала»):
    // раньше при пересылке всегда подставлялось имя ТЕКУЩЕГО пользователя
    // (того, кто нажал «Переслать»), а не автора исходного сообщения. Теперь
    // вместе с сообщением сохраняем и настоящее имя источника — например,
    // название канала, если сообщение было постом канала.
    fun set(message: Message, originSenderName: String) {
        set(listOf(Item(message, originSenderName)))
    }

    fun set(items: List<Item>) {
        this.items = items.toList()
    }

    /** Сообщения остаются в holder до успешной отправки всей пачки. */
    fun peekAll(): List<Item> = items
    fun peek(): Message? = items.firstOrNull()?.message
    fun peekOriginSenderName(): String? = items.firstOrNull()?.originSenderName

    fun takeAllAndClear(): List<Item> {
        val result = items
        items = emptyList()
        return result
    }

    fun takeAndClear(): Message? = takeAllAndClear().firstOrNull()?.message
}
