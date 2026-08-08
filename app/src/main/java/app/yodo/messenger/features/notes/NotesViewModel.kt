package app.yodo.messenger.features.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.data.local.PersonalNote
import app.yodo.messenger.data.local.PersonalNotesPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** НОВОЕ: личный блокнот — приватные заметки, только на устройстве. */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val personalNotesPreferences: PersonalNotesPreferences
) : ViewModel() {

    val notes: StateFlow<List<PersonalNote>> = personalNotesPreferences.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(text: String) {
        viewModelScope.launch { personalNotesPreferences.addNote(text) }
    }

    fun removeNote(id: String) {
        viewModelScope.launch { personalNotesPreferences.removeNote(id) }
    }
}
