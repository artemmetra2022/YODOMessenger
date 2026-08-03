package app.yodo.messenger.ui.locale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.AppLanguage
import app.yodo.messenger.data.local.LanguagePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Общая ViewModel для переключателя языка — используется как на экране регистрации/приветствия,
 * так и (через SettingsViewModel) в настройках. Значение хранится в LanguagePreferences и
 * действует сразу во всём приложении (см. LocalizedApp в MainActivity).
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languagePreferences: LanguagePreferences
) : ViewModel() {

    val currentLanguage: StateFlow<AppLanguage> = languagePreferences.languageCode
        .map { AppLanguage.fromCode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.SYSTEM)

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { languagePreferences.setLanguage(language) }
    }
}
