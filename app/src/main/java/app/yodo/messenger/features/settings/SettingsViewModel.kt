package app.yodo.messenger.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.core.util.toUserMessage
import app.yodo.messenger.notifications.NotificationHelper
import app.yodo.messenger.data.local.AppLanguage
import app.yodo.messenger.data.local.ChatBackgroundType
import app.yodo.messenger.data.local.DraftsPreferences
import app.yodo.messenger.data.local.FontSize
import app.yodo.messenger.data.local.LanguagePreferences
import app.yodo.messenger.data.local.PinCheckResult
import app.yodo.messenger.data.local.PinRequirement
import app.yodo.messenger.data.local.ThemePreferences
import app.yodo.messenger.data.local.UserSettingsPreferences
import app.yodo.messenger.domain.model.ChatFolder
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.PresenceRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
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
    // НОВОЕ: тихие часы, пауза (snooze) и скрытие текста в уведомлениях.
    val quietHoursEnabled: StateFlow<Boolean> = userSettingsPreferences.quietHoursEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val quietHoursStart: StateFlow<Int> = userSettingsPreferences.quietHoursStart.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22)
    val quietHoursEnd: StateFlow<Int> = userSettingsPreferences.quietHoursEnd.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)
    val hideNotificationPreview: StateFlow<Boolean> = userSettingsPreferences.hideNotificationPreview.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val notificationsSnoozedUntil: StateFlow<Long> = userSettingsPreferences.notificationsSnoozedUntil.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val advancedPollsEnabled: StateFlow<Boolean> = userSettingsPreferences.advancedPollsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    // НОВОЕ (поиск по настройкам): показывать ли настройки в общем поиске на главном экране.
    val showSettingsInGlobalSearch: StateFlow<Boolean> = userSettingsPreferences.showSettingsInGlobalSearch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pinRequirement: StateFlow<PinRequirement> = userSettingsPreferences.pinRequirement.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PinRequirement.NEVER)
    val isPinSet: StateFlow<Boolean> = userSettingsPreferences.isPinSet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // НОВОЕ (скрытые чаты): установлен ли ложный (decoy) PIN.
    val isDecoyPinSet: StateFlow<Boolean> = userSettingsPreferences.isDecoyPinSet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // НОВОЕ: задержка блокировки при сворачивании (в секундах, 0 = сразу).
    val pinLockDelaySeconds: StateFlow<Int> = userSettingsPreferences.pinLockDelaySeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // НОВОЕ (п.18): автоудаление аккаунта
    val autoDeleteEnabled: StateFlow<Boolean> = userSettingsPreferences.autoDeleteEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoDeleteDays: StateFlow<Int> = userSettingsPreferences.autoDeleteDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    // НОВОЕ (п.13): фон чата
    val chatBackgroundType: StateFlow<ChatBackgroundType> = userSettingsPreferences.chatBackgroundType.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatBackgroundType.DEFAULT)
    val chatBackgroundCustomPath: StateFlow<String> = userSettingsPreferences.chatBackgroundCustomPath.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // НОВОЕ (п.4): папки чатов
    val chatFolders: StateFlow<List<ChatFolder>> = userSettingsPreferences.chatFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentUser: StateFlow<YodoUser?> =
        userRepository.observeCurrentUser().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val showBirthDate: StateFlow<Boolean> = currentUser.map { it?.showBirthDate ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showAboutMe: StateFlow<Boolean> = currentUser.map { it?.showAboutMe ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showLocation: StateFlow<Boolean> = currentUser.map { it?.showLocation ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showWebsite: StateFlow<Boolean> = currentUser.map { it?.showWebsite ?: true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showPhoneNumber: StateFlow<Boolean> = currentUser.map { it?.showPhoneNumber ?: false }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showEmail: StateFlow<Boolean> = currentUser.map { it?.showEmail ?: false }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // НОВОЕ (AC/AD): главный админ (одна из 2 почт) — видит раздел «Жалобы».
    val isAppAdmin: Boolean =
        firebaseAuth.currentUser?.email?.lowercase() in app.yodo.messenger.domain.repository.ChatRepository.ADMIN_EMAILS

    private val _accountDeleted = MutableStateFlow(false)
    val accountDeleted: StateFlow<Boolean> = _accountDeleted

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
    // НОВОЕ: тихие часы / пауза / скрытие превью / тестовое уведомление.
    fun setQuietHoursEnabled(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setQuietHoursEnabled(enabled) } }
    fun shiftQuietHoursStart(deltaHours: Int) { viewModelScope.launch { userSettingsPreferences.setQuietHours(quietHoursStart.value + deltaHours, quietHoursEnd.value) } }
    fun shiftQuietHoursEnd(deltaHours: Int) { viewModelScope.launch { userSettingsPreferences.setQuietHours(quietHoursStart.value, quietHoursEnd.value + deltaHours) } }
    fun setHideNotificationPreview(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setHideNotificationPreview(enabled) } }
    fun snoozeNotifications(durationMillis: Long) { viewModelScope.launch { userSettingsPreferences.snoozeNotificationsFor(durationMillis) } }
    fun clearNotificationSnooze() { viewModelScope.launch { userSettingsPreferences.clearNotificationSnooze() } }
    fun sendTestNotification() {
        NotificationHelper.showMessageNotification(
            context = appContext,
            chatId = "test_notification",
            senderName = "Yodo",
            messageText = "Это тестовое уведомление ✅ Уведомления работают."
        )
    }
    fun setAdvancedPollsEnabled(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setAdvancedPollsEnabled(enabled) } }
    // НОВОЕ (поиск по настройкам): переключатель показа настроек в общем поиске.
    fun setShowSettingsInGlobalSearch(enabled: Boolean) { viewModelScope.launch { userSettingsPreferences.setShowSettingsInGlobalSearch(enabled) } }

    fun setPin(pin: String, requirement: PinRequirement = PinRequirement.ON_CLOSE) {
        viewModelScope.launch {
            userSettingsPreferences.setPin(pin)
            userSettingsPreferences.setPinRequirement(requirement)
        }
    }
    fun setPinRequirement(requirement: PinRequirement) { viewModelScope.launch { userSettingsPreferences.setPinRequirement(requirement) } }
    // НОВОЕ: задать задержку блокировки при сворачивании (в секундах, 0 = сразу).
    fun setPinLockDelaySeconds(seconds: Int) { viewModelScope.launch { userSettingsPreferences.setPinLockDelaySeconds(seconds) } }
    fun clearPin() { viewModelScope.launch { userSettingsPreferences.clearPin() } }
    // НОВОЕ (скрытые чаты): управление ложным (decoy) PIN.
    fun setDecoyPin(pin: String) { viewModelScope.launch { userSettingsPreferences.setDecoyPin(pin) } }
    fun clearDecoyPin() { viewModelScope.launch { userSettingsPreferences.clearDecoyPin() } }
    suspend fun verifyPin(pin: String): PinCheckResult = userSettingsPreferences.verifyPin(pin)

    // НОВОЕ (п.18): автоудаление аккаунта
    fun setAutoDeleteEnabled(enabled: Boolean) {
        viewModelScope.launch { userSettingsPreferences.setAutoDeleteEnabled(enabled) }
    }
    fun setAutoDeleteDays(days: Int) {
        viewModelScope.launch { userSettingsPreferences.setAutoDeleteDays(days) }
    }
    fun updateLastActiveTimestamp() {
        viewModelScope.launch { userSettingsPreferences.updateLastActiveTimestamp() }
    }

    // НОВОЕ (п.13): фон чата
    fun setChatBackgroundType(type: ChatBackgroundType) {
        viewModelScope.launch { userSettingsPreferences.setChatBackgroundType(type) }
    }
    fun setChatBackgroundCustomPath(path: String) {
        viewModelScope.launch { userSettingsPreferences.setChatBackgroundCustomPath(path) }
    }

    // НОВОЕ (п.4): папки чатов
    fun addChatFolder(name: String) {
        viewModelScope.launch {
            val folder = ChatFolder(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                order = chatFolders.value.size
            )
            userSettingsPreferences.addChatFolder(folder)
        }
    }
    fun updateChatFolder(folder: ChatFolder) {
        viewModelScope.launch { userSettingsPreferences.updateChatFolder(folder) }
    }
    fun deleteChatFolder(folderId: String) {
        viewModelScope.launch { userSettingsPreferences.deleteChatFolder(folderId) }
    }
    fun addChatToFolder(folderId: String, chatId: String) {
        viewModelScope.launch { userSettingsPreferences.addChatToFolder(folderId, chatId) }
    }
    fun removeChatFromFolder(folderId: String, chatId: String) {
        viewModelScope.launch { userSettingsPreferences.removeChatFromFolder(folderId, chatId) }
    }

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
                _errorMessage.value = e.toUserMessage("Не удалось удалить аккаунт")
            }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
        _errorMessageResId.value = null
    }
}