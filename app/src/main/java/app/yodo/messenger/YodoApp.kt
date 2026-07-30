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

        // Обновляем запись о текущем устройстве в Firestore при каждом старте приложения.
        // Если пользователь не вошёл — метод безопасно ничего не делает.
        appScope.launch {
            sessionRepository.updateCurrentSession()
        }
    }
}
