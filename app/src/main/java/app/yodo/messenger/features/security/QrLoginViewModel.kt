package app.yodo.messenger.features.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yodo.messenger.core.qrlogin.QrLoginCrypto
import app.yodo.messenger.data.local.AccountStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * НОВОЕ (вход по QR-коду): состояние и логика экрана сканирования QR с сайта.
 * Формат QR см. в parse() ниже; протокол шифрования — QrLoginCrypto/firestore.rules.
 */
sealed class QrLoginState {
    data object Scanning : QrLoginState()
    // Отсканировали валидный QR, но перед отправкой пароля спрашиваем явное
    // подтверждение пользователя — это единственный шаг, который отличает
    // "кто-то навёл мою камеру на чужой QR" от осознанного входа.
    data class AwaitingConfirmation(val sessionId: String) : QrLoginState()
    data object Approving : QrLoginState()
    data object Success : QrLoginState()
    data class Error(val message: String) : QrLoginState()
}

@HiltViewModel
class QrLoginViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val accountStore: AccountStore
) : ViewModel() {

    private val _state = MutableStateFlow<QrLoginState>(QrLoginState.Scanning)
    val state: StateFlow<QrLoginState> = _state

    @Volatile private var handled = false
    private var pendingSessionId: String? = null
    private var pendingSitePublicKey: ByteArray? = null

    fun onQrScanned(raw: String) {
        if (handled) return
        val parsed = parse(raw) ?: return // не наш QR — игнорируем кадр, продолжаем сканировать
        handled = true
        pendingSessionId = parsed.first
        pendingSitePublicKey = parsed.second
        _state.value = QrLoginState.AwaitingConfirmation(parsed.first)
    }

    /**
     * НОВОЕ (обычные считыватели): сессия пришла deep link'ом (yodo://qrlogin/...),
     * а не через камеру — считаем её сразу отсканированной. Аналог onQrScanned,
     * но без разбора содержимого кадра: ключ уже распарсен на стороне ссылки.
     */
    fun consumeExternalSession(sessionId: String, publicKeyB64url: String) {
        if (handled) return
        val publicKey = try {
            QrLoginCrypto.decodeSitePublicKeyFromQr(publicKeyB64url)
        } catch (e: Exception) {
            _state.value = QrLoginState.Error("Некорректная ссылка входа")
            handled = true
            return
        }
        if (publicKey.size != 65) {
            _state.value = QrLoginState.Error("Некорректная ссылка входа")
            handled = true
            return
        }
        handled = true
        pendingSessionId = sessionId
        pendingSitePublicKey = publicKey
        _state.value = QrLoginState.AwaitingConfirmation(sessionId)
    }

    /** Пользователь подтвердил в диалоге, что это действительно он входит на сайте. */
    fun confirmLogin() {
        val sessionId = pendingSessionId ?: return
        val sitePublicKey = pendingSitePublicKey ?: return
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            _state.value = QrLoginState.Error("Вы не авторизованы")
            return
        }
        val account = accountStore.getAccounts().firstOrNull { it.uid == uid }
        if (account == null) {
            // Пароль хранится локально только если он вводился на этом устройстве
            // (см. AccountStore) — например, вход был через Google, сохранённых
            // данных нет. Без пароля передать вход на сайт нечем.
            _state.value = QrLoginState.Error(
                "Не удалось найти сохранённые данные входа на этом устройстве"
            )
            return
        }

        _state.value = QrLoginState.Approving
        viewModelScope.launch {
            try {
                val payloadJson = JSONObject()
                    .put("email", account.email)
                    .put("password", account.password)
                    .toString()

                val encryptedPayload = withContext(Dispatchers.Default) {
                    QrLoginCrypto.encryptForSite(sitePublicKey, payloadJson)
                }

                firestore.collection("qrLogins").document(sessionId)
                    .update(
                        mapOf(
                            "status" to "approved",
                            "approvedBy" to uid,
                            "encryptedPayload" to encryptedPayload
                        )
                    )
                    .await()

                _state.value = QrLoginState.Success
            } catch (e: Exception) {
                _state.value = QrLoginState.Error(
                    "Не удалось подтвердить вход. QR мог устареть — попробуйте отсканировать заново"
                )
            }
        }
    }

    /** Пользователь отменил в диалоге подтверждения — просто продолжаем сканировать. */
    fun cancelConfirmation() {
        resumeScanning()
    }

    fun resumeScanning() {
        handled = false
        pendingSessionId = null
        pendingSitePublicKey = null
        _state.value = QrLoginState.Scanning
    }

    /**
     * Ожидаемые форматы (см. ниже, НОВОЕ — второй вариант):
     *   1) yodo://qrlogin/{sessionId}?pk={base64url(raw P-256 point)} — deep link,
     *      им QR обменивался раньше и по нему же переходит страница-мост;
     *   2) https://yodomessenger.web.app/qr-login.html?s={sessionId}&pk={...} —
     *      ссылка внутри QR для обычных считывателей/камеры телефона.
     * sessionId и ключ идут именно в URL, а не только в Firestore, чтобы одного
     * скана было достаточно — без дополнительного похода в сеть за метаданными.
     */
    private fun parse(raw: String): Pair<String, ByteArray>? {
        // НОВОЕ (обычные считыватели): QR теперь кодирует https-ссылку на страницу-мост
        // qr-login.html (её понимает любой стандартный считыватель и камера), а сама
        // страница открывает в приложении deep link yodo://qrlogin/... Поддерживаем
        // оба формата — и прямое сканирование кода с веб-версии, и deep link.
        val normalized: String = when {
            raw.startsWith("yodo://qrlogin/") -> raw
            raw.startsWith("https://yodomessenger.web.app/qr-login.html") -> {
                val uri = android.net.Uri.parse(raw)
                val s = uri.getQueryParameter("s").orEmpty()
                val pk = uri.getQueryParameter("pk").orEmpty()
                if (s.isBlank() || pk.isBlank()) return null
                "yodo://qrlogin/$s?pk=$pk"
            }
            else -> return null
        }
        return try {
            val withoutPrefix = normalized.removePrefix("yodo://qrlogin/")
            val parts = withoutPrefix.split("?pk=", limit = 2)
            if (parts.size != 2) return null
            val sessionId = parts[0].takeIf { it.isNotBlank() } ?: return null
            val publicKey = QrLoginCrypto.decodeSitePublicKeyFromQr(parts[1])
            if (publicKey.size != 65) return null
            sessionId to publicKey
        } catch (e: Exception) {
            null
        }
    }
}
