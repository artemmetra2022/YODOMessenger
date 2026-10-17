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
data class CanvasSavedBoard(
    val id: String, val title: String, val storageChatHash: String, val chatId: String?,
    val modifiedAt: Long, val objectCount: Int?, val readable: Boolean, val error: String? = null
)

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
        synchronized(lock) { if (!file(owner,chat,id).exists() && !File(file(owner,chat,id).path+".bak").exists()) null else loadLocked(owner,chat,id).also { rememberChat(owner,chat) } }
    }
    suspend fun recent(owner: String, chat: String): List<CanvasRecent> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if(dir(owner,chat).exists())rememberChat(owner,chat)
            boardFiles(dir(owner,chat)).sortedByDescending { it.lastModified() }.take(20)
                .mapNotNull { runCatching { loadLocked(owner,chat,it.name.removeSuffix(".enc")).let { doc -> CanvasRecent(doc.id, doc.title) } }.getOrNull() }
        }
    }
    suspend fun delete(owner: String, chat: String, id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val target = file(owner, chat, id)
            AtomicFile(target).delete()
            check(!target.exists() && !File(target.path + ".bak").exists() && !File(target.path + ".new").exists()) { "Не удалось удалить доску" }
        }
    }
    private fun saveLocked(owner: String, chat: String, doc: CanvasDocument) {
        val plain = CanvasCodec.encode(doc).toByteArray(); require(plain.size <= CanvasLimits.MAX_JSON_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key(owner))
        require(cipher.iv.size == 12); cipher.updateAAD("$owner/$chat/${doc.id}".toByteArray())
        val bytes = byteArrayOf(89,67,66,49) + cipher.iv + cipher.doFinal(plain)
        writeAtomic(file(owner,chat,doc.id),bytes)
        rememberChat(owner,chat)
    }
    suspend fun save(owner: String, chat: String, doc: CanvasDocument) = withContext(Dispatchers.IO) {
        synchronized(lock) { saveLocked(owner,chat,doc) }
    }
    private fun writeAtomic(target: File, bytes: ByteArray) {
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Нет доступа к хранилищу досок" }
        val atomic=AtomicFile(target);val stream=atomic.startWrite()
        try { stream.write(bytes);atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream);throw e }
    }
    /** The old directory name is a one-way hash. Keep the chat ID encrypted, never in a plaintext index. */
    private fun rememberChat(owner: String, chat: String) {
        val target=File(dir(owner,chat),"location.enc")
        if(target.exists()) {
            val valid=runCatching { readChat(owner,hash(chat),target)==chat }.getOrDefault(false)
            if(valid)return
        }
        require(chat.length in 1..1024)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(owner))
        cipher.updateAAD("canvas-location/$owner/${hash(chat)}".toByteArray())
        writeAtomic(target,cipher.iv+cipher.doFinal(chat.toByteArray(Charsets.UTF_8)))
    }
    private fun readChat(owner: String, chatHash: String, target: File): String {
        val bytes=AtomicFile(target).openRead().use { input ->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(1024)
            while(true) { val n=input.read(buffer);if(n<0)break;require(output.size()+n<=8192);output.write(buffer,0,n) }
            output.toByteArray()
        };require(bytes.size in 29..8192)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(owner),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        cipher.updateAAD("canvas-location/$owner/$chatHash".toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8).also { require(hash(it)==chatHash) }
    }
    private fun boardFiles(folder: File): List<File> = folder.listFiles().orEmpty().mapNotNull {
        val name=it.name.removeSuffix(".bak")
        if(name.endsWith(".enc") && CanvasLimits.validId(name.removeSuffix(".enc")))File(folder,name) else null
    }.distinctBy { it.name }
    /** No recent-board limit; include unreadable legacy files so they can still be removed. */
    suspend fun listAll(owner: String, knownChatIds: Collection<String> = emptyList()): List<CanvasSavedBoard> = withContext(Dispatchers.IO) {
        require(owner.isNotBlank())
        synchronized(lock) {
            val known=knownChatIds.associateBy { hash(it) }
            val root=File(context.filesDir,"canvas_boards/${hash(owner)}")
            root.listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("[0-9a-f]{64}")) }.flatMap { folder ->
                val chat=runCatching { readChat(owner,folder.name,File(folder,"location.enc")) }.getOrNull() ?: known[folder.name]
                if(chat!=null)runCatching { rememberChat(owner,chat) }
                boardFiles(folder).map { target ->
                    val id=target.name.removeSuffix(".enc")
                    val modified=maxOf(target.lastModified(),File(target.path+".bak").lastModified())
                    val doc=chat?.let { runCatching { loadLocked(owner,it,id) }.getOrNull() }
                    CanvasSavedBoard(id,doc?.title?:if(chat==null)"Доска из недоступного чата" else "Не удалось прочитать доску",
                        folder.name,chat,modified,doc?.objects?.size,doc!=null,
                        if(doc!=null)null else if(chat==null)"Откройте исходный чат для восстановления доступа к старой доске. Файл можно удалить здесь."
                        else "Файл повреждён или ключ недоступен. Можно удалить его с устройства.")
                }
            }.sortedByDescending { it.modifiedAt }
        }
    }
    suspend fun renameSaved(owner: String, entry: CanvasSavedBoard, title: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val chat=requireNotNull(entry.chatId);require(hash(chat)==entry.storageChatHash)
            val doc=loadLocked(owner,chat,entry.id)
            saveLocked(owner,chat,doc.copy(title=title.trim()))
        }
    }
    suspend fun deleteSaved(owner: String, entry: CanvasSavedBoard) = withContext(Dispatchers.IO) {
        require(owner.isNotBlank() && entry.storageChatHash.matches(Regex("[0-9a-f]{64}")) && CanvasLimits.validId(entry.id))
        synchronized(lock) {
            val target=File(context.filesDir,"canvas_boards/${hash(owner)}/${entry.storageChatHash}/${entry.id}.enc")
            AtomicFile(target).delete()
            check(!target.exists()&&!File(target.path+".bak").exists()&&!File(target.path+".new").exists()) { "Не удалось удалить доску" }
        }
    }
}
