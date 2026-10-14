package app.yodo.messenger.features.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.yodo.messenger.MainActivity
import app.yodo.messenger.R
import com.google.firebase.auth.FirebaseAuth

class MessageReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        val chatId = inputData.getString("chatId") ?: return Result.failure()
        val messageId = inputData.getString("messageId") ?: return Result.failure()
        // Never expose another account's reminder. No network or chat text is needed.
        if (FirebaseAuth.getInstance().currentUser?.uid != uid || isStopped) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()
        val channelId = "message_reminders"
        manager.createNotificationChannel(NotificationChannel(channelId,
            applicationContext.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val key = reminderKey(uid, chatId, messageId)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_REMINDER
            data = Uri.Builder().scheme("yodo-reminder").authority("message").appendPath(key).build()
            putExtra("reminderUid", uid)
            putExtra("reminderChatId", chatId)
            putExtra("reminderMessageId", messageId)
            putExtra("reminderTopicId", inputData.getString("topicId"))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.reminder_notification_title))
            .setContentText(applicationContext.getString(R.string.reminder_notification_body))
            .setContentIntent(pendingIntent).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        if (!isStopped && FirebaseAuth.getInstance().currentUser?.uid == uid) {
            try { manager.notify(key, 1, notification) } catch (_: SecurityException) { }
        }
        return Result.success()
    }

    companion object {
        const val ACTION_OPEN_REMINDER = "app.yodo.messenger.OPEN_MESSAGE_REMINDER"
    }
}
