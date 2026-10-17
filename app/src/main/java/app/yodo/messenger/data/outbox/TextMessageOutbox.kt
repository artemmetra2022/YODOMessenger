package app.yodo.messenger.data.outbox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.yodo.messenger.domain.model.Message
import app.yodo.messenger.domain.model.MessageStatus
import app.yodo.messenger.domain.repository.MessageRepository
import app.yodo.messenger.domain.repository.ReplyContext
import app.yodo.messenger.domain.repository.SendMessageResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Durable, encrypted, per-account FIFO. Its lifetime is independent of the chat screen. */
@Singleton
class TextMessageOutbox @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val repository: MessageRepository
) {
    data class Item(val id: String, val owner: String, val chat: String, val text: String,
        val timestamp: Long, val topic: String?, val reply: ReplyContext?, val hasTtl: Boolean,
        val ttl: Long?, val silent: Boolean, val failed: Boolean = false, val error: String? = null) {
        fun message() = Message(id = id, chatId = chat, senderId = owner, topicId = topic,
            text = text, timestamp = timestamp, status = if (failed) MessageStatus.FAILED else MessageStatus.SENDING,
            replyToMessageId = reply?.messageId, replyToSenderName = reply?.senderName, replyToText = reply?.text)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val workers = mutableMapOf<String, Job>()
    private val mutableItems = MutableStateFlow<List<Item>>(emptyList())
    val items = mutableItems.asStateFlow()
    private var owner: String? = null
    private var loaded = false

    init {
        auth.addAuthStateListener { scope.launch {
            mutex.withLock {
                try { loadAccount(); startWorkers() }
                catch (_: Exception) { /* Enqueue reports storage errors without discarding the file. */ }
            }
        } }
    }

    suspend fun enqueue(chat: String, text: String, topic: String?, reply: ReplyContext?,
        hasTtl: Boolean, ttl: Long?, silent: Boolean): Item = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadAccount()
            val uid = auth.currentUser?.uid ?: error("Войдите в аккаунт")
            check(mutableItems.value.size < 200) { "Очередь заполнена. Повторите неотправленные сообщения" }
            val item = Item(UUID.randomUUID().toString(), uid, chat, text, maxOf(System.currentTimeMillis(), (mutableItems.value.lastOrNull()?.timestamp ?: 0L) + 1),
                topic, reply, hasTtl, ttl, silent)
            save(mutableItems.value + item)
            startWorkers()
            item
        }
    }

    suspend fun retry(chat: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadAccount()
            save(mutableItems.value.map { if (it.chat == chat) it.copy(failed = false, error = null) else it })
            startWorkers()
        }
    }

    suspend fun discardFailed(chat: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadAccount()
            save(mutableItems.value.filterNot { it.chat == chat && it.failed })
            startWorkers()
        }
    }

    private fun loadAccount() {
        val uid = auth.currentUser?.uid
        if (loaded && owner == uid) return
        workers.values.forEach { it.cancel() }; workers.clear()
        owner = uid; loaded = false; mutableItems.value = emptyList()
        if (uid != null) {
            val file = storage(uid)
            if (file.baseFile.exists()) {
                val bytes = file.readFully()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(uid), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                val array = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
                mutableItems.value = (0 until array.length()).map { index ->
                    val j = array.getJSONObject(index)
                    val reply = if (j.has("replyId")) ReplyContext(j.getString("replyId"), j.getString("replyName"), j.getString("replyText")) else null
                    Item(j.getString("id"), uid, j.getString("chat"), j.getString("text"), j.getLong("timestamp"),
                        if (j.has("topic")) j.getString("topic") else null, reply, j.getBoolean("hasTtl"),
                        if (j.has("ttl")) j.getLong("ttl") else null, j.getBoolean("silent"), j.optBoolean("failed"),
                        if (j.has("error")) j.getString("error") else null)
                }
            }
        }
        loaded = true
    }

    private fun save(next: List<Item>) {
        val uid = owner ?: error("Войдите в аккаунт")
        check(auth.currentUser?.uid == uid) { "Аккаунт изменился" }
        val array = JSONArray()
        next.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("chat", item.chat); put("text", item.text); put("timestamp", item.timestamp)
                put("topic", item.topic); put("hasTtl", item.hasTtl); put("ttl", item.ttl); put("silent", item.silent)
                put("failed", item.failed); put("error", item.error)
                item.reply?.let { put("replyId", it.messageId); put("replyName", it.senderName); put("replyText", it.text) }
            })
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(uid))
        val encrypted = cipher.iv + cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val file = storage(uid)
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        mutableItems.value = next
    }

    // Called only under mutex. One chat cannot reorder or block a different chat.
    private fun startWorkers() {
        mutableItems.value.map { it.chat }.distinct().forEach { chat ->
            if (workers[chat]?.isActive == true || mutableItems.value.first { it.chat == chat }.failed) return@forEach
            val uid = owner ?: return@forEach
            workers[chat] = scope.launch {
                val worker = currentCoroutineContext()[Job]
                try { while (isActive) {
                    val item = mutex.withLock {
                        if (auth.currentUser?.uid != uid || owner != uid) null
                        else mutableItems.value.firstOrNull { it.chat == chat }?.takeUnless { it.failed }
                    } ?: break
                    val result = try {
                        repository.sendMessage(item.chat, item.text, item.reply, item.hasTtl, item.ttl,
                            item.silent, item.topic, item.id)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { SendMessageResult.Error(e.message ?: "Не удалось отправить") }
                    try {
                        mutex.withLock {
                            if (owner != uid || auth.currentUser?.uid != uid) return@withLock
                            save(when (result) {
                                is SendMessageResult.Success -> mutableItems.value.filterNot { it.id == item.id }
                                is SendMessageResult.Error -> mutableItems.value.map { if (it.id == item.id) it.copy(failed = true, error = result.message) else it }
                            })
                        }
                    } catch (_: Exception) {
                        // Keep the original durable request. Stable server ID makes a restart safe.
                        mutex.withLock {
                            if (owner == uid) mutableItems.value = mutableItems.value.map {
                                if (it.id == item.id) it.copy(failed = true, error = "Не удалось сохранить очередь. Перезапустите приложение") else it
                            }
                        }
                        break
                    }
                    if (result is SendMessageResult.Error) break
                    delay(1_250L) // Throttle without dropping text.
                } } finally {
                    withContext(NonCancellable) { mutex.withLock {
                        if (workers[chat] === worker) {
                            workers.remove(chat)
                            if (owner == uid && auth.currentUser?.uid == uid) startWorkers()
                        }
                    } }
                }
            }
        }
    }

    private fun digest(uid: String) = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun storage(uid: String) = AtomicFile(File(context.noBackupFilesDir, "outbox-${digest(uid)}.enc"))
    private fun key(uid: String): SecretKey {
        val alias = "yodo_text_outbox_${digest(uid)}"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
