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
import androidx.core.app.RemoteInput
import app.yodo.messenger.MainActivity
import app.yodo.messenger.R
import app.yodo.messenger.data.local.StoredNotificationMessage
import app.yodo.messenger.data.remote.fcm.NotificationActionReceiver

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

    // НОВОЕ (push о модерации): отдельный канал для уведомлений о модерации
    // (глобальный бан/разбан). Не завязан на настройки звука/вибрации сообщений —
    // такие уведомления всегда со звуком, так как они важны и редки.
    const val CHANNEL_ID_MODERATION = "yodo_moderation"

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

        // НОВОЕ (push о модерации): отдельный канал, всегда со звуком и вибрацией —
        // не зависит от пользовательских настроек звука/вибрации для обычных сообщений.
        val moderation = NotificationChannel(
            CHANNEL_ID_MODERATION,
            "Модерация аккаунта",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о блокировке или разблокировке вашего аккаунта"
            enableVibration(true)
        }
        manager.createNotificationChannel(moderation)
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
     *
     * НОВОЕ (быстрые действия "Прочитано"/"Ответить"): для личных чатов (1 на 1)
     * добавляются две кнопки прямо в уведомлении — "Прочитано" (отмечает чат
     * прочитанным без открытия приложения) и "Ответить" (открывает системную
     * форму ввода текста прямо в уведомлении через RemoteInput и отправляет
     * ответ в фоне). Для групп/каналов кнопки не показываются, так как там нет
     * единого "собеседника", а "Прочитано" по клику одного пользователя не должно
     * молча помечать всё как прочитанное для всех сценариев.
     */
    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messageText: String,
        history: List<StoredNotificationMessage> = emptyList(),
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        isPrivateChat: Boolean = false
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

        // НОВОЕ (быстрые действия): только для личных чатов (1 на 1) — см. комментарий
        // к функции выше. Оба действия обрабатывает NotificationActionReceiver в фоне,
        // без открытия приложения (никакого showsUserInterface).
        if (isPrivateChat) {
            val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_READ
                putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, chatId)
            }
            val markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                chatId.hashCode(), // reuse chat-specific request code so PendingIntents don't collide across chats
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val markReadAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_mark_read),
                markReadPendingIntent
            ).setShowsUserInterface(false).build()

            val replyLabel = context.getString(R.string.notification_action_reply)
            val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
                .setLabel(replyLabel)
                .build()
            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, chatId)
            }
            // request code сдвинут (+1), чтобы не совпасть с markReadPendingIntent для того же чата
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                chatId.hashCode() + 1,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // RemoteInput требует MUTABLE
            )
            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                replyLabel,
                replyPendingIntent
            ).addRemoteInput(remoteInput)
                .setShowsUserInterface(false)
                .setAllowGeneratedReplies(false)
                .build()

            builder.addAction(markReadAction)
            builder.addAction(replyAction)
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

    // НОВОЕ (подтверждение "Ответ отправлен"): перерисовывает то же уведомление личного
    // чата с обновлённой историей (включающей только что отправленную реплику "от себя"),
    // чтобы пользователь увидел подтверждение отправки прямо в шторке, не открывая
    // приложение. Кнопки действий не добавляются заново — раз ответ уже отправлен,
    // уведомление можно просто прочитать; setTimeoutAfter автоматически уберёт его,
    // если пользователь не взаимодействует с ним дальше. Новое входящее сообщение
    // (следующий push) перерисует уведомление обычным путём — с кнопками — как всегда.
    fun showReplySentNotification(
        context: Context,
        chatId: String,
        history: List<StoredNotificationMessage>
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

        val conversationTitle = history.lastOrNull { it.senderName != "Вы" }?.senderName
        val me = Person.Builder().setName("Вы").build()
        val messagingStyle = NotificationCompat.MessagingStyle(me)
        if (conversationTitle != null) {
            messagingStyle.setConversationTitle(conversationTitle)
        }
        history.forEach { msg ->
            val sender = Person.Builder().setName(msg.senderName).build()
            messagingStyle.addMessage(msg.text, msg.timestampMillis, sender)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES_MUTED)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(chatId)
            // Ответ уже отправлен — новый звук/вибрация не нужны, только визуальное
            // подтверждение, поэтому используем "тихий" канал независимо от настроек звука.
            .setOnlyAlertOnce(true)
        builder.setTimeoutAfter(REPLY_SENT_AUTO_DISMISS_MS)

        runCatching {
            NotificationManagerCompat.from(context).notify(chatId.hashCode(), builder.build())
        }
    }

    // Автоматическое скрытие подтверждения "Ответ отправлен", если пользователь не смахнул
    // его сам — чтобы шторка не копила уведомления без действия.
    private const val REPLY_SENT_AUTO_DISMISS_MS = 15_000L

    // НОВОЕ (push о модерации): простое уведомление без MessagingStyle и без
    // диплинка в конкретный чат — тап просто открывает приложение. Используется
    // для событий "аккаунт заблокирован"/"блокировка снята" (см.
    // YodoFirebaseMessagingService — ветка data.type == "moderation").
    fun showModerationNotification(context: Context, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = MODERATION_NOTIFICATION_ID
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_MODERATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build())
        }
    }

    // Фиксированный ID — уведомления о модерации не группируются по чату, как
    // сообщения (там ID = chatId.hashCode()), поэтому используем отдельную константу.
    private const val MODERATION_NOTIFICATION_ID = -1001

    const val EXTRA_CHAT_ID = "extra_chat_id"
}
