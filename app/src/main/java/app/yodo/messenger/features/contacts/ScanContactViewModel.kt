package app.yodo.messenger.features.contacts

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.core.crypto.CryptoManager
import app.yodo.messenger.data.local.SavedContactsPreferences
import app.yodo.messenger.domain.model.OfflineContact
import app.yodo.messenger.domain.repository.ChatRepository
import app.yodo.messenger.domain.repository.CreateChatResult
import app.yodo.messenger.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

// НОВОЕ (офлайн обмен контактами по QR): состояние экрана сканирования.
sealed class ScanState {
    data object Scanning : ScanState()
    data class Success(val contact: OfflineContact, val savedOffline: Boolean) : ScanState()
    data class Error(val message: String) : ScanState()
}

@HiltViewModel
class ScanContactViewModel @Inject constructor(
    private val savedContacts: SavedContactsPreferences,
    private val cryptoManager: CryptoManager,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state: StateFlow<ScanState> = _state

    private val _openedChatId = MutableStateFlow<String?>(null)
    val openedChatId: StateFlow<String?> = _openedChatId

    private val _isOpeningChat = MutableStateFlow(false)
    val isOpeningChat: StateFlow<Boolean> = _isOpeningChat

    // Защита от повторной обработки одного и того же кадра (анализатор шлёт много кадров).
    @Volatile private var handled = false

    fun onQrScanned(raw: String) {
        if (handled) return
        val parsed = parse(raw)
        if (parsed == null) {
            // Не наш QR — не блокируем сканирование, просто игнорируем этот кадр.
            return
        }
        handled = true
        viewModelScope.launch {
            var contact = parsed
            val hadKeyOffline = contact.publicKey != null
            // Если QR старого формата (только uid) или без ключа — дополняем данными из сети.
            if (contact.publicKey == null || contact.displayName.isBlank()) {
                val remote = runCatching { userRepository.getUserById(contact.uid) }.getOrNull()
                if (remote != null) {
                    contact = contact.copy(
                        displayName = contact.displayName.ifBlank { remote.displayName },
                        username = contact.username ?: remote.username,
                        publicKey = contact.publicKey ?: remote.publicKey
                    )
                }
            }
            contact.publicKey?.let { cryptoManager.cachePublicKey(contact.uid, it) }
            savedContacts.addContact(contact)
            _state.value = ScanState.Success(contact, savedOffline = hadKeyOffline)
        }
    }

    fun openChat(uid: String) {
        if (_isOpeningChat.value) return
        _isOpeningChat.value = true
        viewModelScope.launch {
            when (val r = chatRepository.createOrGetPrivateChat(uid)) {
                is CreateChatResult.Success -> _openedChatId.value = r.chatId
                is CreateChatResult.Error -> _state.value = ScanState.Error(r.message)
            }
            _isOpeningChat.value = false
        }
    }

    fun consumeOpenedChat() { _openedChatId.value = null }

    /** Продолжить сканирование после показа результата/ошибки. */
    fun resumeScanning() {
        handled = false
        _state.value = ScanState.Scanning
    }

    private fun parse(raw: String): OfflineContact? {
        return try {
            when {
                raw.startsWith("yodo://c/") -> {
                    val b64 = raw.removePrefix("yodo://c/")
                    val json = String(
                        Base64.decode(b64, Base64.NO_WRAP or Base64.URL_SAFE),
                        Charsets.UTF_8
                    )
                    val o = JSONObject(json)
                    val uid = o.optString("uid").takeIf { it.isNotBlank() } ?: return null
                    OfflineContact(
                        uid = uid,
                        displayName = o.optString("n"),
                        username = o.optString("u").takeIf { it.isNotBlank() },
                        publicKey = o.optString("pk").takeIf { it.isNotBlank() }
                    )
                }
                // Обратная совместимость со старым форматом QR (только ссылка на профиль).
                raw.startsWith("yodo://user/") -> {
                    val uid = raw.removePrefix("yodo://user/").takeIf { it.isNotBlank() } ?: return null
                    OfflineContact(uid = uid, displayName = "")
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
