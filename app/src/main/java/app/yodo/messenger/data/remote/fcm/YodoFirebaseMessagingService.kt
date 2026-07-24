package app.yodo.messenger.data.remote.fcm

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
 * data: { chatId, senderName, messageText }
 */
@AndroidEntryPoint
class YodoFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var firestore: FirebaseFirestore

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var userSettingsPreferences: UserSettingsPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val chatId = message.data["chatId"] ?: return
        val senderName = message.data["senderName"] ?: "Yodo Messenger"
        val messageText = message.data["messageText"] ?: message.notification?.body.orEmpty()

        serviceScope.launch {
            val globallyMuted = userSettingsPreferences.muteAllNotifications.first()
            if (globallyMuted) return@launch

            val soundEnabled = userSettingsPreferences.notificationSound.first()
            val vibrationEnabled = userSettingsPreferences.notificationVibration.first()

            NotificationHelper.showMessageNotification(
                context = applicationContext,
                chatId = chatId,
                senderName = senderName,
                messageText = messageText,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled
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
