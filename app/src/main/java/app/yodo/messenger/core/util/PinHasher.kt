package app.yodo.messenger.core.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * п.6: PIN-код для входа в приложение никогда не хранится в открытом виде —
 * только SHA-256(pin + salt) вместе со сгенерированной солью. Соль генерируется
 * один раз при установке/смене PIN и хранится рядом с хэшем в DataStore.
 */
object PinHasher {
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        val hashed = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashed, Base64.NO_WRAP)
    }
}
