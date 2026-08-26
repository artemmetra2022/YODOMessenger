package app.yodo.messenger.offline

import android.content.Context

/** Локальная визитка пользователя для общения без аккаунта и интернета. */
data class OfflineProfile(
    val displayName: String,
    val bio: String = "",
    val status: String = "",
    val emoji: String = "",
    val colorIndex: Int = 0
) {
    val initials: String
        get() = displayName.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "Y" }
}

class OfflineProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): OfflineProfile = OfflineProfile(
        displayName = prefs.getString(KEY_NAME, "") ?: "",
        bio = prefs.getString(KEY_BIO, "") ?: "",
        status = prefs.getString(KEY_STATUS, "") ?: "",
        emoji = prefs.getString(KEY_EMOJI, "") ?: "",
        colorIndex = prefs.getInt(KEY_COLOR, 0)
    )

    fun save(profile: OfflineProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.displayName)
            .putString(KEY_BIO, profile.bio)
            .putString(KEY_STATUS, profile.status)
            .putString(KEY_EMOJI, profile.emoji)
            .putInt(KEY_COLOR, profile.colorIndex)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "offline_prefs"
        private const val KEY_NAME = "offline_display_name"
        private const val KEY_BIO = "offline_profile_bio"
        private const val KEY_STATUS = "offline_profile_status"
        private const val KEY_EMOJI = "offline_profile_emoji"
        private const val KEY_COLOR = "offline_profile_color"
    }
}
