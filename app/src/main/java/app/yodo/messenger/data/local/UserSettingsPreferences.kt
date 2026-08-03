package app.yodo.messenger.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "yodo_user_settings")

// п.33: 5 положений ползунка вместо 3
enum class FontSize(val scale: Float, val displayName: String) {
    VERY_SMALL(0.85f, "Очень мелкий"),
    SMALL(0.925f, "Мелкий"),
    MEDIUM(1.0f, "Обычный"),
    LARGE(1.1f, "Крупный"),
    VERY_LARGE(1.2f, "Очень крупный")
}

// п.6: когда именно приложение требует PIN-код при входе.
enum class PinRequirement(val displayName: String) {
    NEVER("Никогда"),
    ON_CLOSE("После закрытия приложения"),
    ON_BACKGROUND("После сворачивания приложения")
}

/** Результат проверки PIN: успех, неверный код (с числом оставшихся попыток), или блокировка. */
sealed class PinCheckResult {
    data object Success : PinCheckResult()
    data class WrongPin(val attemptsRemaining: Int) : PinCheckResult()
    data class LockedOut(val unlockAtMillis: Long) : PinCheckResult()
}

private const val MAX_PIN_ATTEMPTS = 5
private const val PIN_LOCKOUT_MS = 30_000L // 30 секунд блокировки после исчерпания попыток

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
    // НОВОЕ (расширенные опросы): включает создание опросов с доп. параметрами
    // (анонимность, множественный выбор, дата авто-закрытия). Один и тот же флаг
    // доступен на экране регистрации и в настройках — значение общее для аккаунта.
    private val advancedPollsEnabledKey = booleanPreferencesKey("advanced_polls_enabled")
    // п.6: PIN хранится не как открытый текст, а как SHA-256 хэш + соль.
    // НОВОЕ: показываем собственный экран запроса разрешения на уведомления
    // только один раз — сразу после первого входа пользователя.
    private val notificationPermissionAskedKey = booleanPreferencesKey("notification_permission_asked")
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val pinSaltKey = stringPreferencesKey("pin_salt")
    private val pinRequirementKey = stringPreferencesKey("pin_requirement")
    // Лимит попыток: после MAX_PIN_ATTEMPTS неверных вводов подряд — блокировка на LOCKOUT_MS.
    private val pinFailedAttemptsKey = androidx.datastore.preferences.core.intPreferencesKey("pin_failed_attempts")
    private val pinLockedUntilKey = androidx.datastore.preferences.core.longPreferencesKey("pin_locked_until")

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
    // НОВОЕ (расширенные опросы): по умолчанию выключено — доступно и на регистрации, и в настройках.
    val advancedPollsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[advancedPollsEnabledKey] ?: false }
    val pinRequirement: Flow<PinRequirement> = context.settingsDataStore.data.map { prefs ->
        prefs[pinRequirementKey]?.let { raw -> runCatching { PinRequirement.valueOf(raw) }.getOrNull() } ?: PinRequirement.NEVER
    }
    val isPinSet: Flow<Boolean> = context.settingsDataStore.data.map { !it[pinHashKey].isNullOrBlank() }

    // НОВОЕ: флаг "уже показывали диалог с запросом разрешения на уведомления".
    val notificationPermissionAsked: Flow<Boolean> =
        context.settingsDataStore.data.map { it[notificationPermissionAskedKey] ?: false }

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
    // НОВОЕ (расширенные опросы): сеттер общий — вызывается и из RegisterScreen/AuthViewModel
    // (сразу после регистрации), и из SettingsViewModel, пишет в один и тот же ключ DataStore.
    suspend fun setAdvancedPollsEnabled(enabled: Boolean) { context.settingsDataStore.edit { it[advancedPollsEnabledKey] = enabled } }

    suspend fun setPinRequirement(requirement: PinRequirement) {
        context.settingsDataStore.edit { it[pinRequirementKey] = requirement.name }
    }

    /** Задаёт новый 4-значный PIN (или меняет существующий). Хранится только хэш + соль. */
    suspend fun setPin(pin: String) {
        val salt = app.yodo.messenger.core.util.PinHasher.generateSalt()
        val hash = app.yodo.messenger.core.util.PinHasher.hash(pin, salt)
        context.settingsDataStore.edit {
            it[pinSaltKey] = salt
            it[pinHashKey] = hash
        }
    }

    /** Полностью отключает PIN (например, при выключении в настройках). */
    suspend fun clearPin() {
        context.settingsDataStore.edit {
            it.remove(pinHashKey)
            it.remove(pinSaltKey)
            it[pinRequirementKey] = PinRequirement.NEVER.name
        }
    }

    /**
     * Сверяет введённый PIN с сохранённым хэшем, учитывая лимит попыток.
     * При исчерпании MAX_PIN_ATTEMPTS подряд — блокировка на PIN_LOCKOUT_MS.
     * Успешный ввод сбрасывает счётчик попыток.
     */
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
            // PIN не установлен — считаем это успехом, чтобы не блокировать вход.
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
}
