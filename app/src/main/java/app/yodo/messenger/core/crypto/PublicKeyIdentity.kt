package app.yodo.messenger.core.crypto

import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import java.io.ByteArrayOutputStream
import java.util.Base64

object PublicKeyIdentity {
    fun handle(encoded: String): KeysetHandle {
        HybridConfig.register()
        return KeysetHandle.readNoSecret(JsonKeysetReader.withBytes(Base64.getDecoder().decode(encoded)))
    }
    fun fingerprint(encoded: String): String {
        val handle = handle(encoded)
        handle.getPrimitive(HybridEncrypt::class.java)
        val output = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(output))
        return keyFingerprint(output.toByteArray())
    }
}
