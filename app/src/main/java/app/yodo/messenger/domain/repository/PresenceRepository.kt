package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.UserPresence
import kotlinx.coroutines.flow.Flow

interface PresenceRepository {

    companion object {
        /**
         * Как часто клиент обновляет lastSeen, пока приложение открыто (см. heartbeat()).
         * Чем меньше интервал, тем точнее статус "в сети" / время последнего захода.
         */
        const val HEARTBEAT_INTERVAL_MILLIS = 20_000L

        /**
         * Если с момента последнего обновления presence прошло больше этого времени,
         * а isOnline всё ещё true — считаем это "зависшим" статусом (например, процесс
         * убила система без вызова onStop) и на клиенте показываем как оффлайн.
         * Берём с запасом в 2.5x от интервала пульса, чтобы не мигать статусом на
         * кратковременных сетевых задержках.
         */
        const val PRESENCE_STALE_THRESHOLD_MILLIS = HEARTBEAT_INTERVAL_MILLIS * 3
    }

    /** Вызывается при переходе приложения на передний план / в фон (см. PresenceLifecycleObserver). */
    fun setOnline(isOnline: Boolean)

    /**
     * "Пульс" — обновляет только lastSeen (не трогая isOnline), пока приложение находится
     * на переднем плане. Вызывается периодически (см. PresenceLifecycleObserver), чтобы
     * время последнего появления в сети было максимально точным, а не "залипало" на моменте
     * последнего onStart — например, если процесс убьёт система без вызова onStop.
     */
    fun heartbeat()

    /**
     * Включает/выключает приватность статуса "в сети" немедленно (не дожидаясь следующего
     * onStart/onStop). Когда hidden = true — статус тут же скрывается у всех наблюдателей,
     * даже если пользователь прямо сейчас находится в приложении.
     */
    fun setOnlineStatusHidden(hidden: Boolean)

    /** Реалтайм-статус конкретного пользователя: онлайн / когда был последний раз.
     *  Если сам пользователь скрыл свой статус конфиденциальности, наблюдатель всегда
     *  получит isOnline = false и lastSeenMillis = 0 (кроме случая, когда наблюдает сам за собой). */
    fun observePresence(uid: String): Flow<UserPresence>

    /** Реалтайм-множество uid участников, которые сейчас печатают в этом чате (кроме меня). */
    fun observeTypingUsers(chatId: String): Flow<Set<String>>

    /** Отметить, печатаю ли я сейчас в этом чате. */
    suspend fun setTyping(chatId: String, isTyping: Boolean)
}
