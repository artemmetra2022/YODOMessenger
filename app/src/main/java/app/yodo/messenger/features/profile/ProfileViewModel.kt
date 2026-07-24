package app.yodo.messenger.features.profile

import android.graphics.Bitmap
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
    val isSavingAboutMe: Boolean = false,
    val isSavingBirthDate: Boolean = false,
    val isSavingLocation: Boolean = false,
    val isSavingWebsite: Boolean = false,
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
            when (val r = userRepository.updateDisplayName(name)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingName = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingName = false, errorMessage = r.message)
            }
        }
    }

    fun updateBio(bio: String) {
        _uiState.value = _uiState.value.copy(isSavingBio = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateBio(bio)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingBio = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingBio = false, errorMessage = r.message)
            }
        }
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(isSavingUsername = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateUsername(username)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingUsername = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingUsername = false, errorMessage = r.message)
            }
        }
    }

    fun updateAboutMe(aboutMe: String) {
        _uiState.value = _uiState.value.copy(isSavingAboutMe = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateAboutMe(aboutMe)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingAboutMe = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingAboutMe = false, errorMessage = r.message)
            }
        }
    }

    fun updateBirthDate(birthDate: String) {
        _uiState.value = _uiState.value.copy(isSavingBirthDate = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateBirthDate(birthDate)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingBirthDate = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingBirthDate = false, errorMessage = r.message)
            }
        }
    }

    fun updateLocation(location: String) {
        _uiState.value = _uiState.value.copy(isSavingLocation = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateLocation(location)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingLocation = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingLocation = false, errorMessage = r.message)
            }
        }
    }

    fun updateWebsite(website: String) {
        _uiState.value = _uiState.value.copy(isSavingWebsite = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.updateWebsite(website)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isSavingWebsite = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isSavingWebsite = false, errorMessage = r.message)
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        _uiState.value = _uiState.value.copy(isUploadingAvatar = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.uploadAvatar(uri)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isUploadingAvatar = false, errorMessage = r.message)
            }
        }
    }

    // Загрузка аватара после экрана перемещения/масштабирования (кропа)
    fun uploadAvatar(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(isUploadingAvatar = true, errorMessage = null)
        viewModelScope.launch {
            when (val r = userRepository.uploadAvatar(bitmap)) {
                is ProfileUpdateResult.Success -> _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                is ProfileUpdateResult.Error -> _uiState.value = _uiState.value.copy(isUploadingAvatar = false, errorMessage = r.message)
            }
        }
    }

    fun consumeError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
