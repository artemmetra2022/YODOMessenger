
package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.data.local.AppLanguage
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.data.local.LanguagePreferences
import app.yodo.messenger.data.local.PinCheckResult
import app.yodo.messenger.data.local.PinRequirement
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val languagePreferences: LanguagePreferences,
    private val draftsPreferences: DraftsPreferences,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val presenceRepository: PresenceRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    val currentLanguage: StateFlow<AppLanguage> = languagePreferences.languageCode
        .map { AppLanguage.fromCode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.SYSTEM)
    val isDarkTheme: StateFlow<Boolean> = themePreferences.isDarkTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val colorThemeName: StateFlow<String> = themePreferences.colorThemeName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "BLUE")
    val sendOnEnter: StateFlow<Boolean> = userSettingsPreferences.sendOnEnter.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val fontSize: StateFlow<FontSize> = userSettingsPreferences.fontSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontSize.MEDIUM)
    val showOnlineStatus: StateFlow<Boolean> = userSettingsPreferences.showOnlineStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showReadReceipts: StateFlow<Boolean> = userSettingsPreferences.showReadReceipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoDownloadImages: StateFlow<Boolean> = userSettingsPreferences.autoDownloadImages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val hideKeyboardOnSend: StateFlow<Boolean> = userSettingsPreferences.hideKeyboardOnSend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notificationSound: StateFlow<Boolean> = userSettingsPreferences.notificationSound.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notificationVibration: StateFlow<Boolean> = userSettingsPreferences.notificationVibration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val muteAllNotifications: StateFlow<Boolean> = userSettingsPreferences.muteAllNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // НОВОЕ (расширенные опросы): тот же флаг, что и на экране регистрации — общее значение.
    val advancedPollsEnabled: StateFlow<Boolean> = userSettingsPreferences.advancedPollsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pinRequirement: StateFlow<PinRequirement> = userSettingsPreferences.pinRequirement.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PinRequirement.NEVER)
    val isPinSet: StateFlow<Boolean> = userSettingsPreferences.isPinSet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val currentUser: StateFlow<YodoUser?> =
        userRepository.observeCurrentUser().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val showBirthDate: StateFlow<Boolean> = currentUser.map { it?.showBirthDate ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showAboutMe: StateFlow<Boolean> = currentUser.map { it?.showAboutMe ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showLocation: StateFlow<Boolean> = currentUser.map { it?.showLocation ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showWebsite: StateFlow<Boolean> = currentUser.map { it?.showWebsite ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showPhoneNumber: StateFlow<Boolean> = currentUser.map { it?.showPhoneNumber ?: false }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showEmail: StateFlow<Boolean> = currentUser.map { it?.showEmail ?: false }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _accountDeleted = MutableStateFlow(false)
    val accountDeleted: StateFlow<Boolean> = _accountDeleted
    // errorMessage хранит либо готовую строку (например, из toUserMessage — сообщения Firebase
    // пока не переведены, см. FirebaseErrorMapper), либо null. Для управляемых нами сообщений
    // ("не авторизован" и т.п.) используем ресурсные строки, резолвим их уже на экране.
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    private val _errorMessageResId = MutableStateFlow<Int?>(null)
    val errorMessageResId: StateFlow<Int?> = _errorMessageResId

    fun setLanguage(language: AppLanguage) { viewModelScope.launch { languagePreferences.setLanguage(language) } }

    fun setDarkTheme(enabled: Boolean) { viewModelScope.launch { themePreferences.setDarkTheme(enabled) } }
    fun setColorTheme(name: String) { viewModelScope.launch { themePreferences.setColorTheme(name) } }
    fun setSendOnEnter(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setSendOnEnter(enabled) } }
    fun setFontSize(size: FontSize) { viewModelScope.launch { userSettingsPreferences.setFontSize(size) } }
    fun setShowOnlineStatus(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsPreferences.setShowOnlineStatus(enabled)
            presenceRepository.setOnlineStatusHidden(hidden = !enabled)
        }
    }
    fun setShowReadReceipts(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setShowReadReceipts(enabled) } }
    fun setAutoDownloadImages(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setAutoDownloadImages(enabled) } }
    fun setHideKeyboardOnSend(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setHideKeyboardOnSend(enabled) } }
    fun setNotificationSound(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setNotificationSound(enabled) } }
    fun setNotificationVibration(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setNotificationVibration(enabled) } }
    fun setMuteAllNotifications(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setMuteAllNotifications(enabled) } }
    // НОВОЕ (расширенные опросы): включает/выключает доп. параметры опросов (общий флаг с регистрацией).
    fun setAdvancedPollsEnabled(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setAdvancedPollsEnabled(enabled) } }

    /** п.6: сохраняет новый PIN и сразу выставляет режим требования (по умолчанию — после закрытия). */
    fun setPin(pin: String, requirement: PinRequirement = PinRequirement.ON_CLOSE) {
        viewModelScope.launch {
            userSettingsPreferences.setPin(pin)
            userSettingsPreferences.setPinRequirement(requirement)
        }
    }
    fun setPinRequirement(requirement: PinRequirement) { viewModelScope.launch { userSettingsPreferences.setPinRequirement(requirement) } }
    fun clearPin() { viewModelScope.launch { userSettingsPreferences.clearPin() } }
    suspend fun verifyPin(pin: String): PinCheckResult = userSettingsPreferences.verifyPin(pin)

    private fun pushPrivacySettings(
        showBirthDate: Boolean = this.showBirthDate.value,
        showAboutMe: Boolean = this.showAboutMe.value,
        showLocation: Boolean = this.showLocation.value,
        showWebsite: Boolean = this.showWebsite.value,
        showPhoneNumber: Boolean = this.showPhoneNumber.value,
        showEmail: Boolean = this.showEmail.value
    ) {
        viewModelScope.launch {
            userRepository.updatePrivacySettings(
                showBirthDate = showBirthDate, showAboutMe = showAboutMe,
                showLocation = showLocation, showWebsite = showWebsite,
                showPhoneNumber = showPhoneNumber, showEmail = showEmail
            )
        }
    }

    fun setShowBirthDate(enabled: Boolean) = pushPrivacySettings(showBirthDate = enabled)
    fun setShowAboutMe(enabled: Boolean) = pushPrivacySettings(showAboutMe = enabled)
    fun setShowLocation(enabled: Boolean) = pushPrivacySettings(showLocation = enabled)
    fun setShowWebsite(enabled: Boolean) = pushPrivacySettings(showWebsite = enabled)
    fun setShowPhoneNumber(enabled: Boolean) = pushPrivacySettings(showPhoneNumber = enabled)
    fun setShowEmail(enabled: Boolean) = pushPrivacySettings(showEmail = enabled)

    fun logout() { authRepository.logout() }
    fun clearAllDrafts() { viewModelScope.launch { draftsPreferences.clearAllDrafts() } }

    fun deleteAccount() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            _errorMessageResId.value = app.yodo.messenger.R.string.settings_not_authorized
            return
        }
        viewModelScope.launch {
            try {
                user.delete().await()
                _accountDeleted.value = true
            } catch (e: Exception) {
                // Сообщения из FirebaseErrorMapper (toUserMessage) пока не локализованы —
                // это общая утилита на всё приложение, её перевод выходит за рамки текущей
                // задачи (переключение языка на регистрации/в настройках).
                _errorMessage.value = e.toUserMessage("Не удалось удалить аккаунт")
            }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
        _errorMessageResId.value = null
    }
}