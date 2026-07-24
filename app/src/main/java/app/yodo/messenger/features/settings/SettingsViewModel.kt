package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.FontSize
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
    private val draftsPreferences: DraftsPreferences,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val presenceRepository: PresenceRepository,
    private val userRepository: UserRepository
) : ViewModel() {

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

    // Текущий пользователь из Firestore — источник правды для переключателей видимости полей
    // расширенного профиля (что именно видят другие люди на экране UserProfileScreen).
    private val currentUser: StateFlow<YodoUser?> =
        userRepository.observeCurrentUser().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Настройки видимости полей расширенного профиля другим пользователям. Раньше в настройках
    // не было ни одного переключателя для этого, хотя сама логика в UserRepository уже
    // существовала (updatePrivacySettings) — просто ничего её не вызывало.
    val showBirthDate: StateFlow<Boolean> = currentUser.map { it?.showBirthDate ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showAboutMe: StateFlow<Boolean> = currentUser.map { it?.showAboutMe ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showLocation: StateFlow<Boolean> = currentUser.map { it?.showLocation ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showWebsite: StateFlow<Boolean> = currentUser.map { it?.showWebsite ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showPhoneNumber: StateFlow<Boolean> = currentUser.map { it?.showPhoneNumber ?: false }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showEmail: StateFlow<Boolean> = currentUser.map { it?.showEmail ?: false }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _accountDeleted = MutableStateFlow(false)
    val accountDeleted: StateFlow<Boolean> = _accountDeleted
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun setDarkTheme(enabled: Boolean) { viewModelScope.launch { themePreferences.setDarkTheme(enabled) } }
    fun setColorTheme(name: String) { viewModelScope.launch { themePreferences.setColorTheme(name) } }
    fun setSendOnEnter(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setSendOnEnter(enabled) } }
    fun setFontSize(size: FontSize) { viewModelScope.launch { userSettingsPreferences.setFontSize(size) } }

    fun setShowOnlineStatus(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsPreferences.setShowOnlineStatus(enabled)
            // Раньше эта настройка была только локальной (DataStore) и никогда не попадала в
            // Firestore, поэтому другие пользователи всё равно видели статус "в сети" — сама
            // настройка ни на что не влияла. Теперь сразу пишем флаг конфиденциальности на
            // сервер и мгновенно скрываем/показываем статус, не дожидаясь следующего
            // сворачивания/разворачивания приложения.
            presenceRepository.setOnlineStatusHidden(hidden = !enabled)
        }
    }

    fun setShowReadReceipts(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setShowReadReceipts(enabled) } }
    fun setAutoDownloadImages(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setAutoDownloadImages(enabled) } }
    fun setHideKeyboardOnSend(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setHideKeyboardOnSend(enabled) } }
    fun setNotificationSound(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setNotificationSound(enabled) } }
    fun setNotificationVibration(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setNotificationVibration(enabled) } }
    fun setMuteAllNotifications(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setMuteAllNotifications(enabled) } }

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
                showBirthDate = showBirthDate,
                showAboutMe = showAboutMe,
                showLocation = showLocation,
                showWebsite = showWebsite,
                showPhoneNumber = showPhoneNumber,
                showEmail = showEmail
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
        if (user == null) { _errorMessage.value = "Вы не авторизованы"; return }
        viewModelScope.launch {
            try {
                user.delete().await()
                _accountDeleted.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Не удалось удалить аккаунт."
            }
        }
    }

    fun consumeError() { _errorMessage.value = null }
}
