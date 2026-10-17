package app.yodo.messenger.features.canvas

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Requires Android Keystore on a device/emulator; never touches the real signed-in account. */
class CanvasLibraryStorageTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    @Test fun catalogueIsCompleteEncryptedAndAccountScoped() = runBlocking {
        val store=CanvasStore(context);val owner="library-test-${UUID.randomUUID()}";val other="library-other-${UUID.randomUUID()}"
        try {
            repeat(24) { store.save(owner,"chat-$it",CanvasDocument(title="Личная $it")) }
            store.save(other,"chat-0",CanvasDocument(title="Другой аккаунт"))
            val all=store.listAll(owner);assertEquals(24,all.size);assertTrue(all.all{it.readable});assertFalse(all.any{it.title=="Другой аккаунт"})
            val entry=all.first();val chatId=requireNotNull(entry.chatId);store.renameSaved(owner,entry,"Новое имя")
            assertEquals("Новое имя",store.load(owner,chatId,entry.id)?.title)
            val index=File(context.filesDir,"canvas_boards/${hash(owner)}/${entry.storageChatHash}/location.enc")
            assertFalse(index.readBytes().toString(Charsets.UTF_8).contains(chatId))
            store.deleteSaved(owner,entry);assertEquals(23,store.listAll(owner).size);assertEquals(1,store.listAll(other).size)
        } finally {
            File(context.filesDir,"canvas_boards/${hash(owner)}").deleteRecursively()
            File(context.filesDir,"canvas_boards/${hash(other)}").deleteRecursively()
            val keyStore=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
            keyStore.deleteEntry("yodo_canvas_${hash(owner)}");keyStore.deleteEntry("yodo_canvas_${hash(other)}")
        }
    }
    @Test fun legacyAndBackupOnlyBoardsRemainManageable() = runBlocking {
        val store=CanvasStore(context);val owner="library-test-${UUID.randomUUID()}";val chat="legacy-chat"
        try {
            val d=CanvasDocument(title="Старая доска");store.save(owner,chat,d)
            val folder=File(context.filesDir,"canvas_boards/${hash(owner)}/${hash(chat)}")
            File(folder,"location.enc").delete()
            val file=File(folder,"${d.id}.enc");assertTrue(file.renameTo(File(file.path+".bak")))
            val unresolved=store.listAll(owner).single();assertFalse(unresolved.readable)
            val resolved=store.listAll(owner,listOf(chat)).single();assertTrue(resolved.readable);assertEquals(d.title,resolved.title)
            store.deleteSaved(owner,resolved);assertFalse(file.exists());assertFalse(File(file.path+".bak").exists());assertTrue(store.listAll(owner).isEmpty())
        } finally {
            File(context.filesDir,"canvas_boards/${hash(owner)}").deleteRecursively()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("yodo_canvas_${hash(owner)}")}
        }
    }
}
