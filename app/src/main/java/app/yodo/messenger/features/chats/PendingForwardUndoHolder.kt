package app.yodo.messenger.features.chats

import javax.inject.Inject
import javax.inject.Singleton

/**
 * п.2 UX-правок: после пересылки сообщения пользователь возвращается в чат
 * (обычно тот, откуда пересылал), и там снизу — над панелью ввода — должна
 * на 5 секунд показаться плашка "Сообщение переслано" с кнопкой "Отменить".
 *
 * Навигация в этом приложении передаёт между экранами только простые типы,
 * поэтому обмен состоянием между ForwardMessageViewModel и ChatScreen идёт
 * через этот синглтон-holder — по аналогии с PendingForwardHolder.
 */
data class PendingForwardUndo(
    val targetChatId: String,
    val messageId: String,
    // НОВОЕ (п.1): кому переслали — для плашки "Сообщение переслано пользователю ...".
    // targetUserId — если известен (личный чат) — по клику на имя открываем профиль.
    val targetName: String? = null,
    val targetUsername: String? = null,
    val targetUserId: String? = null
)

@Singleton
class PendingForwardUndoHolder @Inject constructor() {
    private var pending: PendingForwardUndo? = null

    fun set(undo: PendingForwardUndo) {
        pending = undo
    }

    /** Забирает состояние (если оно относится к текущему чату) и сразу очищает holder. */
    fun takeIfForChat(chatId: String): PendingForwardUndo? {
        val current = pending ?: return null
        return if (current.targetChatId == chatId) {
            pending = null
            current
        } else null
    }

    fun clear() {
        pending = null
    }
}
