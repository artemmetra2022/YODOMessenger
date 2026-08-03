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

    /**
     * п.36: посмотреть сообщение, которое будет переслано, БЕЗ очистки holder'а.
     * Нужно, чтобы экран пересылки мог показать превью ("Вы пересылаете: ...")
     * ещё до того, как пользователь выберет чат-получатель.
     */
    fun peek(): Message? = message

    fun takeAndClear(): Message? {
        val result = message
        message = null
        return result
    }
}
