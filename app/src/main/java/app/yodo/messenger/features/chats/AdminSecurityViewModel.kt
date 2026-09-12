package app.yodo.messenger.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.repository.AdminSecurityRepository
import app.yodo.messenger.domain.repository.AdminTotpState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject

@HiltViewModel
class AdminSecurityViewModel @Inject constructor(
    private val repository: AdminSecurityRepository
) : ViewModel() {
    val state: StateFlow<AdminTotpState> = repository.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminTotpState())

    suspend fun beginSetup() = repository.beginSetup()
    suspend fun enable(code: String) = repository.verifyAndEnable(code)
    suspend fun disable(code: String) = repository.disable(code)
}
