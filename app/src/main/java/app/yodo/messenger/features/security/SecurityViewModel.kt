package app.yodo.messenger.features.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AppSettingsRepository
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.TwoFactorEmailSendResult
import app.yodo.messenger.domain.repository.TwoFactorRepository
import app.yodo.messenger.domain.repository.UserRepository
import app.yodo.messenger.util.EmojiOnlyValidator
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Batch 7: единый ViewModel для «Центра безопасности».
 * 2FA теперь только по email-коду (см. TwoFactorRepository) — без отдельного
 * второго пароля и без контрольных вопросов для сброса. Screenshot-защита и
 * статусы по-прежнему хранятся локально в UserSettingsPreferences.
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val prefs: UserSettingsPreferences,
    private val userRepository: UserRepository,
    private val twoFactorRepository: TwoFactorRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isTwoFactorSet: Flow<Boolean> = kotlinx.coroutines.flow.map(twoFactorRepository.observeState()) { it.enabled }
    val screenshotProtection: Flow<Boolean> = prefs.screenshotProtection
    val emojiStatus: Flow<String> = prefs.emojiStatus
    val customStatus: Flow<String> = prefs.customStatus

    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode

    // НОВОЕ: видно/меняется только двум доверенным email (см. ChatRepository.ADMIN_EMAILS).
    // Глобально требует (или нет) подтверждение почты при входе для ВСЕХ пользователей —
    // сама проверка уже существует в AuthRepositoryImpl.login, здесь только переключатель.
    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in ChatRepository.ADMIN_EMAILS.map { it.lowercase() }
    val requireEmailVerification: Flow<Boolean> = appSettingsRepository.observeRequireEmailVerification()

    fun setRequireEmailVerification(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setRequireEmailVerification(enabled) }
    }

    fun enableTwoFactor() {
        viewModelScope.launch { twoFactorRepository.enable() }
    }

    /** Первый шаг отключения: запрашиваем код на почту для подтверждения. */
    suspend fun requestDisableCode(): TwoFactorEmailSendResult {
        _isSendingCode.value = true
        val result = twoFactorRepository.sendEmailCode()
        _isSendingCode.value = false
        return result
    }

    /** Второй шаг отключения: проверка кода из письма. */
    suspend fun disableTwoFactor(emailCode: String): Boolean = twoFactorRepository.disable(emailCode)

    fun setScreenshotProtection(enabled: Boolean) {
        viewModelScope.launch { prefs.setScreenshotProtection(enabled) }
    }

    // ──────────────────────────────────────────────────────────
    // Поддержка AppTwoFactorGateScreen (гейт при запуске приложения):
    // единственный шаг — код с почты, без пароля.
    // ──────────────────────────────────────────────────────────

    /** Отправляет (или повторно отправляет) код на почту при входе. */
    suspend fun sendLoginCode(): TwoFactorEmailSendResult = twoFactorRepository.sendEmailCode()

    /** Проверяет введённый код при входе. */
    suspend fun verifyLoginCode(code: String): Boolean = twoFactorRepository.verifyEmailCode(code)

    fun setEmojiStatus(value: String) {
        // Эмодзи-статус — только один смайлик, без произвольного текста.
        val sanitized = EmojiOnlyValidator.sanitize(value)
        viewModelScope.launch {
            prefs.setEmojiStatus(sanitized)
            // Публикуем статус на сервер, чтобы его видели другие пользователи.
            userRepository.updateEmojiStatus(sanitized)
        }
    }

    fun setCustomStatus(value: String) {
        viewModelScope.launch {
            prefs.setCustomStatus(value)
            userRepository.updateCustomStatus(value)
        }
    }
}
