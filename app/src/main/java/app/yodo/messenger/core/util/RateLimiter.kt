package app.yodo.messenger.core.util

/**
 * НОВОЕ (rate limiting): простой sliding-window лимитер для исходящих действий
 * (отправка текстовых сообщений, фото, голосовых, файлов и т.д.).
 *
 * Не завязан на Firestore/сеть — работает полностью локально по времени последних
 * попыток, поэтому не требует доп. чтений/записей и не тратит квоту.
 *
 * Пример: maxEvents = 5, windowMillis = 10_000 — не больше 5 отправок за 10 секунд.
 * Если лимит превышен, tryAcquire() возвращает false и не регистрирует попытку —
 * т.е. пользователь не наказывается за уже отклонённые попытки.
 */
class RateLimiter(
    private val maxEvents: Int,
    private val windowMillis: Long
) {
    private val timestamps = ArrayDeque<Long>()

    /**
     * Пытается зарегистрировать новое событие (например, отправку сообщения).
     * Возвращает true, если событие разрешено (и регистрирует его),
     * false — если превышен лимит в текущем окне.
     */
    @Synchronized
    fun tryAcquire(nowMillis: Long = System.currentTimeMillis()): Boolean {
        // Убираем из окна все события старше windowMillis.
        while (timestamps.isNotEmpty() && nowMillis - timestamps.first() > windowMillis) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= maxEvents) {
            return false
        }
        timestamps.addLast(nowMillis)
        return true
    }

    /**
     * Через сколько миллисекунд снова станет доступна отправка (0, если можно отправлять сейчас).
     * Полезно для сообщения пользователю "Подождите ещё N сек.".
     */
    @Synchronized
    fun retryAfterMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        while (timestamps.isNotEmpty() && nowMillis - timestamps.first() > windowMillis) {
            timestamps.removeFirst()
        }
        if (timestamps.size < maxEvents) return 0L
        val oldest = timestamps.first()
        val remaining = windowMillis - (nowMillis - oldest)
        return if (remaining > 0) remaining else 0L
    }

    @Synchronized
    fun reset() {
        timestamps.clear()
    }
}
