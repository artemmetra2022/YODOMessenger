package app.yodo.messenger.data.remote.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import app.yodo.messenger.R
import app.yodo.messenger.data.local.NotificationMessageStore
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.SendMessageResult
import app.yodo.messenger.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.goAsync
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (быстрые действия "Прочитано"/"Ответить" в push-уведомлении личного чата).
 *
 * Обрабатывает нажатия на кнопки действия прямо в уведомлении, без открытия
 * приложения (обе NotificationCompat.Action собраны с setShowsUserInterface(false)
 * в NotificationHelper.showMessageNotification):
 *  - ACTION_MARK_READ — помечает чат прочитанным через тот же MessageRepository.
 *    markChatAsRead(), который используется при открытии экрана чата, и убирает
 *    уведомление.
 *  - ACTION_REPLY — достаёт текст, введённый пользователем через системный
 *    RemoteInput (инлайн-поле ввода прямо под уведомлением), отправляет его как
 *    обычное сообщение через MessageRepository.sendMessage() и обновляет
 *    уведомление, показав отправленный текст (либо ошибку, если отправка
 *    не удалась) — как это делают Telegram/WhatsApp.
 *
 * Работает через goAsync(), так как BroadcastReceiver.onReceive выполняется на
 * главном потоке и обычно должен завершиться быстро, а здесь нужен suspend-вызов
 * в Firestore. goAsync() продлевает жизнь ресивера до вызова PendingResult.finish().
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    lateinit var notificationMessageStore: NotificationMessageStore

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return

        when (intent.action) {
            ACTION_MARK_READ -> handleMarkRead(context, chatId)
            ACTION_REPLY -> handleReply(context, intent, chatId)
        }
    }

    private fun handleMarkRead(context: Context, chatId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                messageRepository.markChatAsRead(chatId)
                notificationMessageStore.clear(chatId)
                NotificationHelper.cancelNotification(context, chatId)
            } catch (e: Exception) {
                android.util.Log.w("NotificationActionReceiver", "markChatAsRead failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleReply(context: Context, intent: Intent, chatId: String) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()

        if (replyText.isNullOrEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = messageRepository.sendMessage(chatId = chatId, text = replyText)
                when (result) {
                    is SendMessageResult.Success -> {
                        // НОВОЕ (подтверждение "Ответ отправлен"): дописываем свою реплику в
                        // локальную историю уведомления и перерисовываем то же уведомление —
                        // пользователь сразу видит в шторке, что ответ ушёл (как в Telegram/
                        // WhatsApp), не открывая приложение. Полная синхронизация статуса
                        // (SENDING/READ и т.д.) произойдёт как обычно при следующем открытии чата.
                        val history = notificationMessageStore.addMessage(
                            chatId = chatId,
                            senderName = MY_REPLY_SENDER_LABEL,
                            text = replyText,
                            timestampMillis = System.currentTimeMillis()
                        )
                        NotificationHelper.showReplySentNotification(
                            context = context,
                            chatId = chatId,
                            history = history
                        )
                        showReplySentToast(context)
                    }
                    is SendMessageResult.Error -> {
                        showReplyFailedToast(context)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("NotificationActionReceiver", "reply send failed", e)
                showReplyFailedToast(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showReplySentToast(context: Context) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(
                context,
                context.getString(R.string.notification_reply_sent),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showReplyFailedToast(context: Context) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(
                context,
                context.getString(R.string.notification_reply_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        const val ACTION_MARK_READ = "app.yodo.messenger.action.NOTIFICATION_MARK_READ"
        const val ACTION_REPLY = "app.yodo.messenger.action.NOTIFICATION_REPLY"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val KEY_REPLY_TEXT = "key_reply_text"
        // Имя "отправителя" для собственной реплики в локальной истории уведомления —
        // MessagingStyle.addMessage требует Person, а не флаг "это я"; используем то же
        // условное имя, что и в NotificationHelper.showMessageNotification ("Вы").
        private const val MY_REPLY_SENDER_LABEL = "Вы"
    }
}
