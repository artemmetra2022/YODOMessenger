package app.yodo.messenger.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import app.yodo.messenger.MainActivity
import app.yodo.messenger.R
import app.yodo.messenger.data.local.StoredNotificationMessage

object NotificationHelper {

    // На Android 8+ звук/вибрация закреплены за каналом на момент его создания и не меняются
    // через билдер уведомления. Раньше было всего два канала (со звуком/без), и вибрация
    // не могла управляться отдельно от звука — из-за этого тумблеры "звук" и "вибрация" в
    // настройках фактически не работали независимо. Теперь заводим все 4 комбинации
    // (звук × вибрация) и выбираем канал по ОБОИМ флагам.
    const val CHANNEL_ID_MESSAGES_SOUND_VIBRO = "yodo_messages_sound_vibro"
    const val CHANNEL_ID_MESSAGES_SOUND_ONLY = "yodo_messages_sound_only"
    const val CHANNEL_ID_MESSAGES_VIBRO_ONLY = "yodo_messages_vibro_only"
    const val CHANNEL_ID_MESSAGES_MUTED = "yodo_messages_muted"

    // Старые каналы (для миграции — удаляем, чтобы не засорять настройки приложения).
    private const val LEGACY_CHANNEL_ID_MESSAGES_SOUND = "yodo_messages_sound"
    private const val LEGACY_CHANNEL_ID_MESSAGES_SILENT = "yodo_messages_silent"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Удаляем устаревшие каналы, чтобы пользователь не видел дубли в системных настройках.
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_MESSAGES_SOUND) }
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_MESSAGES_SILENT) }

        val soundVibro = NotificationChannel(
            CHANNEL_ID_MESSAGES_SOUND_VIBRO,
            "Сообщения (звук + вибрация)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях: со звуком и вибрацией"
            enableVibration(true)
        }

        val soundOnly = NotificationChannel(
            CHANNEL_ID_MESSAGES_SOUND_ONLY,
            "Сообщения (только звук)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях: со звуком, без вибрации"
            enableVibration(false)
        }

        val vibroOnly = NotificationChannel(
            CHANNEL_ID_MESSAGES_VIBRO_ONLY,
            "Сообщения (только вибрация)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях: без звука, с вибрацией"
            enableVibration(true)
            setSound(null, null)
        }

        val muted = NotificationChannel(
            CHANNEL_ID_MESSAGES_MUTED,
            "Сообщения (без звука и вибрации)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях: без звука и вибрации"
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannel(soundVibro)
        manager.createNotificationChannel(soundOnly)
        manager.createNotificationChannel(vibroOnly)
        manager.createNotificationChannel(muted)
    }

    // Канал выбирается по обоим флагам сразу — так тумблеры "звук" и "вибрация" работают
    // независимо друг от друга.
    private fun channelIdFor(soundEnabled: Boolean, vibrationEnabled: Boolean): String = when {
        soundEnabled && vibrationEnabled -> CHANNEL_ID_MESSAGES_SOUND_VIBRO
        soundEnabled && !vibrationEnabled -> CHANNEL_ID_MESSAGES_SOUND_ONLY
        !soundEnabled && vibrationEnabled -> CHANNEL_ID_MESSAGES_VIBRO_ONLY
        else -> CHANNEL_ID_MESSAGES_MUTED
    }

    /**
     * Показывает rich-уведомление о новом сообщении в стиле MessagingStyle:
     * заголовок — имя чата/отправителя, тело — история последних сообщений
     * (переданных в [history]), при нескольких подряд — сворачиваются в один стек
     * вместо отдельных уведомлений. При тапе открывает приложение
     * (переход сразу в конкретный чат подключим, когда появится deep-link парсинг в NavGraph).
     */
    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messageText: String,
        history: List<StoredNotificationMessage> = emptyList(),
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

        val channelId = channelIdFor(soundEnabled, vibrationEnabled)

        // Собеседник/автор текущего сообщения — от его лица уведомление и "приходит".
        val me = Person.Builder().setName("Вы").build()
        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(senderName)

        val effectiveHistory = if (history.isNotEmpty()) {
            history
        } else {
            listOf(StoredNotificationMessage(senderName, messageText, System.currentTimeMillis()))
        }

        effectiveHistory.forEach { msg ->
            val sender = Person.Builder().setName(msg.senderName).build()
            messagingStyle.addMessage(msg.text, msg.timestampMillis, sender)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(chatId)

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

    /** Убирает показанное уведомление для чата (например, при его открытии). */
    fun cancelNotification(context: Context, chatId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(chatId.hashCode())
        }
    }

    const val EXTRA_CHAT_ID = "extra_chat_id"
}
