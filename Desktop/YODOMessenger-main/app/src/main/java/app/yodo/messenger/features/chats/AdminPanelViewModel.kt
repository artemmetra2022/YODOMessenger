package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.ChannelUpdateResult
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.model.SupportRestriction
import app.yodo.messenger.domain.repository.SupportConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// NOVOE (chat podderzhki): ViewModel admin-paneli podderzhki.
// Pokazyvaet potok vseh besed podderzhki (tolko dlya adminov, po email).
@HiltViewModel
class AdminPanelViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    val isAdmin: Boolean get() = chatRepository.isSupportAdmin()

    val conversations: StateFlow<List<SupportConversation>> =
        chatRepository.observeSupportConversations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === НОВОЕ (п.19 ТЗ): диалог ограничения возможности писать в поддержку ===

    data class RestrictionDialogState(
        val userId: String,
        val userName: String,
        val isLoading: Boolean = true,
        val current: SupportRestriction? = null,
        val isSubmitting: Boolean = false,
        val error: String? = null
    )

    private val _restrictionDialog = MutableStateFlow<RestrictionDialogState?>(null)
    val restrictionDialog: StateFlow<RestrictionDialogState?> = _restrictionDialog

    /** Открыть диалог ограничения для конкретного обращения (по долгому тапу в списке). */
    fun openRestrictionDialog(userId: String, userName: String) {
        _restrictionDialog.value = RestrictionDialogState(userId = userId, userName = userName)
        viewModelScope.launch {
            val current = chatRepository.getSupportRestriction(userId)
            _restrictionDialog.value = _restrictionDialog.value?.copy(isLoading = false, current = current)
        }
    }

    fun closeRestrictionDialog() {
        _restrictionDialog.value = null
    }

    /** Наложить ограничение. durationMillis == null -> навсегда. */
    fun applyRestriction(reason: String, durationMillis: Long?) {
        val state = _restrictionDialog.value ?: return
        viewModelScope.launch {
            _restrictionDialog.value = state.copy(isSubmitting = true, error = null)
            when (val result = chatRepository.setSupportRestriction(state.userId, reason, durationMillis)) {
                is ChannelUpdateResult.Success -> _restrictionDialog.value = null
                is ChannelUpdateResult.Error -> _restrictionDialog.value =
                    _restrictionDialog.value?.copy(isSubmitting = false, error = result.message)
            }
        }
    }

    /** Снять текущее ограничение. */
    fun removeRestriction() {
        val state = _restrictionDialog.value ?: return
        viewModelScope.launch {
            _restrictionDialog.value = state.copy(isSubmitting = true, error = null)
            when (val result = chatRepository.removeSupportRestriction(state.userId)) {
                is ChannelUpdateResult.Success -> _restrictionDialog.value = null
                is ChannelUpdateResult.Error -> _restrictionDialog.value =
                    _restrictionDialog.value?.copy(isSubmitting = false, error = result.message)
            }
        }
    }
}
