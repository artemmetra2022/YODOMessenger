package app.yodo.messenger.core.util

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * п.6: PIN-код для входа в приложение никогда не хранится в открытом виде.
 *
 * ВАЖНО (безопасность): PIN обычно состоит всего из 4-6 цифр (не более 10^6
 * вариантов), поэтому однопроходный SHA-256 без итераций брутфорсится
 * практически мгновенно, если атакующий получит доступ к сохранённым
 * hash+salt (например, из бэкапа или рутованного устройства). Используем
 * PBKDF2WithHmacSHA256 с большим числом итераций — тот же алгоритм, что и
 * для хэширования email-кода в TwoFactorRepositoryImpl, — чтобы подбор PIN
 * требовал заметного времени даже офлайн.
 */
object PinHasher {
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 50_000
    private const val KEY_LENGTH_BITS = 256

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val spec: KeySpec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashed = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hashed, Base64.NO_WRAP)
    }
}
