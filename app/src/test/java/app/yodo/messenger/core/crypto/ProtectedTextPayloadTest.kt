package app.yodo.messenger.core.crypto

import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class ProtectedTextPayloadTest {
    private fun key(): KeysetHandle {
        HybridConfig.register()
        return KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"))
    }
    private fun encodedPublic(handle: KeysetHandle): String {
        val output = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle.publicKeysetHandle, JsonKeysetWriter.withOutputStream(output))
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }
    @Test fun `unicode quoted text and replies round trip`() {
        val payload = ProtectedTextPayload("Привет \"мир\"\n🌍", "Цитата\nс новой строкой", "Имя")
        assertEquals(payload, ProtectedTextPayload.decode(payload.encode(), 2))
    }
    @Test fun `legacy ciphertext remains readable`() {
        assertEquals("old text", ProtectedTextPayload.decode("old text", 1).text)
    }
    @Test(expected = SecureMessagingException::class) fun `unknown version is rejected`() { ProtectedTextPayload.decode("{}", 3) }
    @Test fun `real HPKE ciphertext hides text and quote`() {
        val key = key()
        val payload = ProtectedTextPayload("PRIVATE SECRET", "PRIVATE QUOTE", "PRIVATE NAME")
        val aad = "yodo-e2e-v1".toByteArray()
        val cipher = key.publicKeysetHandle.getPrimitive(HybridEncrypt::class.java).encrypt(payload.encode().toByteArray(), aad)
        assertFalse(String(cipher).contains("PRIVATE SECRET"))
        val decrypted = key.getPrimitive(HybridDecrypt::class.java).decrypt(cipher, aad)
        assertEquals(payload, ProtectedTextPayload.decode(String(decrypted), 2))
    }
    @Test fun `different keys have different fingerprints`() {
        assertNotEquals(PublicKeyIdentity.fingerprint(encodedPublic(key())), PublicKeyIdentity.fingerprint(encodedPublic(key())))
    }
    @Test fun `JSON whitespace does not change key identity`() {
        val encoded = encodedPublic(key())
        val spacedJson = String(Base64.getDecoder().decode(encoded)).replace("{", "{ ").replace(",", ",\n")
        assertEquals(PublicKeyIdentity.fingerprint(encoded), PublicKeyIdentity.fingerprint(Base64.getEncoder().encodeToString(spacedJson.toByteArray())))
    }
    @Test fun `secret keysets are rejected as public keys`() {
        val output = ByteArrayOutputStream()
        CleartextKeysetHandle.write(key(), JsonKeysetWriter.withOutputStream(output))
        val encoded = Base64.getEncoder().encodeToString(output.toByteArray())
        assertTrue(runCatching { PublicKeyIdentity.fingerprint(encoded) }.isFailure)
    }
    @Test fun `malformed public keys are rejected`() {
        assertTrue(runCatching { PublicKeyIdentity.fingerprint("bad key") }.isFailure)
    }
    @Test fun `wrong associated data cannot decrypt`() {
        val key = key()
        val cipher = key.publicKeysetHandle.getPrimitive(HybridEncrypt::class.java).encrypt("secret".toByteArray(), "yodo-e2e-v1".toByteArray())
        assertTrue(runCatching { key.getPrimitive(HybridDecrypt::class.java).decrypt(cipher, "other protocol".toByteArray()) }.isFailure)
    }
}
