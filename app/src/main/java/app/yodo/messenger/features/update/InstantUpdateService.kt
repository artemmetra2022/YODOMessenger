package app.yodo.messenger.features.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.yodo.messenger.MainActivity
import app.yodo.messenger.R
import app.yodo.messenger.domain.repository.UpdateRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/** Explicit, user-started foreground operation. Never opens the installer from the background. */
@AndroidEntryPoint
class InstantUpdateService : Service() {
    @Inject internal lateinit var repository: UpdateRepository
    @Inject internal lateinit var store: InstantUpdateStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastProgressNotice = 0L
    private val manager get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(WAIT_CHANNEL, "Ожидание и загрузка обновления", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(READY_CHANNEL, "Обновление готово к установке", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { store.cancel(); stopSelf(); return START_NOT_STICKY }
        if (job?.isActive == true) return START_NOT_STICKY
        val version = intent?.getStringExtra("version").orEmpty()
        val code = intent?.getIntExtra("code", 0) ?: 0
        if (code <= repository.currentVersionCode || version.isBlank()) { stopSelf(); return START_NOT_STICKY }
        val initial = InstantUpdateState(InstantPhase.WAITING, version, code)
        store.publish(initial)
        try {
            ServiceCompat.startForeground(this, RUNNING_ID, progressNotification(initial),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        } catch (e: Exception) {
            store.publish(initial.copy(phase = InstantPhase.ERROR,
                message = "Android не разрешил фоновую загрузку. Откройте «О приложении» и повторите запуск."))
            stopSelf()
            return START_NOT_STICKY
        }
        job = scope.launch {
            try { awaitApk(code) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val failed = store.state.value.copy(phase = InstantPhase.ERROR, file = null,
                    message = e.message ?: "Не удалось загрузить обновление. Повторите из «О приложении».")
                store.publish(failed)
                notifySafely(RESULT_ID, resultNotification(failed, false))
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun awaitApk(selectedCode: Int) {
        val (update, file) = awaitInstantUpdate(repository, selectedCode,
            elapsedMillis = { android.os.SystemClock.elapsedRealtime() }, sleep = { delay(it) },
            waiting = { text ->
                store.publish(store.state.value.copy(phase = InstantPhase.WAITING, message = text))
                notifySafely(RUNNING_ID, progressNotification(store.state.value))
            }, downloading = { update ->
                store.publish(InstantUpdateState(InstantPhase.DOWNLOADING, update.versionName, update.versionCode))
                notifySafely(RUNNING_ID, progressNotification(store.state.value))
            }, progress = { done, total ->
                val value = store.state.value.copy(downloadedBytes = done, totalBytes = total.takeIf { it > 0 })
                store.publish(value)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastProgressNotice >= 1_000L) {
                    lastProgressNotice = now
                    notifySafely(RUNNING_ID, progressNotification(value))
                }
            })
        currentCoroutineContext().ensureActive()
        val info = withContext(Dispatchers.IO) { packageManager.getPackageArchiveInfo(file.absolutePath, 0) }
        require(info?.packageName == packageName && androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) == update.versionCode.toLong()) {
            file.delete(); "APK не соответствует приложению или версии релиза"
        }
        val ready = store.state.value.copy(phase = InstantPhase.READY, file = file, message = null)
        store.publish(ready)
        notifySafely(RESULT_ID, resultNotification(ready, true))
    }

    private fun openApp() = PendingIntent.getActivity(this, 830, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun progressNotification(value: InstantUpdateState): Notification {
        val cancel = PendingIntent.getService(this, 831, Intent(this, InstantUpdateService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val downloading = value.phase == InstantPhase.DOWNLOADING
        val total = value.totalBytes
        val percent = total?.let { ((value.downloadedBytes.toDouble() / it) * 100).toInt().coerceIn(0,100) }
        return NotificationCompat.Builder(this, WAIT_CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (downloading) "Загрузка YodoMessenger ${value.versionName}" else "Мгновенная загрузка · ${value.versionName}")
            .setContentText(if (downloading) formatDownloadProgress(value.downloadedBytes, total, percent)
                else value.message ?: "Ожидаем APK. Проверка каждые 30 секунд.")
            .setContentIntent(openApp()).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .addAction(0, "Остановить", cancel).build()
    }
    private fun resultNotification(value: InstantUpdateState, ready: Boolean): Notification {
        val target = if (ready) PendingIntent.getActivity(this, 832, Intent(this, UpdateInstallActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) else openApp()
        val text = if (ready) "Приложение ожидает вашего подтверждения установки. Нажмите, чтобы продолжить." else value.message.orEmpty()
        return NotificationCompat.Builder(this, READY_CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (ready) "YodoMessenger ${value.versionName} готов к установке" else "Мгновенная загрузка остановлена")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(target).setAutoCancel(true).apply { if (ready) addAction(0, "Установить", target) }.build()
    }
    private fun notifySafely(id: Int, notification: Notification) {
        try { manager.notify(id, notification) } catch (_: SecurityException) { /* Ready remains available in About. */ }
    }
    override fun onDestroy() {
        scope.cancel()
        if (store.state.value.active) store.publish(store.state.value.copy(phase = InstantPhase.ERROR,
            message = "Ожидание остановлено. Запустите его повторно в «О приложении»."))
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val WAIT_CHANNEL = "instant_update_wait"
        private const val READY_CHANNEL = "instant_update_ready"
        private const val RUNNING_ID = 8391
        private const val RESULT_ID = 8392
        private const val ACTION_CANCEL = "app.yodo.messenger.STOP_INSTANT_UPDATE"
    }
}
