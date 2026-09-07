package app.yodo.messenger.data.remote.fcm

import app.yodo.messenger.data.local.NotificationMessageStore
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Формат данных в push-сообщении (см. push-worker/index.js):
 * data: { chatId, senderName, messageText } — обычное сообщение чата
 * data: { type: "moderation", title, body } — уведомление о модерации
 * (глобальный бан/разбан); намеренно не проверяет mute/quiet hours/snooze —
 * это редкое и важное системное уведомление, которое не должно теряться
 * из-за пользовательских настроек тишины для обычных сообщений.
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

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

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

        val chatId = message.data["chatId"] ?: return
        val senderName = message.data["senderName"] ?: "Yodo Messenger"
        val messageText = message.data["messageText"] ?: message.notification?.body.orEmpty()
        // НОВОЕ (быстрые действия "Прочитано"/"Ответить"): кнопки показываем только
        // для личных чатов (1 на 1) — push-worker кладёт chatType в data (см.
        // push-worker/index.js). По умолчанию (chatType отсутствует/неизвестен)
        // считаем чат НЕ приватным — безопаснее не показать кнопки, чем случайно
        // показать их в группе.
        val isPrivateChat = message.data["chatType"].equals("PRIVATE", ignoreCase = true)

        serviceScope.launch {
            val globallyMuted = userSettingsPreferences.muteAllNotifications.first()
            if (globallyMuted) return@launch

            // НОВОЕ: пауза уведомлений (snooze) — молчим, пока не истечёт таймер.
            val snoozedUntil = userSettingsPreferences.notificationsSnoozedUntil.first()
            if (snoozedUntil > System.currentTimeMillis()) return@launch

            // НОВОЕ: тихие часы — подавляем уведомления в заданном ночном окне.
            val quietEnabled = userSettingsPreferences.quietHoursEnabled.first()
            if (quietEnabled) {
                val start = userSettingsPreferences.quietHoursStart.first()
                val end = userSettingsPreferences.quietHoursEnd.first()
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val inQuiet = if (start <= end) hour in start until end else (hour >= start || hour < end)
                if (inQuiet) return@launch
            }

            val soundEnabled = userSettingsPreferences.notificationSound.first()
            val vibrationEnabled = userSettingsPreferences.notificationVibration.first()

            // НОВОЕ: скрытие текста в уведомлениях — показываем обезличенное уведомление.
            val hidePreview = userSettingsPreferences.hideNotificationPreview.first()
            if (hidePreview) {
                NotificationHelper.showMessageNotification(
                    context = applicationContext,
                    chatId = chatId,
                    senderName = "Новое сообщение",
                    messageText = "Откройте приложение, чтобы прочитать",
                    soundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    // Текст скрыт настройками приватности — быстрый инлайн-ответ здесь неуместен
                    // (не видно, на что отвечаешь), но отметить прочитанным всё же можно.
                    isPrivateChat = isPrivateChat
                )
                return@launch
            }

            // Копим историю на клиенте — rich-уведомление (MessagingStyle) показывает
            // стек последних сообщений чата, а не только это одно.
            val history = notificationMessageStore.addMessage(
                chatId = chatId,
                senderName = senderName,
                text = messageText,
                timestampMillis = System.currentTimeMillis()
            )

            NotificationHelper.showMessageNotification(
                context = applicationContext,
                chatId = chatId,
                senderName = senderName,
                messageText = messageText,
                history = history,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                isPrivateChat = isPrivateChat
            )
        }
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
