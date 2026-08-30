package app.yodo.messenger.features.contacts

import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.domain.model.YodoUser
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Один контакт из телефонной книги
data class PhoneContact(
    val name: String,
    val phoneNumber: String
)

sealed class ContactsUiState {
    data object Loading : ContactsUiState()
    data object NoPermission : ContactsUiState()
    data object Empty : ContactsUiState()
    data class Content(
        // Контакты, у которых есть аккаунт Yodo — их можно открыть/написать
        val registered: List<YodoUser>,
        // Остальные контакты — без аккаунта, просто показываем список
        val notRegistered: List<PhoneContact>
    ) : ContactsUiState()
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val uiState: StateFlow<ContactsUiState> = _uiState

    fun onPermissionDenied() {
        _uiState.value = ContactsUiState.NoPermission
    }

    fun loadContacts() {
        _uiState.value = ContactsUiState.Loading
        viewModelScope.launch {
            val phoneContacts = withContext(Dispatchers.IO) { readDeviceContacts() }
            if (phoneContacts.isEmpty()) {
                _uiState.value = ContactsUiState.Empty
                return@launch
            }

            val registeredUsers = userRepository.getUsersByPhoneNumbers(
                phoneContacts.map { it.phoneNumber }
            )
            val registeredPhones = registeredUsers.mapNotNull { it.phoneNumber }.toSet()
            val notRegistered = phoneContacts.filter { it.phoneNumber !in registeredPhones }

            _uiState.value = ContactsUiState.Content(
                registered = registeredUsers.sortedBy { it.displayName.lowercase() },
                notRegistered = notRegistered.sortedBy { it.name.lowercase() }
            )
        }
    }

    private fun readDeviceContacts(): List<PhoneContact> {
        val result = mutableListOf<PhoneContact>()
        val seenNumbers = mutableSetOf<String>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val rawNumber = if (numberIdx >= 0) it.getString(numberIdx) else null
                    val normalized = normalizePhoneNumber(rawNumber) ?: continue
                    if (name.isNullOrBlank()) continue
                    if (seenNumbers.add(normalized)) {
                        result.add(PhoneContact(name = name, phoneNumber = normalized))
                    }
                }
            }
        } catch (e: Exception) { }
        return result
    }

    // Приводим номер к формату E.164, в котором хранятся номера в Firebase Auth/Firestore.
    // Без кода страны сопоставить с базой надёжно нельзя — предполагаем российский номер,
    // если он начинается с 8 или без кода, т.к. это основной рынок приложения.
    private fun normalizePhoneNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return null
        return when {
            digits.startsWith("+") -> digits
            digits.startsWith("8") && digits.length == 11 -> "+7" + digits.substring(1)
            digits.startsWith("7") && digits.length == 11 -> "+$digits"
            digits.length == 10 -> "+7$digits"
            else -> "+$digits"
        }
    }
}
