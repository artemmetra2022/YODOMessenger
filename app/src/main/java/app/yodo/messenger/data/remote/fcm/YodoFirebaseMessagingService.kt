package app.yodo.messenger.data.remote.fcm

import app.yodo.messenger.data.local.NotificationMessageStore
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Формат данных в push-сообщении (см. push-worker/index.js):
 * data: { chatId, senderName, messageText, chatType?, recipientId?, senderId?, topicId? }
 * Legacy pushes are supported. For groups/channels, @username is matched against the
 * signed-in user's current username, never a substring or an email address.
 * Optional type="mention" with recipientId is accepted from the trusted FCM sender.
 * No decrypted PRIVATE message text is uploaded or required for this feature.
 * data: { type: "moderation", title, body } — уведомление о модерации
 * (глобальный бан/разбан); намеренно не проверяет mute/quiet hours/snooze —
 * это редкое и важное системное уведомление, которое не должно теряться
 * из-за пользовательских настроек тишины для обычных сообщений.
 * data: { type: "school", title, body } — события школьного раздела
 * (новый вопрос учителю, обновление файла урока); показываются как простое
 * уведомление (см. NotificationHelper.showSchoolNotification).
 */
@AndroidEntryPoint
class YodoFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var firestore: FirebaseFirestore

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var userSettingsPreferences: UserSettingsPreferences

    @Inject
    lateinit var notificationMessageStore: NotificationMessageStore

    @Inject lateinit var chatAccess: app.yodo.messenger.notifications.NotificationChatAccess
    @Inject lateinit var silenceStore: app.yodo.messenger.data.local.NotificationSilenceStore

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Новое сообщение → обновить виджет «Непрочитанные».
        runCatching { app.yodo.messenger.features.widget.WidgetScheduler.requestRefresh(applicationContext) }

        // НОВОЕ (push о модерации): отдельная ветка для событий модерации
        // (глобальный бан/разбан) — другой payload (title/body, без chatId),
        // поэтому не смешивается с обработкой обычных сообщений чата ниже.
        if (message.data["type"] == "moderation") {
            val title = message.data["title"] ?: "Yodo Messenger"
            val body = message.data["body"] ?: ""
            app.yodo.messenger.notifications.NotificationHelper.showModerationNotification(
                context = applicationContext,
                title = title,
                body = body
            )
            return
        }

        // НОВОЕ (раздел «Школа»): события школьного раздела — новый вопрос
        // учителю или обновление файла урока. Тот же формат title/body, что и
        // у модерации, но отдельный канал уведомлений.
        if (message.data["type"] == "school") {
            val title = message.data["title"] ?: "Школа"
            val body = message.data["body"] ?: ""
            app.yodo.messenger.notifications.NotificationHelper.showSchoolNotification(
                context = applicationContext,
                title = title,
                body = body
            )
            return
        }

        val chatId = message.data["chatId"]?.takeIf { it.isNotBlank() } ?: return
        val uid=firebaseAuth.currentUser?.uid ?: return
        if(message.data["recipientId"]?.let { it!=uid }==true)return
        if(message.data["senderId"]==uid)return
        // FirebaseMessagingService invokes this on its worker thread. Finish bounded work before
        // returning; a detached coroutine can be killed before it posts the notification.
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            kotlinx.coroutines.withTimeoutOrNull(9_000) {
                try { handleChatPush(message,uid,chatId) }
                catch(e: kotlinx.coroutines.CancellationException){throw e}
                catch(e: Exception){android.util.Log.w("YodoPush","Chat notification could not be processed",e)}
            }
        }
    }

    private suspend fun handleChatPush(message: RemoteMessage,uid: String,chatId: String) {
        if(userSettingsPreferences.muteAllNotifications.first())return
        if(userSettingsPreferences.notificationsSnoozedUntil.first()>System.currentTimeMillis())return
        if(userSettingsPreferences.quietHoursEnabled.first()) {
            val start=userSettingsPreferences.quietHoursStart.first()
            val end=userSettingsPreferences.quietHoursEnd.first()
            val hour=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if(if(start<=end)hour in start until end else hour>=start||hour<end)return
        }
        val hidePreview=userSettingsPreferences.hideNotificationPreview.first()
        val access=chatAccess.load(uid,chatId,hidePreview) ?: return
        // Existing per-chat "disable notifications" is different from the new local sound-only action.
        if(access.serverMuted)return
        val sender=message.data["senderName"] ?: "YodoMessenger"
        val text=message.data["messageText"] ?: message.notification?.body.orEmpty()
        val mention=if(message.data["type"]=="mention" && message.data["recipientId"]==uid)true
            else if(access.type=="GROUP"||access.type=="CHANNEL") {
                val username=try { kotlinx.coroutines.withTimeout(2_000) {
                    firestore.collection("users").document(uid).get().await().getString("username")
                }} catch(e: Exception){
                    if(e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException)throw e
                    null
                }
                app.yodo.messenger.core.mentions.mentionsUsername(text,username)
            } else false
        val locallyMuted=silenceStore.isSilent(uid,chatId)
        val (sound,vibration)=app.yodo.messenger.notifications.notificationAlerts(
            userSettingsPreferences.notificationSound.first(),userSettingsPreferences.notificationVibration.first(),locallyMuted)
        if(firebaseAuth.currentUser?.uid!=uid)return
        val displaySender=if(hidePreview)"Новое сообщение"else sender
        val displayText=if(hidePreview)"Откройте приложение, чтобы прочитать"else text
        val history=if(hidePreview)emptyList() else notificationMessageStore.addMessage(chatId,displaySender,displayText,
            System.currentTimeMillis(),accountId=uid,mention=mention)
        if(firebaseAuth.currentUser?.uid!=uid)return
        NotificationHelper.showMessageNotification(applicationContext,chatId,displaySender,displayText,history,
            soundEnabled=sound,vibrationEnabled=vibration,accountId=uid,chatType=access.type,
            canMarkRead=access.actions.markRead,canReply=access.actions.reply,canMute=access.actions.mute,
            topicId=message.data["topicId"],conversationTitle=if(hidePreview)null else access.title.takeIf { it.isNotBlank() },isMention=mention,soundMuted=locallyMuted)
    }

    private fun saveTokenToFirestore(token: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update("fcmToken", token)
            .addOnFailureListener {
                // Пользователь мог ещё не быть создан в Firestore на момент получения токена —
                // не критично, токен будет сохранён повторно при следующем onNewToken или логине.
            }
    }
}
