package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.SupportConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
}
