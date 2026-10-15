package app.yodo.messenger.features.canvas

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Editable boards never leave this account/device. Keystore keys are not backed up. */
data class CanvasRecent(val id: String, val title: String)

@Singleton
class CanvasStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Any()
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun dir(owner: String, chat: String): File {
        require(owner.isNotBlank() && chat.isNotBlank())
        return File(context.filesDir, "canvas_boards/${hash(owner)}/${hash(chat)}")
    }
    private fun file(owner: String, chat: String, id: String): File {
        require(CanvasLimits.validId(id)); return File(dir(owner, chat), "$id.enc")
    }
    private fun key(owner: String): SecretKey {
        val alias = "yodo_canvas_${hash(owner)}"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun loadLocked(owner: String, chat: String, id: String): CanvasDocument {
        val atomic = AtomicFile(file(owner, chat, id))
        val bytes = atomic.openRead().use { val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192); while(true){ val n=it.read(buffer); if(n<0)break; require(output.size()+n<=CanvasLimits.MAX_JSON_BYTES+32); output.write(buffer,0,n) }; output.toByteArray() }
        require(bytes.size in 33..(CanvasLimits.MAX_JSON_BYTES + 32))
        require(bytes.copyOfRange(0,4).contentEquals(byteArrayOf(89,67,66,49)))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(owner), GCMParameterSpec(128, bytes.copyOfRange(4,16)))
        cipher.updateAAD("$owner/$chat/$id".toByteArray())
        return CanvasCodec.decode(cipher.doFinal(bytes.copyOfRange(16, bytes.size)).toString(Charsets.UTF_8)).also { require(it.id == id) }
    }
    suspend fun load(owner: String, chat: String, id: String): CanvasDocument? = withContext(Dispatchers.IO) {
        synchronized(lock) { if (!file(owner,chat,id).exists() && !File(file(owner,chat,id).path+".bak").exists()) null else loadLocked(owner,chat,id) }
    }
    suspend fun recent(owner: String, chat: String): List<CanvasRecent> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            dir(owner,chat).listFiles()?.filter { it.name.endsWith(".enc") }?.sortedByDescending { it.lastModified() }?.take(20)
                ?.mapNotNull { runCatching { loadLocked(owner,chat,it.name.removeSuffix(".enc")).let { doc -> CanvasRecent(doc.id, doc.title) } }.getOrNull() } ?: emptyList()
        }
    }
    suspend fun save(owner: String, chat: String, doc: CanvasDocument) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val plain = CanvasCodec.encode(doc).toByteArray(); require(plain.size <= CanvasLimits.MAX_JSON_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key(owner))
            require(cipher.iv.size == 12); cipher.updateAAD("$owner/$chat/${doc.id}".toByteArray())
            val bytes = byteArrayOf(89,67,66,49) + cipher.iv + cipher.doFinal(plain)
            val target = file(owner,chat,doc.id); target.parentFile!!.mkdirs()
            val atomic = AtomicFile(target); val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) } catch (e: Exception) { atomic.failWrite(stream); throw e }
        }
    }
}
