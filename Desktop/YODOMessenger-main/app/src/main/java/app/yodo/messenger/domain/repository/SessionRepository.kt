package app.yodo.messenger.domain.repository

import app.yodo.messenger.domain.model.DeviceSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    /** Записывает / обновляет запись о текущем сеансе в Firestore. */
    suspend fun updateCurrentSession()

    /** Возвращает поток всех активных сеансов пользователя в реальном времени. */
    fun observeSessions(): Flow<List<DeviceSession>>

    /**
     * Следит именно за документом ТЕКУЩЕГО сеанса (users/{uid}/sessions/{currentSessionId}).
     * Эмитит false, если документ был удалён (сеанс завершён с другого устройства/экрана) —
     * по этому сигналу вызывающая сторона должна выполнить signOut и уйти на экран входа.
     * Эмитит true, пока документ существует.
     */
    fun observeCurrentSessionExists(): Flow<Boolean>

    /** Завершает (удаляет) указанный сеанс. Текущий сеанс удалять нельзя — проверяйте isCurrent. */
    suspend fun terminateSession(sessionId: String)

    /** Возвращает идентификатор текущего устройства (UUID, сгенерированный при первом запуске). */
    fun getCurrentSessionId(): String
}
