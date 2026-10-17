package app.yodo.messenger.data.remote.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import app.yodo.messenger.R
import app.yodo.messenger.data.local.NotificationMessageStore
import app.yodo.messenger.data.local.NotificationSilenceStore
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.data.outbox.TextMessageOutbox
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.notifications.NotificationChatAccess
import app.yodo.messenger.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Account-bound actions; receiver is not exported and never trusts publishing flags in an intent. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var notificationMessageStore: NotificationMessageStore
    @Inject lateinit var outbox: TextMessageOutbox
    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var chatAccess: NotificationChatAccess
    @Inject lateinit var silenceStore: NotificationSilenceStore
    @Inject lateinit var settings: UserSettingsPreferences

    override fun onReceive(context: Context,intent: Intent) {
        val action=intent.action?.takeIf { it in setOf(ACTION_MARK_READ,ACTION_REPLY,ACTION_MUTE,ACTION_UNMUTE) } ?: return
        val chat=intent.getStringExtra(EXTRA_CHAT_ID)?.takeIf { it.isNotBlank() } ?: return
        val uid=intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return
        if(auth.currentUser?.uid!=uid)return
        val reply=if(action==ACTION_REPLY)RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim() else null
        if(action==ACTION_REPLY&&reply.isNullOrBlank())return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(9_000) {
                    if(auth.currentUser?.uid!=uid)return@withTimeout
                    if(action==ACTION_MUTE || action==ACTION_UNMUTE) {
                        // Local sound-only setting works offline, doesn't mark messages read and doesn't mutate server membership/mute.
                        if(action==ACTION_MUTE)silenceStore.silence(uid,chat)else silenceStore.unsilence(uid,chat)
                        if(auth.currentUser?.uid==uid) {
                            NotificationHelper.cancelNotification(context,chat,uid)
                            toast(context,if(action==ACTION_MUTE)R.string.notification_chat_silenced else R.string.notification_chat_sound_restored)
                        }
                        return@withTimeout
                    }
                    val access=chatAccess.load(uid,chat,settings.hideNotificationPreview.first(),requireServer=true)
                        ?: error("Чат недоступен")
                    check(auth.currentUser?.uid==uid)
                    when(action) {
                        ACTION_MARK_READ -> {
                            check(access.actions.markRead)
                            messageRepository.markChatAsRead(chat,expectedAccount=uid)
                            if(auth.currentUser?.uid==uid) {
                                notificationMessageStore.clear(chat,uid)
                                NotificationHelper.cancelNotification(context,chat,uid)
                            }
                        }
                        ACTION_REPLY -> {
                            check(access.actions.reply) { "Нет прав для ответа" }
                            // Same durable FIFO as the composer: no lost inline replies or false "sent" confirmations.
                            outbox.enqueue(chat,reply!!,intent.getStringExtra(EXTRA_TOPIC_ID),null,false,null,false,expectedOwner=uid)
                            if(auth.currentUser?.uid==uid) {
                                NotificationHelper.cancelNotification(context,chat,uid)
                                NotificationHelper.showReplyQueuedNotification(context,chat,listOf(
                                    app.yodo.messenger.data.local.StoredNotificationMessage("Вы",context.getString(R.string.notification_reply_queued),System.currentTimeMillis())))
                                toast(context,R.string.notification_reply_queued)
                            }
                        }
                    }
                }
            } catch(e: Exception) {
                android.util.Log.w("NotificationAction","Notification action failed",e)
                if(auth.currentUser?.uid==uid)toast(context,if(action==ACTION_REPLY)R.string.notification_reply_failed else R.string.notification_action_failed)
            } finally { pending.finish() }
        }
    }
    private fun toast(context: Context,id: Int) {
        android.os.Handler(context.mainLooper).post {Toast.makeText(context,context.getString(id),Toast.LENGTH_SHORT).show()}
    }
    companion object {
        const val ACTION_MARK_READ="app.yodo.messenger.action.NOTIFICATION_MARK_READ"
        const val ACTION_REPLY="app.yodo.messenger.action.NOTIFICATION_REPLY"
        const val ACTION_MUTE="app.yodo.messenger.action.NOTIFICATION_MUTE"
        const val ACTION_UNMUTE="app.yodo.messenger.action.NOTIFICATION_UNMUTE"
        const val EXTRA_CHAT_ID="extra_chat_id"
        const val EXTRA_ACCOUNT_ID="extra_account_id"
        const val EXTRA_TOPIC_ID="extra_topic_id"
        const val KEY_REPLY_TEXT="key_reply_text"
    }
}
