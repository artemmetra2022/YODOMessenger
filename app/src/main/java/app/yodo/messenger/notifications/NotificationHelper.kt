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

    private const val MENTION_PREFIX = "yodo_mentions_"

    // НОВОЕ (push о модерации): отдельный канал для уведомлений о модерации
    // (глобальный бан/разбан). Не завязан на настройки звука/вибрации сообщений —
    // такие уведомления всегда со звуком, так как они важны и редки.
    const val CHANNEL_ID_MODERATION = "yodo_moderation"

    // НОВОЕ (раздел «Школа»): канал событий школьного раздела — новый вопрос
    // учителю, обновление файла урока. Пользователь может отдельно отключить
    // его в системных настройках приложения.
    const val CHANNEL_ID_SCHOOL = "yodo_school"

    // Старые каналы (для миграции — удаляем, чтобы не засорять настройки приложения).
    private const val LEGACY_CHANNEL_ID_MESSAGES_SOUND = "yodo_messages_sound"
    private const val LEGACY_CHANNEL_ID_MESSAGES_SILENT = "yodo_messages_silent"

    fun openMentionSettings(context: Context,sound: Boolean,vibration: Boolean) {
        createNotificationChannel(context)
        val intent=Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,mentionChannelId(sound,vibration))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

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

        // Separate system controls for mentions, including independent sound/vibration combinations.
        listOf(true to true,true to false,false to true,false to false).forEach { (sound,vibration) ->
            manager.createNotificationChannel(NotificationChannel(mentionChannelId(sound,vibration),
                "Упоминания @ · "+when {sound&&vibration->"звук и вибрация";sound->"звук";vibration->"вибрация";else->"без звука"},
                NotificationManager.IMPORTANCE_HIGH).apply {
                    description="Сообщения, в которых упомянуто ваше имя пользователя"
                    enableVibration(vibration)
                    if(!sound)setSound(null,null)
                })
        }

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

        // НОВОЕ (раздел «Школа»): канал событий школьного раздела (вопросы
        // учителю, обновления файла урока) — пользователь может отключить его
        // отдельно от сообщений в системных настройках.
        val school = NotificationChannel(
            CHANNEL_ID_SCHOOL,
            "Школа",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Вопросы учеников и обновления файлов уроков"
        }
        manager.createNotificationChannel(school)
    }

    // Канал выбирается по обоим флагам сразу — так тумблеры "звук" и "вибрация" работают
    // независимо друг от друга.
    private fun channelIdFor(soundEnabled: Boolean, vibrationEnabled: Boolean): String = when {
        soundEnabled && vibrationEnabled -> CHANNEL_ID_MESSAGES_SOUND_VIBRO
        soundEnabled && !vibrationEnabled -> CHANNEL_ID_MESSAGES_SOUND_ONLY
        !soundEnabled && vibrationEnabled -> CHANNEL_ID_MESSAGES_VIBRO_ONLY
        else -> CHANNEL_ID_MESSAGES_MUTED
    }

    private fun mentionChannelId(sound: Boolean,vibration: Boolean) = MENTION_PREFIX+"${if(sound)1 else 0}_${if(vibration)1 else 0}"
    private fun notificationTag(uid: String,chat: String,mention: Boolean)="yodo:$uid:$chat:${if(mention)"mention" else "message"}"

    /** One ordinary stack and a separate mention stack per account/chat. Rights come from verified chat context. */
    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messageText: String,
        history: List<StoredNotificationMessage> = emptyList(),
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        isPrivateChat: Boolean = false,
        accountId: String? = null,
        chatType: String = if(isPrivateChat)"PRIVATE"else "",
        canMarkRead: Boolean = isPrivateChat,
        canReply: Boolean = isPrivateChat,
        canMute: Boolean = false,
        topicId: String? = null,
        conversationTitle: String? = null,
        isMention: Boolean = false,
        soundMuted: Boolean = false
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
            data=android.net.Uri.Builder().scheme("yodo-notification").authority("open").appendPath(accountId.orEmpty()).appendPath(chatId).build()
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if(isMention)mentionChannelId(soundEnabled,vibrationEnabled) else channelIdFor(soundEnabled, vibrationEnabled)

        // Собеседник/автор текущего сообщения — от его лица уведомление и "приходит".
        val me = Person.Builder().setName("Вы").build()
        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if(isMention)"@ Упоминание · ${conversationTitle ?: senderName}" else conversationTitle ?: senderName)
            .setGroupConversation(chatType=="GROUP"||chatType=="CHANNEL")

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

        if(accountId!=null) {
            fun actionIntent(action: String): Intent = Intent(context,NotificationActionReceiver::class.java).apply {
                this.action=action
                data=android.net.Uri.Builder().scheme("yodo-notification").authority("action")
                    .appendPath(accountId).appendPath(chatId).appendPath(action).appendQueryParameter("topic",topicId.orEmpty()).build()
                putExtra(NotificationActionReceiver.EXTRA_CHAT_ID,chatId)
                putExtra(NotificationActionReceiver.EXTRA_ACCOUNT_ID,accountId)
                putExtra(NotificationActionReceiver.EXTRA_TOPIC_ID,topicId)
            }
            fun pending(action: String,mutable: Boolean=false)=PendingIntent.getBroadcast(context,0,actionIntent(action),
                PendingIntent.FLAG_UPDATE_CURRENT or if(mutable)PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
            if(canMarkRead)builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification,
                context.getString(R.string.notification_action_mark_read),pending(NotificationActionReceiver.ACTION_MARK_READ))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build())
            if(canReply) {
                val label=if(chatType=="CHANNEL")"Опубликовать" else context.getString(R.string.notification_action_reply)
                builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification,label,pending(NotificationActionReceiver.ACTION_REPLY,true))
                    .addRemoteInput(RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT).setLabel(label).build())
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false).setAllowGeneratedReplies(false).build())
            }
            if(canMute)builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification,
                context.getString(if(soundMuted)R.string.notification_action_unmute else R.string.notification_action_mute),
                pending(if(soundMuted)NotificationActionReceiver.ACTION_UNMUTE else NotificationActionReceiver.ACTION_MUTE))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE).setShowsUserInterface(false).build())
        }

        // На Android 13+ показ уведомлений требует разрешения POST_NOTIFICATIONS —
        // запрашивается в MainActivity. Если разрешения нет, notify() просто не сработает без краша.
        runCatching {
            NotificationManagerCompat.from(context).cancel(chatId.hashCode())
            if(accountId!=null)NotificationManagerCompat.from(context).notify(notificationTag(accountId,chatId,isMention),0,builder.build())
            else NotificationManagerCompat.from(context).notify(chatId.hashCode(), builder.build())
        }
    }

    /** Убирает показанное уведомление для чата (например, при его открытии). */
    fun cancelNotification(context: Context, chatId: String, accountId: String? = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid) {
        runCatching {
            val manager=NotificationManagerCompat.from(context)
            manager.cancel(chatId.hashCode())
            accountId?.let { uid -> manager.cancel(notificationTag(uid,chatId,false),0);manager.cancel(notificationTag(uid,chatId,true),0) }
        }
    }

    // Quiet, temporary acknowledgement of a DURABLE QUEUE operation, not a server delivery receipt.
    // Actual send failures remain visible in the encrypted outbox in the chat.
    fun showReplyQueuedNotification(
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

    // НОВОЕ (раздел «Школа»): уведомление о событиях школьного раздела —
    // новый вопрос учителю или обновление файла урока (см.
    // YodoFirebaseMessagingService — ветка data.type == "school"). Как и
    // модерация, без диплинка: тап просто открывает приложение. ID — хэш от
    // заголовка, чтобы разные события не затирали друг друга.
    fun showSchoolNotification(context: Context, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = title.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SCHOOL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build())
        }
    }

    const val EXTRA_CHAT_ID = "extra_chat_id"
}
