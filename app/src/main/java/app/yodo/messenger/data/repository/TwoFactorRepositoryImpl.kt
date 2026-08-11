package app.yodo.messenger.data.repository

import app.yodo.messenger.domain.model.TwoFactorState
import app.yodo.messenger.domain.repository.TwoFactorRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
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
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
    }

    private fun userDoc() =
        firebaseAuth.currentUser?.uid?.let { uid -> firestore.collection("users").document(uid) }

    private fun securityDoc() =
        firebaseAuth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .collection("security").document(SECURITY_DOC_ID)
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
