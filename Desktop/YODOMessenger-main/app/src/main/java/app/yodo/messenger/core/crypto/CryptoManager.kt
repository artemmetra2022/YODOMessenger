package app.yodo.messenger.core.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * НОВОЕ (сквозное шифрование, E2EE).
 *
 * Единая точка работы с криптографией на устройстве. Использует Google Tink и
 * гибридное шифрование HPKE (DHKEM X25519 + HKDF-SHA256 + AES-256-GCM):
 *  - у каждого пользователя есть пара ключей (приватный/публичный);
 *  - приватный keyset хранится ЛОКАЛЬНО и зашифрован мастер-ключом из Android Keystore
 *    (AndroidKeysetManager) — он никогда не покидает устройство и недоступен серверу;
 *  - публичный ключ выкладывается в документ пользователя в Firestore и вшивается в QR-код,
 *    чтобы собеседник мог зашифровать сообщение именно для нас.
 *
 * Для личного (1-на-1) чата отправитель шифрует текст ОТДЕЛЬНО под публичный ключ каждого
 * из двух участников (и получателя, и себя — чтобы видеть свои же отправленные сообщения
 * при перезагрузке / с другого устройства). На сервере хранится только шифртекст.
 *
 * Ограничения текущей версии (осознанный MVP, задел на будущее):
 *  - шифруется только текстовое тело сообщения в личных чатах;
 *  - групповые чаты, каналы, медиа, а также цитаты/пересланные сниппеты пока не шифруются;
 *  - нет forward secrecy (нет ратчета, как в Signal) — статический ECDH под публичный ключ.
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {

    private val initMutex = Mutex()
    @Volatile private var localKeysReady = false
    private var privateHandle: KeysetHandle? = null
    private var hybridDecrypt: HybridDecrypt? = null
    private var myPublicKeyCache: String? = null

    // Кэш публичных ключей других пользователей (uid -> base64 публичного keyset).
    private val publicKeyCache = ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "CryptoManager"
        // Associated data — привязываем шифртекст к нашему протоколу/версии.
        private val CONTEXT_INFO = "yodo-e2e-v1".toByteArray(Charsets.UTF_8)
        private const val KEYSET_TEMPLATE =
            "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"
        private const val SHARED_PREF_FILE = "yodo_e2e_prefs"
        private const val KEYSET_NAME = "yodo_e2e_keyset"
        private const val MASTER_KEY_URI = "android-keystore://yodo_e2e_master_key"
    }

    /**
     * Лениво создаёт (при первом запуске) и загружает локальную пару ключей.
     * Может бросить исключение при проблемах с Keystore — вызывающая сторона обрабатывает.
     */
    @Synchronized
    private fun ensureLocalKeys() {
        if (localKeysReady && privateHandle != null) return
        HybridConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, SHARED_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get(KEYSET_TEMPLATE))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        privateHandle = handle
        hybridDecrypt = handle.getPrimitive(HybridDecrypt::class.java)

        val publicHandle = handle.publicKeysetHandle
        val bos = ByteArrayOutputStream()
        CleartextKeysetHandle.write(publicHandle, JsonKeysetWriter.withOutputStream(bos))
        myPublicKeyCache = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        localKeysReady = true
    }

    /** Публичный ключ текущего устройства (base64) или null, если инициализация не удалась. */
    fun myPublicKey(): String? = try {
        ensureLocalKeys()
        myPublicKeyCache
    } catch (e: Exception) {
        Log.w(TAG, "myPublicKey failed", e)
        null
    }

    /**
     * Гарантирует наличие локальных ключей и публикует публичный ключ в документ пользователя
     * в Firestore (идемпотентно — обновляет только если ключ изменился/отсутствует).
     * Безопасно вызывать при каждом старте приложения после авторизации.
     */
    suspend fun ensureInitialized() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        initMutex.withLock {
            try {
                ensureLocalKeys()
                val pk = myPublicKeyCache ?: return
                val existing = runCatching {
                    firestore.collection("users").document(uid).get().await().getString("publicKey")
                }.getOrNull()
                if (existing != pk) {
                    firestore.collection("users").document(uid)
                        .set(mapOf("publicKey" to pk), SetOptions.merge()).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "ensureInitialized failed", e)
            }
        }
    }

    /** Кладём известный публичный ключ в кэш (например, полученный из QR-кода офлайн). */
    fun cachePublicKey(uid: String, publicKey: String) {
        if (publicKey.isNotBlank()) publicKeyCache[uid] = publicKey
    }

    /** Достаёт публичный ключ пользователя из кэша или из Firestore. */
    suspend fun getPublicKey(uid: String): String? {
        publicKeyCache[uid]?.let { return it }
        return try {
            val pk = firestore.collection("users").document(uid).get().await().getString("publicKey")
            if (!pk.isNullOrBlank()) publicKeyCache[uid] = pk
            pk
        } catch (e: Exception) {
            Log.w(TAG, "getPublicKey failed for $uid", e)
            null
        }
    }

    /**
     * Шифрует [plaintext] под публичные ключи ВСЕХ участников.
     * Возвращает карту uid -> base64(шифртекст) или null, если хотя бы у одного участника
     * нет опубликованного ключа (тогда вызывающая сторона отправит обычный текст).
     */
    suspend fun encryptForParticipants(
        participantIds: List<String>,
        plaintext: String
    ): Map<String, String>? {
        return try {
            ensureLocalKeys()
            val result = HashMap<String, String>()
            for (uid in participantIds.distinct()) {
                val pk = getPublicKey(uid) ?: return null
                val ct = encryptWith(pk, plaintext) ?: return null
                result[uid] = ct
            }
            result.ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "encryptForParticipants failed", e)
            null
        }
    }

    private fun encryptWith(publicKeyB64: String, plaintext: String): String? = try {
        val bytes = Base64.decode(publicKeyB64, Base64.NO_WRAP)
        val handle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(bytes))
        val enc = handle.getPrimitive(HybridEncrypt::class.java)
        val ct = enc.encrypt(plaintext.toByteArray(Charsets.UTF_8), CONTEXT_INFO)
        Base64.encodeToString(ct, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.w(TAG, "encryptWith failed", e)
        null
    }

    /** Расшифровывает base64-шифртекст нашим приватным ключом. null при ошибке. */
    fun decrypt(ciphertextB64: String): String? = try {
        ensureLocalKeys()
        val dec = hybridDecrypt ?: return null
        val ct = Base64.decode(ciphertextB64, Base64.NO_WRAP)
        String(dec.decrypt(ct, CONTEXT_INFO), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "decrypt failed", e)
        null
    }
}
