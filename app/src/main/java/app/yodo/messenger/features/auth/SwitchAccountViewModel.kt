package app.yodo.messenger.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.AccountStore
import app.yodo.messenger.domain.repository.AuthRepository
import app.yodo.messenger.domain.repository.AuthResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * НОВОЕ (Y): логика экрана «Сменить аккаунт». Показывает сохранённые аккаунты и
 * позволяет переключаться между ними без ручного выхода и входа.
 */
@HiltViewModel
class SwitchAccountViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<AccountStore.SavedAccount>>(emptyList())
    val accounts: StateFlow<List<AccountStore.SavedAccount>> = _accounts

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching

    private val _switched = MutableStateFlow(false)
    val switched: StateFlow<Boolean> = _switched

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val currentEmail: String? get() = firebaseAuth.currentUser?.email

    init { refresh() }

    fun refresh() { _accounts.value = accountStore.getAccounts() }

    fun switchTo(account: AccountStore.SavedAccount) {
        if (account.email.equals(currentEmail, ignoreCase = true)) return
        viewModelScope.launch {
            _switching.value = true
            _error.value = null
            authRepository.logout()
            when (val result = authRepository.login(account.email, account.password)) {
                is AuthResult.Success -> _switched.value = true
                is AuthResult.Error -> _error.value = result.message
            }
            _switching.value = false
        }
    }

    fun removeAccount(email: String) {
        accountStore.removeAccount(email)
        refresh()
    }

    fun clearError() { _error.value = null }
}
