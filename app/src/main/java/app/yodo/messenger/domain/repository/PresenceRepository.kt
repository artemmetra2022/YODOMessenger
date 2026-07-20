package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.UserPresence
import kotlinx.coroutines.flow.Flow

interface PresenceRepository {

    /** Вызывается при переходе приложения на передний план / в фон (см. PresenceLifecycleObserver). */
    fun setOnline(isOnline: Boolean)

    /** Реалтайм-статус конкретного пользователя: онлайн / когда был последний раз. */
    fun observePresence(uid: String): Flow<UserPresence>

    /** Реалтайм-множество uid участников, которые сейчас печатают в этом чате (кроме меня). */
    fun observeTypingUsers(chatId: String): Flow<Set<String>>

    /** Отметить, печатаю ли я сейчас в этом чате. */
    suspend fun setTyping(chatId: String, isTyping: Boolean)
}
