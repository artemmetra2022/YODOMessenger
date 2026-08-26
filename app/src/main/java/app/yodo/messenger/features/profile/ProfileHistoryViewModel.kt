package app.yodo.messenger.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ProfileHistoryEntry
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (История изменений профиля): загружает журнал изменений профиля.
 */
sealed class ProfileHistoryUiState {
    data object Loading : ProfileHistoryUiState()
    data class Content(val entries: List<ProfileHistoryEntry>) : ProfileHistoryUiState()
    data object Empty : ProfileHistoryUiState()
}

@HiltViewModel
class ProfileHistoryViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileHistoryUiState>(ProfileHistoryUiState.Loading)
    val uiState: StateFlow<ProfileHistoryUiState> = _uiState

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = ProfileHistoryUiState.Loading
            val entries = userRepository.getProfileHistory()
            _uiState.value = if (entries.isEmpty()) ProfileHistoryUiState.Empty
            else ProfileHistoryUiState.Content(entries)
        }
    }
}
