package app.yodo.messenger.features.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Keys include the chat folder: board UUIDs alone need not be unique across chats. */
data class CanvasLibrarySelection(val active: Boolean = false, val keys: Set<String> = emptySet()) {
    fun toggle(key: String) = copy(active=true,keys=if(key in keys)keys-key else keys+key)
    fun toggleAll(available: Set<String>) = copy(active=true,keys=if(available.isNotEmpty()&&keys.containsAll(available))emptySet() else available)
    fun retain(available: Set<String>): CanvasLibrarySelection {
        val remaining=keys.intersect(available)
        return if(keys.isNotEmpty()&&remaining.isEmpty())CanvasLibrarySelection() else copy(keys=remaining)
    }
}

data class CanvasLibraryDeleteResult<T>(val deleted: List<T>, val failed: List<T>, val firstError: String?)

/** Best effort per file, but cancellation/account changes always stop the entire batch. */
suspend fun <T> deleteCanvasLibraryBatch(
    entries: List<T>, verifyAccount: () -> Unit, delete: suspend (T) -> Unit,
    onProgress: (Int, Int) -> Unit
): CanvasLibraryDeleteResult<T> {
    val deleted=mutableListOf<T>();val failed=mutableListOf<T>();var error: String?=null
    entries.forEachIndexed { index,entry ->
        coroutineContext.ensureActive();verifyAccount()
        try { delete(entry);deleted.add(entry) }
        catch(e: Exception) {
            if(e is CancellationException)throw e
            failed.add(entry);if(error==null)error=e.message
        }
        verifyAccount();onProgress(index+1,entries.size)
    }
    return CanvasLibraryDeleteResult(deleted,failed,error)
}
