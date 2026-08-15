package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.TwoFactorState
import app.yodo.messenger.domain.repository.TwoFactorEmailSendResult
import app.yodo.messenger.domain.repository.TwoFactorRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ВАЖНО (безопасность): PBKDF2 hash/salt второго пароля хранятся ОТДЕЛЬНО от
 * публичного профиля пользователя — в приватной подколлекции
 * users/{uid}/security/twoFactor, которая по firestore.rules читается только
 * самим владельцем аккаунта. Раньше hash/salt лежали прямо в users/{uid},
 * который читается публично (allow read: if true — это нужно для входа по
 * username), из-за чего хеш второго пароля любого пользователя можно было
 * скачать без авторизации и брутфорсить офлайн. Поле "enabled"/"hint"
 * секретом не является (нужно для экрана входа, чтобы показать, что 2FA
 * включена, и подсказку) и по-прежнему хранится в публичном профиле —
 * это не даёт злоумышленнику ничего, кроме факта включённости 2FA.
 */
@Singleton
class TwoFactorRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : TwoFactorRepository {

    companion object {
        private const val FIELD = "twoFactor"
        private const val SECURITY_DOC_ID = "twoFactor"
        private const val EMAIL_CODE_DOC_ID = "emailCode"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16

        private const val EMAIL_CODE_TTL_MS = 10 * 60 * 1000L // код действует 10 минут
        private const val EMAIL_CODE_MAX_ATTEMPTS = 5 // не более 5 попыток ввода на один код
        private const val EMAIL_CODE_RESEND_COOLDOWN_MS = 30 * 1000L // не чаще раза в 30 секунд

        // EmailJS (emailjs.com) — бесплатный сервис отправки писем прямо с клиента,
        // без своего сервера/SMTP. Публичный ключ НЕ секретен по дизайну EmailJS
        // (аналог Firebase apiKey — ограничивается доменом/allowlist в панели
        // EmailJS, а не секретностью значения). Приватный Access Token, если он
        // задан в аккаунте EmailJS, тоже можно передавать с клиента — это принятая
        // схема сервиса для serverless-отправки.
        //
        // ЗАПОЛНИТЕ перед сборкой (Settings → API Keys в личном кабинете emailjs.com):
        private const val EMAILJS_SERVICE_ID = "service_qzw3tzg"
        private const val EMAILJS_TEMPLATE_ID = "template_nn2lzzt"
        private const val EMAILJS_PUBLIC_KEY = "ASKDalVpd2AQSK6Jo"
    }

    private val httpClient = OkHttpClient()

    private fun userDoc() =
        firebaseAuth.currentUser?.uid?.let { uid -> firestore.collection("users").document(uid) }

    private fun securityDoc() =
        firebaseAuth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .collection("security").document(SECURITY_DOC_ID)
        }

    private fun emailCodeDoc() =
        firebaseAuth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .collection("security").document(EMAIL_CODE_DOC_ID)
        }

    override fun observeState(): Flow<TwoFactorState> {
        val doc = userDoc() ?: return flowOf(TwoFactorState())
        return callbackFlow {
            val listener = doc.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("TwoFactorRepository", "Ошибка слежения: ${error.message}")
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val map = snapshot?.get(FIELD) as? Map<String, Any?>
                val enabled = (map?.get("enabled") as? Boolean) == true
                val hint = map?.get("hint") as? String
                trySend(TwoFactorState(enabled = enabled, hint = hint?.takeIf { it.isNotBlank() }))
            }
            awaitClose { listener.remove() }
        }
    }

    override suspend fun isEnabled(): Boolean {
        val doc = userDoc() ?: return false
        return try {
            val snap = doc.get().await()
            @Suppress("UNCHECKED_CAST")
            val map = snap.get(FIELD) as? Map<String, Any?>
            (map?.get("enabled") as? Boolean) == true
        } catch (e: Exception) {
            android.util.Log.w("TwoFactorRepository", "Не удалось проверить состояние: ${e.message}")
            false
        }
    }

    override suspend fun setPassword(newPassword: String, hint: String?): Boolean {
        val doc = userDoc() ?: return false
        val secDoc = securityDoc() ?: return false
        return try {
            val salt = generateSalt()
            val hash = hashPassword(newPassword, salt)

            // Секретные данные (hash/salt) — в приватную подколлекцию.
            secDoc.set(
                mapOf(
                    "salt" to salt.toHex(),
                    "hash" to hash,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()

            // Публично видимые данные (только флаг и необязательная подсказка) —
            // в профиль пользователя, как и раньше.
            doc.set(
                mapOf(
                    FIELD to mapOf(
                        "enabled" to true,
                        "hint" to (hint?.trim().takeUnless { it.isNullOrBlank() } ?: ""),
                        "updatedAt" to System.currentTimeMillis()
                    )
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("TwoFactorRepository", "Не удалось установить пароль: ${e.message}")
            false
        }
    }

    override suspend fun verifyPassword(password: String): Boolean {
        val secDoc = securityDoc() ?: return false
        return try {
            val snap = secDoc.get().await()
            val saltHex = snap.getString("salt") ?: return false
            val storedHash = snap.getString("hash") ?: return false
            val computedHash = hashPassword(password, saltHex.fromHex())
            computedHash == storedHash
        } catch (e: Exception) {
            android.util.Log.w("TwoFactorRepository", "Не удалось проверить пароль: ${e.message}")
            false
        }
    }

    override suspend fun disable(currentPassword: String): Boolean {
        val doc = userDoc() ?: return false
        val secDoc = securityDoc() ?: return false
        if (!verifyPassword(currentPassword)) return false
        return try {
            secDoc.set(
                mapOf(
                    "salt" to "",
                    "hash" to "",
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            doc.set(
                mapOf(
                    FIELD to mapOf(
                        "enabled" to false,
                        "hint" to "",
                        "updatedAt" to System.currentTimeMillis()
                    )
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("TwoFactorRepository", "Не удалось отключить пароль: ${e.message}")
            false
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Второй шаг 2FA: 6-значный код на почту, к которой привязан аккаунт.
    //
    // ВАЖНО (безопасность, чем это отличается от "серверного" варианта):
    // здесь нет Cloud Functions (нужен платный план Blaze), поэтому код
    // генерируется и проверяется НА КЛИЕНТЕ, а письмо отправляется через
    // сторонний бесплатный сервис EmailJS напрямую с телефона. Сам код
    // никогда не хранится и не передаётся в открытом виде — в Firestore
    // (users/{uid}/security/emailCode, читает/пишет только владелец
    // аккаунта — см. firestore.rules) лежит только SHA-256 хэш с солью и
    // срок действия, как и с обычным паролем 2FA выше. Тем не менее это
    // немного менее безопасно, чем полностью серверная проверка: человек с
    // доступом к декомпилированному APK теоретически может увидеть саму
    // логику генерации/сравнения хэша (не сам код — код случаен и хранится
    // только как хэш) и написать себе клиент, который читает произвольный
    // документ security/emailCode ДРУГОГО пользователя. Чтобы это не давало
    // ничего полезного, доступ к чужому security/emailCode по-прежнему
    // закрыт правилами Firestore — прочитать/подобрать хэш чужого кода
    // нельзя, можно только для СВОЕГО аккаунта, что и так уже требует
    // valid Firebase-сессии этого же пользователя.
    // ────────────────────────────────────────────────────────────────────────────

    override suspend fun sendEmailCode(): TwoFactorEmailSendResult {
        val docRef = emailCodeDoc()
            ?: return TwoFactorEmailSendResult.Error("Нет активной сессии")
        val email = firebaseAuth.currentUser?.email
            ?: return TwoFactorEmailSendResult.Error("У аккаунта не указана почта")

        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val sentAt = existing.getLong("sentAt") ?: 0L
                if (System.currentTimeMillis() - sentAt < EMAIL_CODE_RESEND_COOLDOWN_MS) {
                    return TwoFactorEmailSendResult.Error(
                        "Код уже отправлен, подождите немного перед повторной отправкой"
                    )
                }
            }

            val code = String.format("%06d", SecureRandom().nextInt(1_000_000))
            val salt = generateSalt()
            val hash = hashCode(code, salt)

            docRef.set(
                mapOf(
                    "hash" to hash,
                    "salt" to salt.toHex(),
                    "expiresAt" to (System.currentTimeMillis() + EMAIL_CODE_TTL_MS),
                    "sentAt" to System.currentTimeMillis(),
                    "attempts" to 0
                )
            ).await()

            val sent = sendEmailViaEmailJs(email, code)
            if (!sent) {
                return TwoFactorEmailSendResult.Error("Не удалось отправить письмо, попробуйте ещё раз")
            }

            TwoFactorEmailSendResult.Success(maskEmail(email))
        } catch (e: Exception) {
            android.util.Log.w("TwoFactorRepository", "Не удалось отправить email-код: ${e.message}")
            TwoFactorEmailSendResult.Error("Не удалось отправить код, попробуйте ещё раз")
        }
    }

    override suspend fun verifyEmailCode(code: String): Boolean {
        val docRef = emailCodeDoc() ?: return false
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) return false

            val expiresAt = snap.getLong("expiresAt") ?: 0L
            if (System.currentTimeMillis() > expiresAt) {
                docRef.delete().await()
                return false
            }

            val attempts = (snap.getLong("attempts") ?: 0L).toInt()
            if (attempts >= EMAIL_CODE_MAX_ATTEMPTS) {
                docRef.delete().await()
                return false
            }

            val saltHex = snap.getString("salt") ?: return false
            val storedHash = snap.getString("hash") ?: return false
            val computedHash = hashCode(code, saltHex.fromHex())

            if (computedHash != storedHash) {
                docRef.update("attempts", attempts + 1).await()
                return false
            }

            // Код верен и одноразов — удаляем, чтобы его нельзя было использовать повторно.
            docRef.delete().await()
            true
        } catch (e: Exception) {
            android.util.Log.w("TwoFactorRepository", "Не удалось проверить email-код: ${e.message}")
            false
        }
    }

    /** Отправляет письмо с кодом через EmailJS REST API (без своего сервера). */
    private suspend fun sendEmailViaEmailJs(toEmail: String, code: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("service_id", EMAILJS_SERVICE_ID)
                    put("template_id", EMAILJS_TEMPLATE_ID)
                    put("user_id", EMAILJS_PUBLIC_KEY)
                    put(
                        "template_params",
                        JSONObject().apply {
                            // Имена переменных подогнаны под встроенный шаблон
                            // EmailJS "One-Time Password": получатель — {{email}},
                            // сам код — {{passcode}}, время истечения — {{time}}.
                            put("email", toEmail)
                            put("passcode", code)
                            // "time" — переменная в готовом шаблоне EmailJS
                            // "One-Time Password" ("valid... till {{time}}").
                            // Показываем реальное время истечения кода (10 минут).
                            put(
                                "time",
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(System.currentTimeMillis() + EMAIL_CODE_TTL_MS))
                            )
                        }
                    )
                }

                val body = payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("https://api.emailjs.com/api/v1.0/email/send")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response -> response.isSuccessful }
            } catch (e: Exception) {
                android.util.Log.w("TwoFactorRepository", "EmailJS: ${e.message}")
                false
            }
        }

    private suspend fun hashCode(code: String, salt: ByteArray): String =
        withContext(Dispatchers.Default) { hashPassword(code, salt) }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return email
        val name = email.substring(0, at)
        val domain = email.substring(at)
        return if (name.length <= 2) {
            "${name.first()}***$domain"
        } else {
            "${name.first()}${"*".repeat((name.length - 2).coerceAtLeast(1))}${name.last()}$domain"
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Криптография: PBKDF2WithHmacSHA256, случайная соль на пользователя.
    // Ни сам пароль, ни обратимая форма никогда не покидают это устройство.
    // ────────────────────────────────────────────────────────────────────────────

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
