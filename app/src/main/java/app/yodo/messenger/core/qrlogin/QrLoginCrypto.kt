package app.yodo.messenger.core.qrlogin

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * НОВОЕ (вход по QR-коду): криптография для одноразовой передачи учётных данных
 * с телефона (уже авторизован) на веб-версию (ещё нет), через недоверенный канал
 * Firestore — см. комментарий в firestore.rules (match /qrLogins/{sessionId}) для
 * полного описания протокола.
 *
 * Реализует ECIES "вручную" на стандартных примитивах JDK, СОВМЕСТИМО с тем, что
 * делает сайт через WebCrypto SubtleCrypto (см. web/qr-login.js):
 *  - кривая P-256 (secp256r1), публичный ключ — несжатая точка 0x04||X(32)||Y(32)
 *    (именно так WebCrypto отдаёт/принимает ключ в формате "raw");
 *  - HKDF-SHA256 без соли, info = "yodo-qrlogin-v1", 32 байта на AES-ключ;
 *  - AES-256-GCM, 12-байтовый случайный IV, 128-битный тег (в конце шифротекста —
 *    так же, как это делает WebCrypto).
 *
 * Итоговая полезная нагрузка (то, что кладётся в encryptedPayload, в base64):
 *   ephemeralPublicKey(65 байт) || iv(12 байт) || ciphertext+tag
 *
 * Приватный ключ телефона в этой схеме эфемерный (создаётся заново для каждого
 * скана и никогда никуда не отправляется и не сохраняется) — это даёт forward
 * secrecy: даже если позже кто-то получит доступ к документу в Firestore, без
 * приватного ключа САЙТА (который тоже эфемерный и живёт только в памяти вкладки)
 * расшифровать пароль невозможно.
 */
object QrLoginCrypto {

    private const val CURVE_NAME = "secp256r1"
    private const val HKDF_INFO = "yodo-qrlogin-v1"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /**
     * Шифрует [plaintextJson] (в нашем случае — JSON {"email":...,"password":...})
     * под публичным ключом сайта, полученным из QR ([sitePublicKeyRaw], 65 байт,
     * несжатая точка P-256). Возвращает готовую строку для поля encryptedPayload.
     */
    fun encryptForSite(sitePublicKeyRaw: ByteArray, plaintextJson: String): String {
        require(sitePublicKeyRaw.size == 65 && sitePublicKeyRaw[0] == 0x04.toByte()) {
            "Ожидалась несжатая точка P-256 (65 байт, префикс 0x04)"
        }

        val kpg = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE_NAME))
        }
        val ephemeralKeyPair = kpg.generateKeyPair()

        val siteKey = decodeRawPublicKey(sitePublicKeyRaw)

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ephemeralKeyPair.private)
        ka.doPhase(siteKey, true)
        val sharedSecret = ka.generateSecret()

        val aesKey = hkdfSha256(sharedSecret, HKDF_INFO.toByteArray(Charsets.UTF_8), 32)

        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))

        val ephemeralPublicRaw = encodeRawPublicKey(ephemeralKeyPair.public as java.security.interfaces.ECPublicKey)

        val out = ByteArray(ephemeralPublicRaw.size + iv.size + ciphertext.size)
        System.arraycopy(ephemeralPublicRaw, 0, out, 0, ephemeralPublicRaw.size)
        System.arraycopy(iv, 0, out, ephemeralPublicRaw.size, iv.size)
        System.arraycopy(ciphertext, 0, out, ephemeralPublicRaw.size + iv.size, ciphertext.size)

        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** Декодирует публичный ключ сайта, зашитый в QR как base64url(raw P-256 point). */
    fun decodeSitePublicKeyFromQr(base64Url: String): ByteArray =
        Base64.decode(base64Url, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    // ────────────────────────────────────────────────────────────────────────────
    // Вспомогательные функции
    // ────────────────────────────────────────────────────────────────────────────

    private fun decodeRawPublicKey(raw: ByteArray): PublicKey {
        val x = java.math.BigInteger(1, raw.copyOfRange(1, 33))
        val y = java.math.BigInteger(1, raw.copyOfRange(33, 65))
        val params = java.security.AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec(CURVE_NAME))
        }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val point = ECPoint(x, y)
        val spec = ECPublicKeySpec(point, params)
        return KeyFactory.getInstance("EC").generatePublic(spec)
    }

    private fun encodeRawPublicKey(key: java.security.interfaces.ECPublicKey): ByteArray {
        val fieldSize = 32 // P-256 → 32-байтовые координаты
        val x = toFixedLength(key.w.affineX.toByteArray(), fieldSize)
        val y = toFixedLength(key.w.affineY.toByteArray(), fieldSize)
        val out = ByteArray(1 + fieldSize * 2)
        out[0] = 0x04
        System.arraycopy(x, 0, out, 1, fieldSize)
        System.arraycopy(y, 0, out, 1 + fieldSize, fieldSize)
        return out
    }

    /** BigInteger.toByteArray() может добавлять/терять байты знака — нормализуем до fieldSize. */
    private fun toFixedLength(bytes: ByteArray, length: Int): ByteArray {
        return when {
            bytes.size == length -> bytes
            bytes.size > length -> bytes.copyOfRange(bytes.size - length, bytes.size) // срезаем ведущий 0x00
            else -> ByteArray(length - bytes.size) + bytes // дополняем слева нулями
        }
    }

    /** HKDF-SHA256 (RFC 5869) без соли (эквивалент соли из length=0 в WebCrypto deriveBits). */
    private fun hkdfSha256(ikm: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val hashLen = 32
        val salt = ByteArray(hashLen) // соль отсутствует → HKDF использует hashLen нулей
        val prk = hmacSha256(salt, ikm)

        var t = ByteArray(0)
        val okm = ByteArray(outputLength)
        var pos = 0
        var counter = 1
        while (pos < outputLength) {
            val input = t + info + byteArrayOf(counter.toByte())
            t = hmacSha256(prk, input)
            val toCopy = minOf(hashLen, outputLength - pos)
            System.arraycopy(t, 0, okm, pos, toCopy)
            pos += toCopy
            counter++
        }
        return okm
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
