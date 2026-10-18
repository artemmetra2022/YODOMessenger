package app.yodo.messenger.features.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.normalizedUnreadCount
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.features.weather.WeatherRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Доступ к синглтонам Hilt из виджетов/воркера (они создаются системой, не Hilt'ом). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun firestore(): FirebaseFirestore
    fun auth(): FirebaseAuth
    fun weather(): WeatherRepository
    fun settings(): UserSettingsPreferences

    companion object {
        fun get(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
}

object WidgetRefresher {
    private val mutex = Mutex()

    /** Обновляет данные только для реально размещённых виджетов и перерисовывает их. */
    suspend fun refreshAll(context: Context) = mutex.withLock {
        val manager = GlanceAppWidgetManager(context)
        val hasUnread = manager.getGlanceIds(UnreadWidget::class.java).isNotEmpty()
        val hasWeather = manager.getGlanceIds(WeatherWidget::class.java).isNotEmpty()
        val hasCombined = manager.getGlanceIds(CombinedWidget::class.java).isNotEmpty()
        if (hasUnread || hasCombined) refreshUnread(context)
        if (hasWeather || hasCombined) refreshWeather(context)
        if (hasUnread) UnreadWidget().updateAll(context)
        if (hasWeather) WeatherWidget().updateAll(context)
        if (hasCombined) CombinedWidget().updateAll(context)
    }

    private suspend fun refreshUnread(context: Context): Boolean {
        val ep = WidgetEntryPoint.get(context)
        val uid = ep.auth().currentUser?.uid
        if (uid == null) {
            // Вышли из аккаунта — на рабочем столе не остаётся ни названий чатов, ни счётчиков.
            WidgetStore.clearUnread(context)
            return true
        }
        return try {
            val hidden = ep.settings().hiddenChatIds.first()
            val firestore = ep.firestore()
            val official = ChatRepository.OFFICIAL_CHANNEL_ID
            val docs = firestore.collection("chats")
                .whereArrayContains("participantIds", uid)
                .limit(200)
                .get().await()
                .documents.filter { it.id != official }
            // Официальный канал приложение показывает отдельно — считаем его тоже, иначе
            // число на виджете разойдётся со значком в списке чатов.
            val officialDoc = runCatching {
                firestore.collection("chats").document(official).get().await()
            }.getOrNull()?.takeIf { it.exists() }
            val raw = (docs + listOfNotNull(officialDoc)).map { it.toRawUnread(uid) }
            WidgetStore.writeUnread(context, buildUnreadSnapshot(uid, raw, hidden))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun refreshWeather(context: Context): Boolean {
        return try {
            val ep = WidgetEntryPoint.get(context)
            val city = ep.settings().weatherCity.first()
            WidgetStore.writeWeather(context, ep.weather().load(city).toWidgetSnapshot())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun DocumentSnapshot.toRawUnread(uid: String): RawChatUnread {
        val titles = get("titles") as? Map<*, *>
        val muted = get("muted") as? Map<*, *>
        val archived = get("archived") as? Map<*, *>
        return RawChatUnread(
            chatId = id,
            title = (titles?.get(uid) as? String) ?: getString("title").orEmpty(),
            unread = normalizedUnreadCount((get("unreadCounts") as? Map<*, *>)?.get(uid)),
            muted = muted?.get(uid) as? Boolean ?: false,
            archived = archived?.get(uid) as? Boolean ?: false,
            lastTimestamp = getLong("lastMessageTimestamp") ?: 0L
        )
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresher.refreshAll(applicationContext)
        return Result.success()
    }
}

object WidgetScheduler {
    private const val PERIODIC = "yodo_widget_periodic"
    private const val NOW = "yodo_widget_now"

    private fun widgetIds(context: Context, receiver: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiver))

    fun hasWidgets(context: Context): Boolean =
        widgetIds(context, UnreadWidgetReceiver::class.java).isNotEmpty() ||
            widgetIds(context, WeatherWidgetReceiver::class.java).isNotEmpty() ||
            widgetIds(context, CombinedWidgetReceiver::class.java).isNotEmpty()

    /** Раз в 15 минут (минимум WorkManager) — подтянуть счётчики и погоду. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Обновить сейчас: новое сообщение (FCM), выход из приложения, смена города/аккаунта. */
    fun requestRefresh(context: Context) {
        val app = context.applicationContext
        if (!hasWidgets(app)) return
        WorkManager.getInstance(app).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        )
    }

    fun cancelIfUnused(context: Context) {
        if (!hasWidgets(context)) WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
