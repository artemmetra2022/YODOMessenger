package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean> = themePreferences.isDarkTheme.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val sendOnEnter: StateFlow<Boolean> = userSettingsPreferences.sendOnEnter.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val fontSize: StateFlow<FontSize> = userSettingsPreferences.fontSize.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = FontSize.MEDIUM
    )
    val showOnlineStatus: StateFlow<Boolean> = userSettingsPreferences.showOnlineStatus.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val showReadReceipts: StateFlow<Boolean> = userSettingsPreferences.showReadReceipts.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val autoDownloadImages: StateFlow<Boolean> = userSettingsPreferences.autoDownloadImages.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val notificationSound: StateFlow<Boolean> = userSettingsPreferences.notificationSound.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val notificationVibration: StateFlow<Boolean> = userSettingsPreferences.notificationVibration.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true
    )
    val muteAllNotifications: StateFlow<Boolean> = userSettingsPreferences.muteAllNotifications.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false
    )

    private val _accountDeleted = MutableStateFlow(false)
    val accountDeleted: StateFlow<Boolean> = _accountDeleted

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { themePreferences.setDarkTheme(enabled) }
    fun setSendOnEnter(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setSendOnEnter(enabled) }
    fun setFontSize(size: FontSize) = viewModelScope.launch { userSettingsPreferences.setFontSize(size) }
    fun setShowOnlineStatus(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setShowOnlineStatus(enabled) }
    fun setShowReadReceipts(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setShowReadReceipts(enabled) }
    fun setAutoDownloadImages(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setAutoDownloadImages(enabled) }
    fun setNotificationSound(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setNotificationSound(enabled) }
    fun setNotificationVibration(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setNotificationVibration(enabled) }
    fun setMuteAllNotifications(enabled: Boolean) = viewModelScope.launch { userSettingsPreferences.setMuteAllNotifications(enabled) }

    fun logout() {
        authRepository.logout()
    }

    fun clearAllDrafts() = viewModelScope.launch { draftsPreferences.clearAllDrafts() }

    /** Удаляет учётную запись Firebase Auth. Данные в Firestore (сообщения, чаты) намеренно не трогаем —
     *  их удаление затронуло бы и собеседников; это соответствует поведению большинства мессенджеров,
     *  где "удаление аккаунта" не стирает историю переписки у других участников. */
    fun deleteAccount() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            _errorMessage.value = "Вы не авторизованы"
            return
        }
        viewModelScope.launch {
            try {
                user.delete().await()
                _accountDeleted.value = true
            } catch (e: Exception) {
                _errorMessage.value = e.message
                    ?: "Не удалось удалить аккаунт. Возможно, нужно войти заново перед удалением."
            }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
    }
}
