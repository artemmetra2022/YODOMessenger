package app.yodo.messenger.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.PresenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            // Уважаем настройку приватности "показывать статус онлайн" — если выключена,
            // просто не публикуем presence вообще (не только скрываем в UI, а правда не пишем).
            val showStatus = userSettingsPreferences.showOnlineStatus.first()
            if (showStatus) {
                presenceRepository.setOnline(true)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        presenceRepository.setOnline(false)
    }
}
