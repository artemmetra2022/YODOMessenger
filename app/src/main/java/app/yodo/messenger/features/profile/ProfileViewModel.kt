package app.yodo.messenger.features.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ProfileUpdateResult
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: YodoUser? = null,
    val isUploadingAvatar: Boolean = false,
    val isSavingName: Boolean = false,
    val isSavingBio: Boolean = false,
    val isSavingUsername: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                _uiState.value = _uiState.value.copy(user = user)
            }
        }
    }

    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(isSavingName = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = userRepository.updateDisplayName(name)) {
                is ProfileUpdateResult.Success ->
                    _uiState.value = _uiState.value.copy(isSavingName = false)
                is ProfileUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isSavingName = false, errorMessage = result.message)
            }
        }
    }

    fun updateBio(bio: String) {
        _uiState.value = _uiState.value.copy(isSavingBio = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = userRepository.updateBio(bio)) {
                is ProfileUpdateResult.Success ->
                    _uiState.value = _uiState.value.copy(isSavingBio = false)
                is ProfileUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isSavingBio = false, errorMessage = result.message)
            }
        }
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(isSavingUsername = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = userRepository.updateUsername(username)) {
                is ProfileUpdateResult.Success ->
                    _uiState.value = _uiState.value.copy(isSavingUsername = false)
                is ProfileUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isSavingUsername = false, errorMessage = result.message)
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        _uiState.value = _uiState.value.copy(isUploadingAvatar = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = userRepository.uploadAvatar(uri)) {
                is ProfileUpdateResult.Success ->
                    _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                is ProfileUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isUploadingAvatar = false, errorMessage = result.message)
            }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
