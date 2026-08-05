package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.yodo.messenger.domain.model.ChatFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "yodo_user_settings")

enum class FontSize(val scale: Float) {
    SMALL(0.9f), MEDIUM(1.0f), LARGE(1.15f)
}

enum class PinRequirement(val displayName: String) {
    NEVER("Никогда"),
    ON_CLOSE("После закрытия приложения"),
    ON_BACKGROUND("После сворачивания приложения")
}

sealed class PinCheckResult {
    data object Success : PinCheckResult()
    data class WrongPin(val attemptsRemaining: Int) : PinCheckResult()
    data class LockedOut(val unlockAtMillis: Long) : PinCheckResult()
}

// НОВОЕ (п.13): типы фона чата
enum class ChatBackgroundType(val displayName: String) {
    DEFAULT("Стандартный"),
    GRADIENT_1("Градиент 1"),
    GRADIENT_2("Градиент 2"),
    GRADIENT_3("Градиент 3"),
    GRADIENT_4("Градиент 4"),
    CUSTOM_IMAGE("Своё фото")
}

private const val MAX_PIN_ATTEMPTS = 5
private const val PIN_LOCKOUT_MS = 30_000L

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
    private val hideKeyboardOnSendKey = booleanPreferencesKey("hide_keyboard_on_send")
    private val advancedPollsEnabledKey = booleanPreferencesKey("advanced_polls_enabled")
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val pinSaltKey = stringPreferencesKey("pin_salt")
    private val pinRequirementKey = stringPreferencesKey("pin_requirement")
    private val pinFailedAttemptsKey = intPreferencesKey("pin_failed_attempts")
    private val pinLockedUntilKey = longPreferencesKey("pin_locked_until")
    private val notificationPermissionAskedKey = booleanPreferencesKey("notification_permission_asked")

    // НОВОЕ (п.18): автоудаление аккаунта
    private val autoDeleteEnabledKey = booleanPreferencesKey("auto_delete_enabled")
    private val autoDeleteDaysKey = intPreferencesKey("auto_delete_days")
    private val lastActiveTimestampKey = longPreferencesKey("last_active_timestamp")

    // НОВОЕ (п.13): фон чата
    private val chatBackgroundTypeKey = stringPreferencesKey("chat_background_type")
    private val chatBackgroundCustomPathKey = stringPreferencesKey("chat_background_custom_path")

    // НОВОЕ (п.4): папки чатов
    private val chatFoldersJsonKey = stringPreferencesKey("chat_folders_json")

    val sendOnEnter: Flow<Boolean> = context.settingsDataStore.data.map { it[sendOnEnterKey] ?: true }
    val fontSize: Flow<FontSize> = context.settingsDataStore.data.map { prefs ->
        prefs[fontSizeKey]?.let { raw -> runCatching { FontSize.valueOf(raw) }.getOrNull() } ?: FontSize.MEDIUM
    }
    val showOnlineStatus: Flow<Boolean> = context.settingsDataStore.data.map { it[showOnlineStatusKey] ?: true }
    val showReadReceipts: Flow<Boolean> = context.settingsDataStore.data.map { it[showReadReceiptsKey] ?: true }
    val autoDownloadImages: Flow<Boolean> = context.settingsDataStore.data.map { it[autoDownloadImagesKey] ?: true }
    val notificationSound: Flow<Boolean> = context.settingsDataStore.data.map { it[notificationSoundKey] ?: true }
    val notificationVibration: Flow<Boolean> = context.settingsDataStore.data.map { it[notificationVibrationKey] ?: true }
    val muteAllNotifications: Flow<Boolean> = context.settingsDataStore.data.map { it[muteAllNotificationsKey] ?: false }
    val hideKeyboardOnSend: Flow<Boolean> = context.settingsDataStore.data.map { it[hideKeyboardOnSendKey] ?: true }
    val advancedPollsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[advancedPollsEnabledKey] ?: false }

    val pinRequirement: Flow<PinRequirement> = context.settingsDataStore.data.map { prefs ->
        prefs[pinRequirementKey]?.let { raw -> runCatching { PinRequirement.valueOf(raw) }.getOrNull() } ?: PinRequirement.NEVER
    }
    val isPinSet: Flow<Boolean> = context.settingsDataStore.data.map { !it[pinHashKey].isNullOrBlank() }

    val notificationPermissionAsked: Flow<Boolean> =
        context.settingsDataStore.data.map { it[notificationPermissionAskedKey] ?: false }

    // НОВОЕ (п.18): автоудаление аккаунта
    val autoDeleteEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[autoDeleteEnabledKey] ?: false }
    val autoDeleteDays: Flow<Int> = context.settingsDataStore.data.map { it[autoDeleteDaysKey] ?: 30 }
    val lastActiveTimestamp: Flow<Long> = context.settingsDataStore.data.map { it[lastActiveTimestampKey] ?: 0L }

    // НОВОЕ (п.13): фон чата
    val chatBackgroundType: Flow<ChatBackgroundType> = context.settingsDataStore.data.map { prefs ->
        prefs[chatBackgroundTypeKey]?.let { raw ->
            runCatching { ChatBackgroundType.valueOf(raw) }.getOrNull()
        } ?: ChatBackgroundType.DEFAULT
    }
    val chatBackgroundCustomPath: Flow<String> = context.settingsDataStore.data.map { it[chatBackgroundCustomPathKey] ?: "" }

    // НОВОЕ (п.4): папки чатов
    val chatFolders: Flow<List<ChatFolder>> = context.settingsDataStore.data.map { prefs ->
        prefs[chatFoldersJsonKey]?.let { json ->
            runCatching { Json.decodeFromString<List<ChatFolder>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun setNotificationPermissionAsked(asked: Boolean) {
        context.settingsDataStore.edit { it[notificationPermissionAskedKey] = asked }
    }
    suspend fun setSendOnEnter(enabled: Boolean) { context.settingsDataStore.edit { it[sendOnEnterKey] = enabled } }
    suspend fun setFontSize(size: FontSize) { context.settingsDataStore.edit { it[fontSizeKey] = size.name } }
    suspend fun setShowOnlineStatus(enabled: Boolean) { context.settingsDataStore.edit { it[showOnlineStatusKey] = enabled } }
    suspend fun setShowReadReceipts(enabled: Boolean) { context.settingsDataStore.edit { it[showReadReceiptsKey] = enabled } }
    suspend fun setAutoDownloadImages(enabled: Boolean) { context.settingsDataStore.edit { it[autoDownloadImagesKey] = enabled } }
    suspend fun setNotificationSound(enabled: Boolean) { context.settingsDataStore.edit { it[notificationSoundKey] = enabled } }
    suspend fun setNotificationVibration(enabled: Boolean) { context.settingsDataStore.edit { it[notificationVibrationKey] = enabled } }
    suspend fun setMuteAllNotifications(enabled: Boolean) { context.settingsDataStore.edit { it[muteAllNotificationsKey] = enabled } }
    suspend fun setHideKeyboardOnSend(enabled: Boolean) { context.settingsDataStore.edit { it[hideKeyboardOnSendKey] = enabled } }
    suspend fun setAdvancedPollsEnabled(enabled: Boolean) { context.settingsDataStore.edit { it[advancedPollsEnabledKey] = enabled } }

    suspend fun setPin(pin: String) {
        val salt = app.yodo.messenger.core.util.PinHasher.generateSalt()
        val hash = app.yodo.messenger.core.util.PinHasher.hash(pin, salt)
        context.settingsDataStore.edit {
            it[pinSaltKey] = salt
            it[pinHashKey] = hash
        }
    }

    suspend fun clearPin() {
        context.settingsDataStore.edit {
            it.remove(pinHashKey)
            it.remove(pinSaltKey)
            it[pinRequirementKey] = PinRequirement.NEVER.name
        }
    }

    suspend fun setPinRequirement(requirement: PinRequirement) {
        context.settingsDataStore.edit { it[pinRequirementKey] = requirement.name }
    }

    suspend fun verifyPin(pin: String): PinCheckResult {
        val prefs = context.settingsDataStore.data.first()
        val now = System.currentTimeMillis()
        val lockedUntil = prefs[pinLockedUntilKey] ?: 0L
        if (lockedUntil > now) {
            return PinCheckResult.LockedOut(lockedUntil)
        }
        val salt = prefs[pinSaltKey]
        val storedHash = prefs[pinHashKey]
        if (salt == null || storedHash == null) {
            return PinCheckResult.Success
        }
        val candidateHash = app.yodo.messenger.core.util.PinHasher.hash(pin, salt)
        return if (candidateHash == storedHash) {
            context.settingsDataStore.edit {
                it[pinFailedAttemptsKey] = 0
                it.remove(pinLockedUntilKey)
            }
            PinCheckResult.Success
        } else {
            val failedAttempts = (prefs[pinFailedAttemptsKey] ?: 0) + 1
            if (failedAttempts >= MAX_PIN_ATTEMPTS) {
                val unlockAt = now + PIN_LOCKOUT_MS
                context.settingsDataStore.edit {
                    it[pinFailedAttemptsKey] = 0
                    it[pinLockedUntilKey] = unlockAt
                }
                PinCheckResult.LockedOut(unlockAt)
            } else {
                context.settingsDataStore.edit { it[pinFailedAttemptsKey] = failedAttempts }
                PinCheckResult.WrongPin(MAX_PIN_ATTEMPTS - failedAttempts)
            }
        }
    }

    // НОВОЕ (п.18): автоудаление аккаунта
    suspend fun setAutoDeleteEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[autoDeleteEnabledKey] = enabled }
    }
    suspend fun setAutoDeleteDays(days: Int) {
        context.settingsDataStore.edit { it[autoDeleteDaysKey] = days }
    }
    suspend fun updateLastActiveTimestamp() {
        context.settingsDataStore.edit { it[lastActiveTimestampKey] = System.currentTimeMillis() }
    }

    // НОВОЕ (п.13): фон чата
    suspend fun setChatBackgroundType(type: ChatBackgroundType) {
        context.settingsDataStore.edit { it[chatBackgroundTypeKey] = type.name }
    }
    suspend fun setChatBackgroundCustomPath(path: String) {
        context.settingsDataStore.edit { it[chatBackgroundCustomPathKey] = path }
    }

    // НОВОЕ (п.4): папки чатов
    suspend fun setChatFolders(folders: List<ChatFolder>) {
        context.settingsDataStore.edit { it[chatFoldersJsonKey] = Json.encodeToString(folders) }
    }
    suspend fun addChatFolder(folder: ChatFolder) {
        val current = chatFolders.first().toMutableList()
        current.add(folder)
        setChatFolders(current)
    }
    suspend fun updateChatFolder(folder: ChatFolder) {
        val current = chatFolders.first().toMutableList()
        val index = current.indexOfFirst { it.id == folder.id }
        if (index >= 0) {
            current[index] = folder
            setChatFolders(current)
        }
    }
    suspend fun deleteChatFolder(folderId: String) {
        val current = chatFolders.first().filter { it.id != folderId }
        setChatFolders(current)
    }
    suspend fun addChatToFolder(folderId: String, chatId: String) {
        val current = chatFolders.first().toMutableList()
        val index = current.indexOfFirst { it.id == folderId }
        if (index >= 0) {
            val folder = current[index]
            if (chatId !in folder.chatIds) {
                current[index] = folder.copy(chatIds = folder.chatIds + chatId)
                setChatFolders(current)
            }
        }
    }
    suspend fun removeChatFromFolder(folderId: String, chatId: String) {
        val current = chatFolders.first().toMutableList()
        val index = current.indexOfFirst { it.id == folderId }
        if (index >= 0) {
            val folder = current[index]
            current[index] = folder.copy(chatIds = folder.chatIds - chatId)
            setChatFolders(current)
        }
    }

    private suspend fun <T> Flow<T>.first(): T {
        var result: T? = null
        collect { result = it; return@collect }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}