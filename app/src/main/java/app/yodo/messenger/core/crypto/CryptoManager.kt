package app.yodo.messenger.core.crypto

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HPKE text encryption with account-scoped local keys and local TOFU pinning.
 * Fresh server keys are checked before every online send. No plaintext fallback.
 * This is NOT a ratchet: no forward secrecy, no group/media E2EE or multi-device protocol.
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {
    private val initMutex = Mutex()
    private val binding = context.getSharedPreferences("yodo_e2e_binding", Context.MODE_PRIVATE)
    private var activeUid: String? = null
    private var hybridDecrypt: HybridDecrypt? = null
    private var myPublicKeyCache: String? = null
    // QR hints remain separate; an offline scan can never replace a trusted online key.
    private val qrKeyHints = ConcurrentHashMap<String, String>()

    companion object {
        private val CONTEXT_INFO = "yodo-e2e-v1".toByteArray(Charsets.UTF_8)
        private const val KEYSET_TEMPLATE = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"
        private const val LEGACY_PREF = "yodo_e2e_prefs"
        private const val KEYSET_NAME = "yodo_e2e_keyset"
        private const val LEGACY_MASTER = "android-keystore://yodo_e2e_master_key"
    }

    private fun uid(): String = firebaseAuth.currentUser?.uid
        ?: throw SecureMessagingException("Войдите в аккаунт для защищённой переписки")
    private fun accountId(uid: String) = keyFingerprint(uid.toByteArray()).lowercase()
    private fun keyManager(pref: String, master: String) = AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, pref).withKeyTemplate(KeyTemplates.get(KEYSET_TEMPLATE))
        .withMasterKeyUri(master).build().keysetHandle
    private fun publicKey(handle: KeysetHandle): String {
        val output = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle.publicKeysetHandle, JsonKeysetWriter.withOutputStream(output))
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
    private fun publicHandle(encoded: String): KeysetHandle = PublicKeyIdentity.handle(encoded)
    private fun fingerprint(encoded: String): String = PublicKeyIdentity.fingerprint(encoded)

    /** Preserve legacy history only for the account whose published key matches the legacy key. */
    private fun resolveLegacyBinding(currentUid: String, publishedKey: String?) {
        if (binding.getString("legacyOwner", null) == null &&
            context.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE).contains(KEYSET_NAME)) {
            val legacy = publicKey(keyManager(LEGACY_PREF, LEGACY_MASTER))
            if (!publishedKey.isNullOrBlank() && fingerprint(legacy) == fingerprint(publishedKey)) {
                check(binding.edit().putString("legacyOwner", currentUid).commit())
                synchronized(this) {
                    if (activeUid == currentUid) { activeUid = null; hybridDecrypt = null; myPublicKeyCache = null }
                }
            }
        }
        check(binding.edit().putBoolean("resolved_" + accountId(currentUid), true).commit())
    }

    @Synchronized private fun ensureLocalKeys() {
        val currentUid = uid()
        if (activeUid == currentUid && hybridDecrypt != null) return
        val legacyOwner = binding.getString("legacyOwner", null)
        val id = accountId(currentUid)
        if (legacyOwner == null && context.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE).contains(KEYSET_NAME) &&
            !binding.getBoolean("resolved_$id", false)) {
            throw SecureMessagingException("Подключитесь к сети для безопасного переноса ключа этого аккаунта")
        }
        HybridConfig.register()
        val handle = if (legacyOwner == currentUid) keyManager(LEGACY_PREF, LEGACY_MASTER)
            else keyManager("yodo_e2e_keys_$id", "android-keystore://yodo_e2e_master_$id")
        hybridDecrypt = handle.getPrimitive(HybridDecrypt::class.java)
        myPublicKeyCache = publicKey(handle)
        activeUid = currentUid
    }

    @Synchronized fun myPublicKey(): String? = runCatching { ensureLocalKeys(); myPublicKeyCache }.getOrNull()

    @Synchronized private fun localPublicKey(owner: String): String {
        ensureLocalKeys()
        requireSameAccount(owner)
        if (activeUid != owner) throw SecureMessagingException("Ключ относится к другому аккаунту")
        return myPublicKeyCache ?: throw SecureMessagingException("Локальный ключ недоступен")
    }

    suspend fun ensureInitialized() = initMutex.withLock {
        val owner = uid()
        HybridConfig.register()
        val ref = firestore.collection("users").document(owner)
        val published = ref.get(Source.SERVER).await().getString("publicKey")
        resolveLegacyBinding(owner, published)
        ensureLocalKeys()
        val local = localPublicKey(owner)
        if (!published.isNullOrBlank() && fingerprint(published) != fingerprint(local)) {
            throw SecureMessagingException("Ключ аккаунта принадлежит другому устройству. Откройте «Проверка ключей»; замена требует подтверждения")
        }
        if (published.isNullOrBlank()) {
            firestore.runTransaction { txn ->
                val existing = txn.get(ref).getString("publicKey")
                if (!existing.isNullOrBlank() && fingerprint(existing) != fingerprint(local)) {
                    throw SecureMessagingException("Ключ аккаунта изменился. Проверьте ключи перед отправкой")
                }
                requireSameAccount(owner)
                txn.set(ref, mapOf("publicKey" to local), SetOptions.merge())
            }.await()
        }
        requireSameAccount(owner)
    }

    fun cachePublicKey(uid: String, publicKey: String) {
        if (publicKey.isNotBlank()) qrKeyHints[uid] = publicKey
    }
    suspend fun getPublicKey(uid: String): String? = firestore.collection("users").document(uid)
        .get(Source.SERVER).await().getString("publicKey")

    fun observeKeyChanges(peerUid: String): Flow<Unit> = callbackFlow {
        val owner = uid()
        var peerSeen = false
        var ownSeen = false
        var lastPeer: String? = null
        var lastOwn: String? = null
        val peerListener = firestore.collection("users").document(peerUid).addSnapshotListener { doc, error ->
            if (error != null) { trySend(Unit) }
            else {
                val key = doc?.get("publicKey") as? String
                if (!peerSeen || key != lastPeer) { peerSeen = true; lastPeer = key; trySend(Unit) }
            }
        }
        val ownListener = firestore.collection("users").document(owner).addSnapshotListener { doc, error ->
            if (error != null) { trySend(Unit) }
            else {
                val key = doc?.get("publicKey") as? String
                if (!ownSeen || key != lastOwn) { ownSeen = true; lastOwn = key; trySend(Unit) }
            }
        }
        awaitClose { peerListener.remove(); ownListener.remove() }
    }

    private fun trustPrefs(owner: String) = context.getSharedPreferences("yodo_key_trust_" + accountId(owner), Context.MODE_PRIVATE)
    @Synchronized private fun firstUsePin(owner: String, peerUid: String, current: String): String {
        val prefs = trustPrefs(owner)
        val id = accountId(peerUid)
        val pinned = prefs.getString("pin_$id", null)
        if (pinned != null) return pinned
        check(prefs.edit().putString("pin_$id", current).putBoolean("verified_$id", false).commit())
        return current
    }
    private fun requireSameAccount(owner: String) {
        if (uid() != owner) throw SecureMessagingException("Аккаунт изменился. Повторите действие в нужном аккаунте")
    }

    suspend fun inspectConversation(peerUid: String): ConversationSecurity = initMutex.withLock {
        val owner = uid()
        require(peerUid != owner)
        HybridConfig.register()
        val publishedOwn = getPublicKey(owner)
        resolveLegacyBinding(owner, publishedOwn)
        ensureLocalKeys()
        val local = fingerprint(localPublicKey(owner))
        val peerKey = getPublicKey(peerUid)?.takeIf { it.isNotBlank() }
            ?: throw SecureMessagingException("У собеседника ещё нет ключа. Попросите его открыть обновлённое приложение")
        val current = fingerprint(peerKey)
        requireSameAccount(owner)
        val pinned = firstUsePin(owner, peerUid, current)
        val publishedFingerprint = publishedOwn?.takeIf { it.isNotBlank() }?.let { fingerprint(it) }
        ConversationSecurity(local, current, pinned,
            trustPrefs(owner).getBoolean("verified_" + accountId(peerUid), false) && pinned == current,
            publishedFingerprint == local, publishedFingerprint)
    }

    /** Explicit out-of-band verification; refuses a key that changed while the dialog was open. */
    suspend fun verifyPeerKey(peerUid: String, expectedFingerprint: String) = initMutex.withLock {
        val owner = uid()
        val key = getPublicKey(peerUid) ?: throw SecureMessagingException("Ключ собеседника недоступен")
        if (fingerprint(key) != expectedFingerprint) throw SecureMessagingException("Ключ снова изменился. Обновите окно проверки и сравните его заново")
        requireSameAccount(owner)
        val id = accountId(peerUid)
        check(trustPrefs(owner).edit().putString("pin_$id", expectedFingerprint).putBoolean("verified_$id", true).commit())
    }

    /** Never called automatically. This intentionally invalidates this account's old published key. */
    suspend fun activateThisDevice(expectedPublishedFingerprint: String?) = initMutex.withLock {
        val owner = uid()
        ensureLocalKeys()
        val local = localPublicKey(owner)
        val ref = firestore.collection("users").document(owner)
        firestore.runTransaction { txn ->
            val current = txn.get(ref).getString("publicKey")?.takeIf { it.isNotBlank() }?.let { fingerprint(it) }
            if (current != expectedPublishedFingerprint) throw SecureMessagingException("Ключ аккаунта снова изменился. Обновите окно проверки")
            requireSameAccount(owner)
            txn.set(ref, mapOf("publicKey" to local), SetOptions.merge())
        }.await()
        requireSameAccount(owner)
    }

    suspend fun encryptForParticipants(participantIds: List<String>, plaintext: String): Map<String, String> {
        val owner = uid()
        try {
            ensureInitialized()
            val ownPublic = localPublicKey(owner)
            val ids = participantIds.distinct()
            if (ids.size != 2 || owner !in ids) throw SecureMessagingException("Не удалось подтвердить участников личного чата")
            val result = mutableMapOf<String, String>()
            for (recipient in ids) {
                val encoded = if (recipient == owner) ownPublic else getPublicKey(recipient)
                val key = encoded?.takeIf { it.isNotBlank() }
                    ?: throw SecureMessagingException("У собеседника нет ключа. Сообщение не отправлено")
                if (recipient != owner) {
                    val current = fingerprint(key)
                    if (keyTrustDecision(firstUsePin(owner, recipient, current), current) == KeyTrustDecision.CHANGED) {
                        throw SecureMessagingException("Ключ собеседника изменился. Отправка остановлена — откройте «Проверка ключей» и сверьте отпечаток")
                    }
                }
                val encryptor = publicHandle(key).getPrimitive(HybridEncrypt::class.java)
                result[recipient] = Base64.encodeToString(encryptor.encrypt(plaintext.toByteArray(Charsets.UTF_8), CONTEXT_INFO), Base64.NO_WRAP)
            }
            requireSameAccount(owner)
            return result
        } catch (e: CancellationException) { throw e }
        catch (e: SecureMessagingException) { throw e }
        catch (_: Exception) { throw SecureMessagingException("Не удалось подтвердить ключи или зашифровать сообщение. Проверьте сеть; открытый текст не отправлен") }
    }

    @Synchronized fun decrypt(ciphertextB64: String): String? = runCatching {
        ensureLocalKeys()
        String(hybridDecrypt!!.decrypt(Base64.decode(ciphertextB64, Base64.NO_WRAP), CONTEXT_INFO), Charsets.UTF_8)
    }.getOrNull()
}
