package app.yodo.messenger.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// НОВОЕ (исключения из «Кто может мне писать»): лёгкий ViewModel поиска
// пользователей для диалога добавления в messagePrivacyExceptions. Тот же
// паттерн debounce, что в SearchViewModel, но без каналов/групп/настроек —
// здесь нужны только люди.
@HiltViewModel
class PrivacyExceptionsPickerViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _results = MutableStateFlow<List<YodoUser>>(emptyList())
    val results: StateFlow<List<YodoUser>> = _results

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _isSearching.value = false
            _results.value = emptyList()
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch {
            delay(350) // debounce — не долбим Firestore на каждое нажатие клавиши
            _results.value = userRepository.searchUsers(query)
            _isSearching.value = false
        }
    }
}
