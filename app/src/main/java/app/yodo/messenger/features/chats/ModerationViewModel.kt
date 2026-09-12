package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.DeletedMessageRecord
import app.yodo.messenger.domain.model.ModerationRule
import app.yodo.messenger.domain.repository.ModerationActionResult
import app.yodo.messenger.domain.repository.ModerationRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val repository: ModerationRepository,
    auth: FirebaseAuth
) : ViewModel() {
    val isAdmin: Boolean = auth.currentUser?.email?.lowercase() in app.yodo.messenger.domain.repository.ChatRepository.ADMIN_EMAILS.map { it.lowercase() }
    val rules = repository.observeRules()
    val history = repository.observeDeletedMessages()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { viewModelScope.launch { repository.cleanupExpiredHistory() } }

    fun saveRule(rule: ModerationRule) = viewModelScope.launch { result(repository.saveRule(rule)) }
    fun deleteRule(id: String) = viewModelScope.launch { result(repository.deleteRule(id)) }
    fun restore(record: DeletedMessageRecord) = viewModelScope.launch { result(repository.restoreDeletedMessage(record)) }
    fun clearError() { _error.value = null }
    private fun result(r: ModerationActionResult) { if (r is ModerationActionResult.Error) _error.value = r.message }
}
