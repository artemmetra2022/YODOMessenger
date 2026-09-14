package app.yodo.messenger.features.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactQrUiState(
    val isLoading: Boolean = true,
    val user: YodoUser? = null,
    val notFound: Boolean = false
)

/**
 * НОВОЕ (поделиться контактом абонента): загружает данные собеседника
 * (имя, username, публичный ключ) по его userId, чтобы построить QR-карточку
 * его контакта (а не своего) и поделиться ею.
 */
@HiltViewModel
class ContactQrViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val userId: String = checkNotNull(savedStateHandle["userId"])

    private val _uiState = MutableStateFlow(ContactQrUiState())
    val uiState: StateFlow<ContactQrUiState> = _uiState

    init {
        viewModelScope.launch {
            val user = userRepository.getUserById(userId)
            _uiState.value = if (user == null) {
                ContactQrUiState(isLoading = false, notFound = true)
            } else {
                ContactQrUiState(isLoading = false, user = user)
            }
        }
    }
}
