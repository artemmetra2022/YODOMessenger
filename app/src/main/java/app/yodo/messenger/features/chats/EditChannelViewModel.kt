package app.yodo.messenger.features.chats

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.ChannelProfile
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditChannelUiState(
    val profile: ChannelProfile? = null,
    val owner: YodoUser? = null,
    val admins: List<YodoUser> = emptyList(),
    val isOwner: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isUploadingCover: Boolean = false,
    val isSavingMeta: Boolean = false,
    val isSearching: Boolean = false,
    val adminSearchResults: List<YodoUser> = emptyList(),
    val errorMessage: String? = null
)

/**
 * НОВОЕ (переработка каналов): редактирование канала — название, описание,
 * аватарка и (только владелец) управление администраторами.
 */
@HiltViewModel
class EditChannelViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _uiState = MutableStateFlow(EditChannelUiState())
    val uiState: StateFlow<EditChannelUiState> = _uiState

    private val _didSave = MutableStateFlow(false)
    val didSave: StateFlow<Boolean> = _didSave

    private var searchJob: Job? = null

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            val profile = chatRepository.getChannelProfile(chatId) ?: return@launch
            val myUid = firebaseAuth.currentUser?.uid
            val owner = profile.ownerId?.let { userRepository.getUserById(it) }
            val admins = profile.adminIds.mapNotNull { userRepository.getUserById(it) }
            _uiState.value = _uiState.value.copy(
                profile = profile,
                owner = owner,
                admins = admins,
                isOwner = myUid != null && myUid == profile.ownerId
            )
        }
    }

    fun save(title: String, description: String) {
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Название не может быть пустым")
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.updateChannelInfo(chatId, title, description)) {
                is ChannelUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _didSave.value = true
                }
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }

    fun consumeSaved() { _didSave.value = false }

    /** Аватарка сохраняется сразу после кропа (как в профиле пользователя). */
    fun uploadAvatar(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(isUploadingAvatar = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.uploadChannelAvatar(chatId, bitmap)) {
                is ChannelUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                    reload()
                }
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isUploadingAvatar = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ (F5): сохранение категории и тегов канала.
    fun saveMeta(category: String, tagsRaw: String) {
        _uiState.value = _uiState.value.copy(isSavingMeta = true, errorMessage = null)
        val tags = tagsRaw.split(",", " ", "#").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            when (val result = chatRepository.updateChannelMeta(chatId, category, tags)) {
                is ChannelUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSavingMeta = false)
                    reload()
                }
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isSavingMeta = false, errorMessage = result.message)
            }
        }
    }

    // НОВОЕ (F5): загрузка обложки (баннера) канала — сразу после кропа.
    fun uploadCover(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(isUploadingCover = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = chatRepository.uploadChannelCover(chatId, bitmap)) {
                is ChannelUpdateResult.Success -> {
                    _uiState.value = _uiState.value.copy(isUploadingCover = false)
                    reload()
                }
                is ChannelUpdateResult.Error ->
                    _uiState.value = _uiState.value.copy(isUploadingCover = false, errorMessage = result.message)
            }
        }
    }

    fun searchAdminCandidates(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(adminSearchResults = emptyList(), isSearching = false)
            return
        }
        _uiState.value = _uiState.value.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            delay(350)
            val profile = _uiState.value.profile
            val excluded = (listOfNotNull(profile?.ownerId) + (profile?.adminIds ?: emptyList()) +
                    firebaseAuth.currentUser?.uid).toSet()
            val results = userRepository.searchUsers(query).filter { it.uid !in excluded }
            _uiState.value = _uiState.value.copy(adminSearchResults = results, isSearching = false)
        }
    }

    fun addAdmin(user: YodoUser) {
        viewModelScope.launch {
            chatRepository.addChannelAdmin(chatId, user.uid)
            _uiState.value = _uiState.value.copy(adminSearchResults = emptyList())
            reload()
        }
    }

    fun removeAdmin(uid: String) {
        viewModelScope.launch {
            chatRepository.removeChannelAdmin(chatId, uid)
            reload()
        }
    }
}