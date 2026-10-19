package app.yodo.messenger.features.widget

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Локальный кэш для виджетов: рисуем мгновенно и без сети, а свежие данные подтягивает
 * [WidgetRefresher]. Файл исключён из резервных копий (см. backup_rules / data_extraction_rules).
 */
object WidgetStore {
    private const val PREFS = "yodo_widget_cache"
    private const val KEY_UNREAD = "unread"
    private const val KEY_WEATHER = "weather"
    private const val KEY_SCHOOL_HOLIDAY = "school_holiday"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readUnread(context: Context): UnreadSnapshot? =
        prefs(context).getString(KEY_UNREAD, null)
            ?.let { runCatching { json.decodeFromString<UnreadSnapshot>(it) }.getOrNull() }

    fun writeUnread(context: Context, value: UnreadSnapshot) {
        prefs(context).edit().putString(KEY_UNREAD, json.encodeToString(value)).apply()
    }

    fun clearUnread(context: Context) {
        prefs(context).edit().remove(KEY_UNREAD).apply()
    }

    fun readWeather(context: Context): WeatherSnapshot? =
        prefs(context).getString(KEY_WEATHER, null)
            ?.let { runCatching { json.decodeFromString<WeatherSnapshot>(it) }.getOrNull() }

    fun writeWeather(context: Context, value: WeatherSnapshot) {
        prefs(context).edit().putString(KEY_WEATHER, json.encodeToString(value)).apply()
    }

    /** Дата каникул от админа (ISO, «ГГГГ-ММ-ДД»); пусто — виджет берёт зашитую SchoolData.HOLIDAY_DATE_ISO. */
    fun readSchoolHoliday(context: Context): String =
        prefs(context).getString(KEY_SCHOOL_HOLIDAY, null).orEmpty()

    fun writeSchoolHoliday(context: Context, isoDate: String) {
        prefs(context).edit().putString(KEY_SCHOOL_HOLIDAY, isoDate).apply()
    }
}
