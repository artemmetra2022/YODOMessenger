package app.yodo.messenger.features.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.UserSettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ: логика собственного диалога с запросом разрешения на уведомления.
 * Диалог должен появиться один раз — сразу после первого входа пользователя,
 * до того как он попадёт в список чатов. Флаг "уже показывали" хранится в DataStore,
 * поэтому при следующих запусках приложения диалог больше не появляется.
 */
@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    private val userSettingsPreferences: UserSettingsPreferences
) : ViewModel() {

    /** true, если диалог уже когда-либо показывался (сразу после первого входа). */
    val alreadyAsked: StateFlow<Boolean> = userSettingsPreferences.notificationPermissionAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Помечает, что диалог уже был показан (вне зависимости от выбора пользователя). */
    fun markAsAsked() {
        viewModelScope.launch {
            userSettingsPreferences.setNotificationPermissionAsked(true)
        }
    }
}
