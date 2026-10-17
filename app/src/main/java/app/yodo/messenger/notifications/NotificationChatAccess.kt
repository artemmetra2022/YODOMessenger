package app.yodo.messenger.notifications

import app.yodo.messenger.domain.model.Permission
import app.yodo.messenger.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

internal data class NotificationChatContext(val account: String,val chat: String,val type: String,val title: String,
    val actions: ChatNotificationActions,val serverMuted: Boolean)

/** Re-checks membership and publishing rights; a PendingIntent or FCM flag never grants them. */
@Singleton
class NotificationChatAccess @Inject constructor(private val auth: FirebaseAuth,private val db: FirebaseFirestore,
    private val chats: ChatRepository) {
    internal suspend fun load(uid: String,chat: String,hidePreview: Boolean,requireServer: Boolean=false): NotificationChatContext? {
        if(auth.currentUser?.uid!=uid)return null
        val ref=db.collection("chats").document(chat)
        val doc=try { withTimeout(3_000) {ref.get(Source.SERVER).await()} }
            catch(e: Exception) {
                if(e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException)throw e
                if(requireServer)return null
                try { ref.get(Source.CACHE).await() }catch(_: Exception){return null}
            }
        if(!doc.exists() || auth.currentUser?.uid!=uid)return null
        val type=doc.getString("type").orEmpty()
        val globalAdmin=auth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS
        val official=chat==ChatRepository.OFFICIAL_CHANNEL_ID
        val member=uid in (doc.get("participantIds") as? List<*>).orEmpty() || official ||
            (type=="SUPPORT"&&(chat=="support_$uid"||globalAdmin))
        if(!member || uid in (doc.get("bannedUserIds") as? List<*>).orEmpty())return null
        val publisher=if(type!="CHANNEL")true else if(official)globalAdmin else {
            uid==doc.getString("createdBy") || uid in (doc.get("adminIds") as? List<*>).orEmpty() ||
                try { withTimeout(2_000){chats.getMemberPermissions(chat,uid).has(Permission.POST_MESSAGES)} }
                catch(e: Exception){if(e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException)throw e;false}
        }
        if(auth.currentUser?.uid!=uid)return null
        return NotificationChatContext(uid,chat,type,doc.getString("title").orEmpty(),
            chatNotificationActions(type,member,publisher,hidePreview),(doc.get("muted") as? Map<*,*>)?.get(uid)==true)
    }
}
