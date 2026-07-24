package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "yodo_settings")

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme_enabled")
    private val useSystemThemeKey = booleanPreferencesKey("use_system_theme")
    private val colorThemeKey = stringPreferencesKey("color_theme_name")

    val useSystemTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[useSystemThemeKey] ?: true
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[darkThemeKey] ?: true
    }

    val colorThemeName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[colorThemeKey] ?: "BLUE"
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[darkThemeKey] = enabled
            prefs[useSystemThemeKey] = false
        }
    }

    suspend fun setUseSystemTheme() {
        context.dataStore.edit { prefs ->
            prefs[useSystemThemeKey] = true
        }
    }

    suspend fun setColorTheme(name: String) {
        context.dataStore.edit { prefs ->
            prefs[colorThemeKey] = name
        }
    }
}
