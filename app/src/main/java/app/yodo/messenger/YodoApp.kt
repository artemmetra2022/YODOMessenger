package app.yodo.messenger

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.notifications.NotificationHelper
import app.yodo.messenger.util.PresenceLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YodoApp : Application() {

    @Inject
    lateinit var presenceRepository: PresenceRepository

    @Inject
    lateinit var userSettingsPreferences: UserSettingsPreferences

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            PresenceLifecycleObserver(presenceRepository, userSettingsPreferences)
        )
    }
}
