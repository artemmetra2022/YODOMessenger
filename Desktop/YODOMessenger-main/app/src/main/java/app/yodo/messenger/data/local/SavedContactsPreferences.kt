package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.yodo.messenger.domain.model.OfflineContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// НОВОЕ (офлайн обмен контактами по QR): отдельный DataStore для сохранённых по QR контактов.
private val Context.savedContactsDataStore by preferencesDataStore(name = "yodo_saved_contacts")

@Singleton
class SavedContactsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val contactsKey = stringPreferencesKey("contacts_json")

    /** Список сохранённых контактов, отсортированный по дате добавления (новые сверху). */
    val contacts: Flow<List<OfflineContact>> = context.savedContactsDataStore.data.map { prefs ->
        parse(prefs[contactsKey]).sortedByDescending { it.addedAt }
    }

    /** Добавляет/обновляет контакт (по uid). */
    suspend fun addContact(contact: OfflineContact) {
        context.savedContactsDataStore.edit { prefs ->
            val current = parse(prefs[contactsKey]).filter { it.uid != contact.uid }
            prefs[contactsKey] = serialize(current + contact)
        }
    }

    /** Удаляет контакт по uid. */
    suspend fun removeContact(uid: String) {
        context.savedContactsDataStore.edit { prefs ->
            val current = parse(prefs[contactsKey]).filter { it.uid != uid }
            prefs[contactsKey] = serialize(current)
        }
    }

    private fun parse(json: String?): List<OfflineContact> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uid = o.optString("uid").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                OfflineContact(
                    uid = uid,
                    displayName = o.optString("name"),
                    username = o.optString("username").takeIf { it.isNotBlank() },
                    publicKey = o.optString("publicKey").takeIf { it.isNotBlank() },
                    addedAt = o.optLong("addedAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serialize(list: List<OfflineContact>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("uid", c.uid)
                put("name", c.displayName)
                c.username?.let { put("username", it) }
                c.publicKey?.let { put("publicKey", it) }
                put("addedAt", c.addedAt)
            })
        }
        return arr.toString()
    }
}
