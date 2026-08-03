package app.yodo.messenger

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.SessionRepository
import app.yodo.messenger.notifications.NotificationHelper
import app.yodo.messenger.util.PresenceLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class YodoApp : Application() {

    @Inject
    lateinit var presenceRepository: PresenceRepository

    @Inject
    lateinit var userSettingsPreferences: UserSettingsPreferences

    @Inject
    lateinit var sessionRepository: SessionRepository

    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            PresenceLifecycleObserver(presenceRepository, userSettingsPreferences)
        )

        // Безопасная инициализация сессии: оборачиваем в runCatching,
        // чтобы краш Firebase (например, отсутствие google-services.json)
        // не убивал всё приложение при старте.
        appScope.launch {
            runCatching {
                sessionRepository.updateCurrentSession()
            }.onFailure { e ->
                // Логируем ошибку, но не крашим приложение.
                // Сессия обновится позже при входе пользователя.
                android.util.Log.w("YodoApp", "Session update failed on startup", e)
            }
        }
    }
}
