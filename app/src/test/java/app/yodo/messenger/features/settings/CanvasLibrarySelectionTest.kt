package app.yodo.messenger.features.settings

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CanvasLibrarySelectionTest {
    @Test fun togglingStartsModeAndTogglesOnlyOneBoard() { val s=CanvasLibrarySelection().toggle("chat/a").toggle("chat/b");assertTrue(s.active);assertEquals(setOf("chat/a","chat/b"),s.keys);assertEquals(setOf("chat/b"),s.toggle("chat/a").keys) }
    @Test fun sameIdInDifferentChatsIsDistinct() { assertEquals(2,CanvasLibrarySelection().toggle("chat1/id").toggle("chat2/id").keys.size) }
    @Test fun selectAllIncludesItemsOutsideViewport() {val keys=(1..1000).map{"chat/$it"}.toSet();assertEquals(keys,CanvasLibrarySelection().toggleAll(keys).keys)}
    @Test fun selectAllAgainClearsChecksButKeepsMode() {val s=CanvasLibrarySelection().toggleAll(setOf("a","b")).toggleAll(setOf("a","b"));assertTrue(s.active);assertTrue(s.keys.isEmpty())}
    @Test fun partialSelectionCanSelectAll() {assertEquals(setOf("a","b"),CanvasLibrarySelection().toggle("a").toggleAll(setOf("a","b")).keys)}
    @Test fun deletedEntriesArePrunedAndFailuresStaySelected() {assertEquals(setOf("failed"),CanvasLibrarySelection(true,setOf("deleted","failed")).retain(setOf("failed","unselected")).keys)}
    @Test fun successfulBatchExitsSelectionMode() {assertEquals(CanvasLibrarySelection(),CanvasLibrarySelection(true,setOf("a")).retain(setOf("b")))}
    @Test fun emptyInitialModeStaysOpenToAllowSelecting() {assertTrue(CanvasLibrarySelection(active=true).retain(setOf("a")).active)}
    @Test fun defaultStateClearsSelectionsForNewAccount() {assertTrue(CanvasLibrarySelection().keys.isEmpty());assertFalse(CanvasLibrarySelection().active)}
    @Test fun batchDeletesOnlyRequestedEntriesAndReportsProgress() = runBlocking {
        val removed=mutableListOf<String>();val progress=mutableListOf<Pair<Int,Int>>()
        val r=deleteCanvasLibraryBatch(listOf("a","c"),{},{removed.add(it)},{done,total->progress.add(done to total)})
        assertEquals(listOf("a","c"),removed);assertEquals(listOf(1 to 2,2 to 2),progress);assertEquals(removed,r.deleted);assertTrue(r.failed.isEmpty())
    }
    @Test fun partialFailureDoesNotPretendSuccessOrSkipOtherFiles() = runBlocking {
        val r=deleteCanvasLibraryBatch(listOf("a","bad","c"),{},{if(it=="bad")error("disk error")},{_,_->})
        assertEquals(listOf("a","c"),r.deleted);assertEquals(listOf("bad"),r.failed);assertEquals("disk error",r.firstError)
    }
    @Test fun accountChangeStopsBeforeNextFile() = runBlocking {
        var owner="a";val deleted=mutableListOf<String>()
        try {deleteCanvasLibraryBatch(listOf("one","two"),{check(owner=="a")},{deleted.add(it);owner="b"},{_,_->});fail("Must stop")}
        catch(_: IllegalStateException){}
        assertEquals(listOf("one"),deleted)
    }
    @Test fun cancellationIsNotReportedAsAFileFailure() = runBlocking {
        val deleted=mutableListOf<String>()
        try {deleteCanvasLibraryBatch(listOf("one","two"),{},{if(it=="one")throw CancellationException("cancel");deleted.add(it)},{_,_->});fail("Must cancel")}
        catch(_: CancellationException){}
        assertTrue(deleted.isEmpty())
    }
    @Test fun emptyBatchHasNoSideEffects() = runBlocking {
        val r=deleteCanvasLibraryBatch(emptyList<String>(),{error("No account access")},{error("No deletion")},{_,_->error("No progress")})
        assertTrue(r.deleted.isEmpty());assertTrue(r.failed.isEmpty());assertNull(r.firstError)
    }
}
