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

private val Context.settingsDataStore by preferencesDataStore(name = "yodo_user_settings")

enum class FontSize(val scale: Float) {
    SMALL(0.9f), MEDIUM(1.0f), LARGE(1.15f)
}

@Singleton
class UserSettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sendOnEnterKey = booleanPreferencesKey("send_on_enter")
    private val fontSizeKey = stringPreferencesKey("font_size")
    private val showOnlineStatusKey = booleanPreferencesKey("show_online_status")
    private val showReadReceiptsKey = booleanPreferencesKey("show_read_receipts")
    private val autoDownloadImagesKey = booleanPreferencesKey("auto_download_images")
    private val notificationSoundKey = booleanPreferencesKey("notification_sound")
    private val notificationVibrationKey = booleanPreferencesKey("notification_vibration")
    private val muteAllNotificationsKey = booleanPreferencesKey("mute_all_notifications")

    /** true = Enter отправляет сообщение (как в Telegram Desktop); false = Enter — перенос строки. */
    val sendOnEnter: Flow<Boolean> = context.settingsDataStore.data.map { it[sendOnEnterKey] ?: true }

    val fontSize: Flow<FontSize> = context.settingsDataStore.data.map { prefs ->
        prefs[fontSizeKey]?.let { raw -> runCatching { FontSize.valueOf(raw) }.getOrNull() } ?: FontSize.MEDIUM
    }

    /** Разрешить другим видеть мой статус "в сети" / "был(а) недавно". */
    val showOnlineStatus: Flow<Boolean> = context.settingsDataStore.data.map { it[showOnlineStatusKey] ?: true }

    /** Разрешить отправителю видеть, что я прочитал(а) его сообщение (двойная галочка). */
    val showReadReceipts: Flow<Boolean> = context.settingsDataStore.data.map { it[showReadReceiptsKey] ?: true }

    /** Показывать фото сразу в чате или только по тапу (экономия трафика). */
    val autoDownloadImages: Flow<Boolean> = context.settingsDataStore.data.map { it[autoDownloadImagesKey] ?: true }

    val notificationSound: Flow<Boolean> = context.settingsDataStore.data.map { it[notificationSoundKey] ?: true }
    val notificationVibration: Flow<Boolean> = context.settingsDataStore.data.map { it[notificationVibrationKey] ?: true }
    val muteAllNotifications: Flow<Boolean> = context.settingsDataStore.data.map { it[muteAllNotificationsKey] ?: false }

    suspend fun setSendOnEnter(enabled: Boolean) {
        context.settingsDataStore.edit { it[sendOnEnterKey] = enabled }
    }

    suspend fun setFontSize(size: FontSize) {
        context.settingsDataStore.edit { it[fontSizeKey] = size.name }
    }

    suspend fun setShowOnlineStatus(enabled: Boolean) {
        context.settingsDataStore.edit { it[showOnlineStatusKey] = enabled }
    }

    suspend fun setShowReadReceipts(enabled: Boolean) {
        context.settingsDataStore.edit { it[showReadReceiptsKey] = enabled }
    }

    suspend fun setAutoDownloadImages(enabled: Boolean) {
        context.settingsDataStore.edit { it[autoDownloadImagesKey] = enabled }
    }

    suspend fun setNotificationSound(enabled: Boolean) {
        context.settingsDataStore.edit { it[notificationSoundKey] = enabled }
    }

    suspend fun setNotificationVibration(enabled: Boolean) {
        context.settingsDataStore.edit { it[notificationVibrationKey] = enabled }
    }

    suspend fun setMuteAllNotifications(enabled: Boolean) {
        context.settingsDataStore.edit { it[muteAllNotificationsKey] = enabled }
    }
}
