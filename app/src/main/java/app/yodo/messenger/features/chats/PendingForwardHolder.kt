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
    private var message: Message? = null

    fun set(message: Message) {
        this.message = message
    }

    fun takeAndClear(): Message? {
        val result = message
        message = null
        return result
    }
}
