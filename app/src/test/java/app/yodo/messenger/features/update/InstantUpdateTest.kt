package app.yodo.messenger.features.update

import app.yodo.messenger.domain.model.AppUpdate
import app.yodo.messenger.domain.model.UpdateCheckResult
import app.yodo.messenger.domain.repository.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InstantUpdateTest {
    private fun release(code: Int = 11400, apk: String? = null) = AppUpdate("1.14.0",code,"",apk,"https://github.com/release")
    @Test fun noReleaseCannotStart() { assertFalse(canStartInstantDownload(null,11300)) }
    @Test fun installedReleaseCannotStart() { assertFalse(canStartInstantDownload(release(11300),11300)) }
    @Test fun olderReleaseCannotStart() { assertFalse(canStartInstantDownload(release(11200),11300)) }
    @Test fun newReleaseWithoutApkCanStart() { assertTrue(canStartInstantDownload(release(),11300)) }
    @Test fun emptyApkCanStart() { assertTrue(canStartInstantDownload(release(apk=" "),11300)) }
    @Test fun alreadyAvailableApkUsesNormalDownload() { assertFalse(canStartInstantDownload(release(apk="https://github.com/app.apk"),11300)) }
    @Test fun sameReleaseReady() { assertTrue(instantApkReady(release(apk="apk"),11400,11300)) }
    @Test fun supersedingReleaseReady() { assertTrue(instantApkReady(release(11500,"apk"),11400,11300)) }
    @Test fun previousReleaseNotDownloaded() { assertFalse(instantApkReady(release(11301,"apk"),11400,11300)) }
    @Test fun installedVersionNeverDownloaded() { assertFalse(instantApkReady(release(11300,"apk"),11200,11300)) }
    @Test fun missingAssetNotReady() { assertFalse(instantApkReady(release(),11400,11300)) }
    @Test fun normalPollingIsThirtySeconds() { assertEquals(30000L,instantPollDelay(null)) }
    @Test fun shortRetryCannotAcceleratePolling() { assertEquals(30000L,instantPollDelay(-1)) }
    @Test fun githubBackoffRespected() { assertEquals(900000L,instantPollDelay(900000L)) }

    private class FakeRepo(val results: List<UpdateCheckResult>, var failDownloads: Int = 0) : UpdateRepository {
        override val currentVersionName="1.13.0"
        override val currentVersionCode=11300
        var checks=0;var downloads=0;val forced=mutableListOf<Boolean>()
        override suspend fun checkForUpdate(force:Boolean):UpdateCheckResult {
            forced+=force
            return results[(checks++).coerceAtMost(results.lastIndex)]
        }
        override suspend fun downloadApk(url:String,versionName:String,onProgress:(Long,Long)->Unit):File {
            downloads++
            if(failDownloads-->0) error("Network failure")
            onProgress(500,1000);onProgress(1000,1000)
            return File("downloaded.apk")
        }
    }
    private class Harness(val repo:FakeRepo) {
        var now=0L;val delays=mutableListOf<Long>();val messages=mutableListOf<String>();val progress=mutableListOf<Long>();var starts=0
        suspend fun run(sleep:suspend (Long)->Unit = { delays+=it;now+=it }):Pair<AppUpdate,File> = awaitInstantUpdate(repo,11400,{now},sleep,{messages+=it},{starts++},{done,_->progress+=done})
    }
    @Test fun waitsThenDownloadsExactlyOnce() = runBlocking {
        val repo=FakeRepo(listOf(UpdateCheckResult.Available(release()),UpdateCheckResult.Available(release(apk="apk"))))
        val h=Harness(repo);val result=h.run()
        assertEquals(11400,result.first.versionCode);assertEquals(listOf(30000L),h.delays);assertEquals(1,repo.downloads);assertEquals(listOf(true,true),repo.forced)
    }
    @Test fun transientCheckFailureRetried() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Failed("offline"),UpdateCheckResult.Available(release(apk="apk")))))
        h.run();assertEquals(listOf(30000L),h.delays);assertEquals(1,h.repo.downloads)
    }
    @Test fun rateLimitDelayIsApplied() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Failed("429",120000L),UpdateCheckResult.Available(release(apk="apk")))))
        h.run();assertEquals(listOf(120000L),h.delays);assertTrue(h.messages.first().contains("ограничил"))
    }
    @Test fun oldReleaseIgnoredUntilSelectedAvailable() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Available(release(11301,"old")),UpdateCheckResult.Available(release(apk="apk")))))
        h.run();assertEquals(2,h.repo.checks);assertEquals(1,h.repo.downloads)
    }
    @Test fun progressAndDownloadStartReported() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Available(release(apk="apk")))))
        h.run();assertEquals(1,h.starts);assertEquals(listOf(500L,1000L),h.progress);assertTrue(h.delays.isEmpty())
    }
    @Test fun failedDownloadRetriedAutomatically() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Available(release(apk="apk"))),1))
        h.run();assertEquals(2,h.repo.downloads);assertEquals(listOf(30000L),h.delays)
    }
    @Test fun threeFailedDownloadsStopWithError() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Available(release(apk="apk"))),3))
        try {h.run();fail("Expected failure")} catch(e:IllegalStateException) {assertEquals("Network failure",e.message)}
        assertEquals(3,h.repo.downloads);assertEquals(2,h.delays.size)
    }
    @Test fun cancelStopsBeforeDownload() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.UpToDate)))
        try {h.run {throw CancellationException("stop")};fail("Expected cancellation")} catch(_:CancellationException) {}
        assertEquals(1,h.repo.checks);assertEquals(0,h.repo.downloads)
    }
    @Test fun timeoutStopsWaiting() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Failed("wait",INSTANT_WAIT_LIMIT_MS))))
        try {h.run();fail("Expected timeout")} catch(e:IllegalStateException) {assertTrue(e.message!!.contains("6 часов"))}
        assertEquals(listOf(INSTANT_WAIT_LIMIT_MS),h.delays);assertEquals(1,h.repo.checks)
    }
    @Test fun backoffCannotExceedWaitLifetime() = runBlocking {
        val h=Harness(FakeRepo(listOf(UpdateCheckResult.Failed("wait",Long.MAX_VALUE))))
        try {h.run();fail("Expected timeout")} catch(_:IllegalStateException) {}
        assertEquals(listOf(INSTANT_WAIT_LIMIT_MS),h.delays)
    }
}
