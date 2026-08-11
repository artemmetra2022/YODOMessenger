package app.yodo.messenger.features.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.PinCheckResult
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.UserRepository
import app.yodo.messenger.util.EmojiOnlyValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Batch 7: единый ViewModel для «Центра безопасности» и локального 2FA-шлюза входа.
 * Всё хранится локально в UserSettingsPreferences (DataStore), пароли — только в виде
 * PBKDF2-хэша (см. PinHasher). Ничего не отправляется на сервер.
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val prefs: UserSettingsPreferences,
    private val userRepository: UserRepository
) : ViewModel() {

    val isTwoFactorSet: Flow<Boolean> = prefs.isTwoFactorSet
    val twoFactorHint: Flow<String> = prefs.twoFactorHint
    val recoveryQuestions: Flow<List<String>> = prefs.recoveryQuestions
    val isRecoverySet: Flow<Boolean> = prefs.isRecoverySet
    val screenshotProtection: Flow<Boolean> = prefs.screenshotProtection
    val emojiStatus: Flow<String> = prefs.emojiStatus
    val customStatus: Flow<String> = prefs.customStatus

    fun enableTwoFactor(password: String, hint: String?) {
        viewModelScope.launch { prefs.setTwoFactorPassword(password, hint) }
    }

    fun disableTwoFactor() {
        viewModelScope.launch { prefs.clearTwoFactor() }
    }

    suspend fun verifyTwoFactor(password: String): PinCheckResult = prefs.verifyTwoFactor(password)

    fun setRecoveryQuestions(questions: List<String>, answers: List<String>) {
        viewModelScope.launch { prefs.setRecoveryQuestions(questions, answers) }
    }

    suspend fun resetTwoFactorWithAnswers(answers: List<String>, newPassword: String): Boolean =
        prefs.resetTwoFactorWithAnswers(answers, newPassword)

    fun setScreenshotProtection(enabled: Boolean) {
        viewModelScope.launch { prefs.setScreenshotProtection(enabled) }
    }

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
