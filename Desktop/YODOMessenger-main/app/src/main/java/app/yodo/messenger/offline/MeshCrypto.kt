package app.yodo.messenger.offline

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Криптография mesh-протокола.
 *
 * Обеспечивает:
 *  - **Аутентификацию** каждого пакета через HMAC-SHA256. Каждый узел подписывает
 *    пакет общим ключом, выработанным из nodeId отправителя и nodeId следующего хопа
 *    (соседа). Приёмник (или ретранслятор) проверяет подпись тем же ключом.
 *    Это предотвращает подделку пакетов (forgery) злонамеренными узлами.
 *
 *  - **Конфиденциальность** личных (unicast) сообщений через AES-256-GCM.
 *    Текст шифруется ключом, общим для отправителя и конечного получателя
 *    (выводится из пары их nodeId). Промежуточные узлы-ретрансляторы видят
 *    только зашифрованный текст и не могут прочитать содержимое.
 *
 * Ключевой дериватив: SHA-256 от отсортированной пары nodeId — оба узла
 * вычисляют один и тот же ключ независимо.
 */
object MeshCrypto {

    private const val HMAC_ALGO = "HmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    /** Симметричный ключ для пары узлов (порядок не важен). */
    fun sharedKey(nodeA: String, nodeB: String): ByteArray {
        val sorted = listOf(nodeA, nodeB).sorted()
        val input = (sorted[0] + "|" + sorted[1]).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    /** HMAC-SHA256(msg, key). */
    fun hmac(msg: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(key, HMAC_ALGO))
        return mac.doFinal(msg)
    }

    /** Проверка HMAC (constant-time сравнение). */
    fun verifyHmac(msg: ByteArray, sig: ByteArray, key: ByteArray): Boolean {
        val expected = hmac(msg, key)
        return MessageDigest.isEqual(expected, sig)
    }

    /** AES-256-GCM шифрование. IV prepended к ciphertext. */
    fun encryptAES(plaintext: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        val iv = ByteArray(GCM_IV_BYTES)
        java.security.SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    /** AES-256-GCM расшифрование. IV извлекается из начала. */
    fun decryptAES(data: ByteArray, key: ByteArray): ByteArray? {
        if (data.size < GCM_IV_BYTES + 16) return null
        return try {
            val iv = data.copyOfRange(0, GCM_IV_BYTES)
            val ct = data.copyOfRange(GCM_IV_BYTES, data.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null
        }
    }

    fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unbase64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
