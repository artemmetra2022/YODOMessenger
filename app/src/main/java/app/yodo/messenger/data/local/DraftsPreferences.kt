package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.draftsDataStore by preferencesDataStore(name = "yodo_drafts")

/** Черновики недописанных сообщений — сохраняются локально на устройстве, отдельно по каждому чату. */
@Singleton
class DraftsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun keyFor(chatId: String) = stringPreferencesKey("draft_$chatId")

    suspend fun getDraft(chatId: String): String {
        return context.draftsDataStore.data
            .map { prefs -> prefs[keyFor(chatId)] ?: "" }
            .first()
    }

    suspend fun saveDraft(chatId: String, text: String) {
        context.draftsDataStore.edit { prefs ->
            if (text.isBlank()) {
                prefs.remove(keyFor(chatId))
            } else {
                prefs[keyFor(chatId)] = text
            }
        }
    }

    suspend fun clearDraft(chatId: String) {
        context.draftsDataStore.edit { prefs -> prefs.remove(keyFor(chatId)) }
    }

    suspend fun clearAllDrafts() {
        context.draftsDataStore.edit { prefs -> prefs.clear() }
    }
}
