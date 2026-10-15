package app.yodo.messenger.util

/**
 * НОВОЕ: общий хелпер для хранения списков строк (шаблоны, дела и т.п.)
 * в SharedPreferences в виде текста, разделённого переводами строк.
 * Вынесено из ToolsScreen.kt, чтобы список шаблонов был доступен
 * и в чате (кнопка «Быстрые ответы»), и на экране «Фишки и инструменты».
 */
fun loadPrefsList(prefs: android.content.SharedPreferences, key: String): List<String> =
    prefs.getString(key, "")!!.split("\n").filter { it.isNotBlank() }

fun savePrefsList(prefs: android.content.SharedPreferences, key: String, list: List<String>) {
    prefs.edit().putString(key, list.joinToString("\n")).apply()
}
