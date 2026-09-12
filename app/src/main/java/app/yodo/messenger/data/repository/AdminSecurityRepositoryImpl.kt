package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.repository.AdminSecurityRepository
import app.yodo.messenger.domain.repository.AdminTotpState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class AdminSecurityRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: android.content.Context
) : AdminSecurityRepository {
    private fun uid() = auth.currentUser?.uid
    private fun doc() = uid()?.let { firestore.collection("users").document(it).collection("security").document("adminTotp") }

    override fun observeState(): Flow<AdminTotpState> = callbackFlow {
        val d = doc()
        if (d == null) { trySend(AdminTotpState()); close(); return@callbackFlow }
        val reg = d.addSnapshotListener { snap, _ ->
            trySend(AdminTotpState(
                enabled = snap?.getBoolean("enabled") ?: false,
                secret = snap?.getString("secret")
            ))
        }
        awaitClose { reg.remove() }
    }

    private suspend fun isAdmin(): Boolean {
        val email = auth.currentUser?.email?.lowercase()
        if (email in listOf("artemmetra2022spb@gmail.com", "artemmelnik2@yandex.ru")) return true
        val u = uid() ?: return false
        val d = firestore.collection("admins").document(u).get().await()
        return d.exists() && d.getBoolean("enabled") != false
    }

    override suspend fun beginSetup(): Result<String> = runCatching {
        if (!isAdmin()) error("Доступ только администраторам")
        val u = uid() ?: error("Нет активной сессии")
        val secret = ByteArray(20).also { SecureRandom().nextBytes(it) }
        val encoded = base32(secret)
        firestore.collection("users").document(u).collection("security").document("adminTotp")
            .set(mapOf("enabled" to false, "secret" to encoded, "updatedAt" to System.currentTimeMillis())).await()
        encoded
    }

    override suspend fun verifyAndEnable(code: String): Result<Unit> = runCatching {
        if (!isAdmin()) error("Доступ только администраторам")
        val d = doc() ?: error("Нет активной сессии")
        val secret = d.get().await().getString("secret") ?: error("Сначала создайте ключ")
        if (!verifyTotp(secret, code)) error("Неверный код")
        d.update(mapOf("enabled" to true, "updatedAt" to System.currentTimeMillis())).await()
    }

    override suspend fun verify(code: String): Boolean {
        if (!isAdmin()) return false
        val d = doc() ?: return false
        val snap = d.get().await()
        val secret = snap.getString("secret") ?: return false
        return snap.getBoolean("enabled") == true && verifyTotp(secret, code)
    }

    override suspend fun disable(code: String): Result<Unit> = runCatching {
        if (!verify(code)) error("Неверный код")
        doc()?.update(mapOf("enabled" to false, "updatedAt" to System.currentTimeMillis()))?.await()
            ?: error("Нет активной сессии")
    }


    private fun base32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out.append(alphabet[(buffer shr bits) and 31])
            }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    private fun base32Decode(value: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (c in value.uppercase()) {
            val v = alphabet.indexOf(c)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun verifyTotp(secret: String, code: String, window: Int = 1): Boolean {
        val normalized = code.trim()
        if (!normalized.matches(Regex("\\d{6}"))) return false
        val key = base32Decode(secret)
        val counter = System.currentTimeMillis() / 1000L / 30L
        return (-window..window).any { delta ->
            totp(key, counter + delta) == normalized
        }
    }

    private fun totp(key: ByteArray, counter: Long): String {
        val data = ByteArray(8)
        for (i in 7 downTo 0) data[7 - i] = (counter ushr (i * 8)).toByte()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }
}
