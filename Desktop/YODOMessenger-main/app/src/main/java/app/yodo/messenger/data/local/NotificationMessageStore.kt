package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationStoreDataStore by preferencesDataStore(name = "yodo_notification_messages")

/** Одна реплика, накопленная для показа в MessagingStyle-уведомлении. */
data class StoredNotificationMessage(
    val senderName: String,
    val text: String,
    val timestampMillis: Long
)

/**
 * Копит последние сообщения по каждому чату специально для rich-уведомлений
 * (MessagingStyle). FCM присылает по одному сообщению за push, поэтому
 * историю для "стека" уведомлений собираем на клиенте, а не тянем из Firestore
 * при получении push (лишняя задержка и трафик).
 *
 * Хранилище не связано с историей самого чата и очищается при его открытии.
 */
@Singleton
class NotificationMessageStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_MESSAGES_PER_CHAT = 5
    }

    private fun keyForChat(chatId: String) = stringPreferencesKey("chat_messages_$chatId")

    suspend fun addMessage(chatId: String, senderName: String, text: String, timestampMillis: Long): List<StoredNotificationMessage> {
        val key = keyForChat(chatId)
        var updated: List<StoredNotificationMessage> = emptyList()
        context.notificationStoreDataStore.edit { prefs ->
            val existing = decode(prefs[key])
            updated = (existing + StoredNotificationMessage(senderName, text, timestampMillis))
                .takeLast(MAX_MESSAGES_PER_CHAT)
            prefs[key] = encode(updated)
        }
        return updated
    }

    suspend fun getMessages(chatId: String): List<StoredNotificationMessage> {
        val key = keyForChat(chatId)
        val prefs = context.notificationStoreDataStore.data.first()
        return decode(prefs[key])
    }

    suspend fun clear(chatId: String) {
        val key = keyForChat(chatId)
        context.notificationStoreDataStore.edit { prefs -> prefs.remove(key) }
    }

    private fun encode(messages: List<StoredNotificationMessage>): String {
        val array = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject()
            obj.put("senderName", msg.senderName)
            obj.put("text", msg.text)
            obj.put("timestampMillis", msg.timestampMillis)
            array.put(obj)
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<StoredNotificationMessage> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                StoredNotificationMessage(
                    senderName = obj.getString("senderName"),
                    text = obj.getString("text"),
                    timestampMillis = obj.getLong("timestampMillis")
                )
            }
        }.getOrDefault(emptyList())
    }
}
