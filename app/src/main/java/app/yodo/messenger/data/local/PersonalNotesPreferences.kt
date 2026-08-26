package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Одна личная заметка пользователя (личный блокнот). Хранится только на устройстве. */
data class PersonalNote(
    val id: String,
    val text: String,
    val createdAt: Long
)

// НОВОЕ: личный блокнот — приватные заметки пользователя, хранятся локально в отдельном DataStore.
private val Context.personalNotesDataStore by preferencesDataStore(name = "yodo_personal_notes")

@Singleton
class PersonalNotesPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notesKey = stringPreferencesKey("notes_json")

    /** Список заметок, новые сверху. */
    val notes: Flow<List<PersonalNote>> = context.personalNotesDataStore.data.map { prefs ->
        parse(prefs[notesKey]).sortedByDescending { it.createdAt }
    }

    suspend fun addNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        context.personalNotesDataStore.edit { prefs ->
            val current = parse(prefs[notesKey])
            val note = PersonalNote(
                id = System.currentTimeMillis().toString() + "_" + (0..9999).random(),
                text = trimmed,
                createdAt = System.currentTimeMillis()
            )
            prefs[notesKey] = serialize(current + note)
        }
    }

    suspend fun removeNote(id: String) {
        context.personalNotesDataStore.edit { prefs ->
            val current = parse(prefs[notesKey]).filter { it.id != id }
            prefs[notesKey] = serialize(current)
        }
    }

    private fun parse(json: String?): List<PersonalNote> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PersonalNote(
                    id = id,
                    text = o.optString("text"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serialize(list: List<PersonalNote>): String {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("text", n.text)
                put("createdAt", n.createdAt)
            })
        }
        return arr.toString()
    }
}
