package app.yodo.messenger.features.update

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

internal enum class InstantPhase { IDLE, WAITING, DOWNLOADING, READY, ERROR }
internal data class InstantUpdateState(
    val phase: InstantPhase = InstantPhase.IDLE,
    val versionName: String = "",
    val versionCode: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val file: File? = null,
    val message: String? = null
) {
    val active: Boolean get() = phase == InstantPhase.WAITING || phase == InstantPhase.DOWNLOADING
}

/** Process-wide state shared by About and the service. Only a completed APK survives process death. */
@Singleton
internal class InstantUpdateStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("instant_update", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(restore())
    val state = mutable.asStateFlow()

    private fun restore(): InstantUpdateState {
        val name = prefs.getString("file", null) ?: return InstantUpdateState()
        val file = File(File(context.cacheDir, "updates"), name)
        val code = prefs.getInt("code", 0)
        return if (name == file.name && file.isFile && file.length() > 0 && code > app.yodo.messenger.BuildConfig.VERSION_CODE)
            InstantUpdateState(InstantPhase.READY, prefs.getString("version", "").orEmpty(), code, file = file)
        else InstantUpdateState()
    }

    fun publish(value: InstantUpdateState) {
        if (value.phase == InstantPhase.READY && value.file != null) {
            prefs.edit().putString("file", value.file.name).putString("version", value.versionName)
                .putInt("code", value.versionCode).apply()
        } else if (prefs.contains("file")) prefs.edit().clear().apply()
        mutable.value = value
    }

    fun start(versionName: String, versionCode: Int) {
        if (state.value.active) return
        publish(InstantUpdateState(InstantPhase.WAITING, versionName, versionCode))
        try {
            ContextCompat.startForegroundService(context, Intent(context, InstantUpdateService::class.java)
                .putExtra("version", versionName).putExtra("code", versionCode))
        } catch (e: Exception) {
            publish(InstantUpdateState(InstantPhase.ERROR, message = "Не удалось запустить ожидание APK: ${e.message.orEmpty()}"))
        }
    }

    fun cancel() {
        context.stopService(Intent(context, InstantUpdateService::class.java))
        publish(InstantUpdateState())
    }
}
