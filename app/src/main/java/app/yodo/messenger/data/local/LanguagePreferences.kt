package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.languageDataStore by preferencesDataStore(name = "yodo_language")

/** Код языка приложения. "system" — использовать системный язык устройства. */
enum class AppLanguage(val code: String, val labelResId: Int) {
    SYSTEM("system", app.yodo.messenger.R.string.settings_language_system),
    RU("ru", app.yodo.messenger.R.string.settings_language_ru),
    EN("en", app.yodo.messenger.R.string.settings_language_en);

    companion object {
        fun fromCode(code: String): AppLanguage = entries.find { it.code == code } ?: SYSTEM
    }
}

/**
 * Хранит выбор языка интерфейса (регистрация → онбординг → настройки — общее значение,
 * как и с "Расширенными опросами"). По умолчанию — SYSTEM (следует системному языку устройства).
 */
@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val languageKey = stringPreferencesKey("app_language_code")

    val languageCode: Flow<String> = context.languageDataStore.data.map { prefs ->
        prefs[languageKey] ?: AppLanguage.SYSTEM.code
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.languageDataStore.edit { prefs ->
            prefs[languageKey] = language.code
        }
    }
}
