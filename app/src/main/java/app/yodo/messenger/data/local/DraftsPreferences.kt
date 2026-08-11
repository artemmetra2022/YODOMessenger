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

private const val DRAFT_KEY_PREFIX = "draft_"

private val Context.draftsDataStore by preferencesDataStore(name = "yodo_drafts")

/** Черновики недописанных сообщений — сохраняются локально на устройстве, отдельно по каждому чату. */
@Singleton
class DraftsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun keyFor(chatId: String) = stringPreferencesKey("$DRAFT_KEY_PREFIX$chatId")

    suspend fun getDraft(chatId: String): String {
        return context.draftsDataStore.data
            .map { prefs -> prefs[keyFor(chatId)] ?: "" }
            .first()
    }

    /**
     * НОВОЕ (п.39): поток всех черновиков сразу — chatId -> текст черновика.
     * Используется в списке чатов (главном меню), чтобы показывать "Черновик: ..."
     * вместо последнего сообщения, пока пользователь не отправил недописанный текст.
     */
    fun observeAllDrafts(): Flow<Map<String, String>> {
        return context.draftsDataStore.data.map { prefs ->
            prefs.asMap().entries
                .mapNotNull { (key, value) ->
                    val name = key.name
                    if (name.startsWith(DRAFT_KEY_PREFIX) && value is String && value.isNotBlank()) {
                        name.removePrefix(DRAFT_KEY_PREFIX) to value
                    } else null
                }
                .toMap()
        }
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
