package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationSilenceDataStore by preferencesDataStore(name="notification_chat_silence")
/** Sound + vibration only; notifications remain visible. Independent for each account and device. */
@Singleton
class NotificationSilenceStore @Inject constructor(@ApplicationContext private val context: Context) {
    private fun key(uid: String)=stringSetPreferencesKey("account_"+hash(uid))
    private fun hash(s: String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    suspend fun isSilent(uid: String,chat: String)=hash(chat) in context.notificationSilenceDataStore.data.first()[key(uid)].orEmpty()
    suspend fun silence(uid: String,chat: String) { context.notificationSilenceDataStore.edit { it[key(uid)]=it[key(uid)].orEmpty()+hash(chat) } }
    suspend fun unsilence(uid: String,chat: String) { context.notificationSilenceDataStore.edit { it[key(uid)]=it[key(uid)].orEmpty()-hash(chat) } }
    suspend fun clear(uid: String) { context.notificationSilenceDataStore.edit { it.remove(key(uid)) } }
}
