package app.yodo.messenger.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.yodo.messenger.MainActivity
import app.yodo.messenger.R

object NotificationHelper {

    // На Android 8+ звук/вибрация закреплены за каналом на момент его создания и не меняются
    // через билдер уведомления — поэтому под настройку "звук вкл/выкл" заведены два канала.
    const val CHANNEL_ID_MESSAGES_SOUND = "yodo_messages_sound"
    const val CHANNEL_ID_MESSAGES_SILENT = "yodo_messages_silent"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES_SOUND,
            "Сообщения (со звуком)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях в чатах"
            enableVibration(true)
        }

        val silentChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES_SILENT,
            "Сообщения (без звука)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях в чатах, без звука/вибрации"
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannel(soundChannel)
        manager.createNotificationChannel(silentChannel)
    }

    /**
     * Показывает уведомление о новом сообщении. При тапе открывает приложение
     * (переход сразу в конкретный чат подключим, когда появится deep-link парсинг в NavGraph).
     */
    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messageText: String,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (soundEnabled) CHANNEL_ID_MESSAGES_SOUND else CHANNEL_ID_MESSAGES_SILENT

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Для Android < 8 (где канала как понятия нет) звук/вибрация настраиваются прямо в билдере
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (!soundEnabled) builder.setSound(null)
            if (!vibrationEnabled) builder.setVibrate(longArrayOf(0L))
        }

        // На Android 13+ показ уведомлений требует разрешения POST_NOTIFICATIONS —
        // запрашивается в MainActivity. Если разрешения нет, notify() просто не сработает без краша.
        runCatching {
            NotificationManagerCompat.from(context).notify(chatId.hashCode(), builder.build())
        }
    }

    const val EXTRA_CHAT_ID = "extra_chat_id"
}
