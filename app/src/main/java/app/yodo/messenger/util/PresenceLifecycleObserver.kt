package app.yodo.messenger.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.PresenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Регистрируется на ProcessLifecycleOwner в YodoApp — срабатывает один раз для всего приложения,
 * а не для каждой Activity/экрана. onStart — приложение вышло на передний план (хотя бы один
 * экран виден), onStop — свёрнуто полностью.
 */
class PresenceLifecycleObserver(
    private val presenceRepository: PresenceRepository,
    private val userSettingsPreferences: UserSettingsPreferences
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            // Уважаем настройку приватности "показывать статус онлайн" — если выключена,
            // просто не публикуем presence вообще (не только скрываем в UI, а правда не пишем).
            val showStatus = userSettingsPreferences.showOnlineStatus.first()
            if (showStatus) {
                presenceRepository.setOnline(true)
                startHeartbeat()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        stopHeartbeat()
        presenceRepository.setOnline(false)
    }

    /**
     * Пока приложение на переднем плане, периодически обновляет lastSeen (см.
     * PresenceRepository.HEARTBEAT_INTERVAL_MILLIS), чтобы статус "в сети" / время
     * последнего появления оставались максимально точными даже при долгих сессиях,
     * а не только в моменты onStart/onStop.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(PresenceRepository.HEARTBEAT_INTERVAL_MILLIS)
                presenceRepository.heartbeat()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
