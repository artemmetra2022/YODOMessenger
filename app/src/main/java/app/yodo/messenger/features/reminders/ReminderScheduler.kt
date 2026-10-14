package app.yodo.messenger.features.reminders

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun schedule(uid: String, chatId: String, messageId: String, topicId: String?, at: Long) = withContext(Dispatchers.IO) {
        require(at > System.currentTimeMillis())
        val key = reminderKey(uid, chatId, messageId)
        val request = OneTimeWorkRequestBuilder<MessageReminderWorker>()
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("uid" to uid, "chatId" to chatId, "messageId" to messageId, "topicId" to topicId))
            .addTag("message-reminder")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, request).result.get()
        NotificationManagerCompat.from(context).cancel(key, 1)
    }

    suspend fun cancel(uid: String, chatId: String, messageId: String) = withContext(Dispatchers.IO) {
        val key = reminderKey(uid, chatId, messageId)
        WorkManager.getInstance(context).cancelUniqueWork(key).result.get()
        NotificationManagerCompat.from(context).cancel(key, 1)
    }
}
