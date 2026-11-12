package io.openhoyi.mobile

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ReleaseDownloadControllerTest {
    private val target = ReleaseUpdate(94,"fixture","a".repeat(64),"b".repeat(64),"https://fixture", "", apkSize=100)
    private class Work(val state: ReleaseDownloadController.State,
        val progress: (Long, Boolean) -> Unit, val result: (Result<File>) -> Unit)
    private class Harness(restored: ReleaseDownloadController.State = ReleaseDownloadController.State()) {
        var writable = true; var cancellations = 0; var saves = 0
        val work = mutableListOf<Work>(); val checks = mutableListOf<(Result<Unit>) -> Unit>()
        val files = kotlin.io.path.createTempDirectory().toFile()
        val owner = ReleaseDownloadController(restored, { saves++; writable }, { state, progress, done ->
            work += Work(state,progress,done); { cancellations++ }
        }, { _, done -> checks += done; { cancellations++ } }, { File(files,"${work.size}.apk") })
        fun finish() { work.last().state.file!!.writeBytes(ByteArray(100)); work.last().result(Result.success(work.last().state.file!!)) }
    }
    @Test fun downloadRequiresExplicitStartAndOneImmutableTargetOwnsTheSlot() {
        val h=Harness()
        try {
            assertTrue(h.work.isEmpty()); assertTrue(h.owner.start(target)); assertFalse(h.owner.start(target.copy(versionCode=95)))
            assertEquals(target,h.owner.state.target); assertEquals(1,h.work.size)
            h.work[0].progress(44,false); assertEquals(44L,h.owner.state.received)
            h.work[0].progress(100,true); assertEquals(ReleaseDownloadController.Phase.VERIFYING,h.owner.state.phase)
            h.finish(); assertEquals(ReleaseDownloadController.Phase.READY,h.owner.state.phase)
        } finally { h.files.deleteRecursively() }
    }
    @Test fun cancellationRetiresCallbacksAndOldWorkerCannotPublishOverNewAttempt() {
        val h=Harness()
        try {
            h.owner.start(target); val old=h.work[0]; h.owner.cancel(); assertEquals(1,h.cancellations)
            h.owner.start(target.copy(versionCode=95)); old.progress(99,false); old.result(Result.success(File("old.apk")))
            assertEquals(95L,h.owner.state.target?.versionCode); assertEquals(0L,h.owner.state.received)
            h.work.last().result(Result.failure(IOException())); assertEquals(ReleaseDownloadController.Phase.FAILED,h.owner.state.phase)
            assertTrue(h.owner.start(target)); assertEquals(3,h.work.size)
        } finally { h.files.deleteRecursively() }
    }
    @Test fun failedJournalStartsNoNetworkAndRestoredFileNeedsExplicitVerification() {
        val h=Harness();h.writable=false
        try { assertFalse(h.owner.start(target));assertTrue(h.work.isEmpty()) } finally {h.files.deleteRecursively()}
        val file=kotlin.io.path.createTempFile().toFile()
        val fresh=Harness(ReleaseDownloadController.State(ReleaseDownloadController.Phase.NEEDS_VERIFICATION,target,file))
        try {
            assertTrue(fresh.work.isEmpty());assertTrue(fresh.checks.isEmpty())
            var result=false;assertTrue(fresh.owner.reverify { result=it });assertFalse(fresh.owner.reverify {})
            fresh.checks[0](Result.success(Unit));assertTrue(result);assertEquals(ReleaseDownloadController.Phase.READY,fresh.owner.state.phase)
            fresh.owner.installerOpened();assertEquals(ReleaseDownloadController.Phase.INSTALLER_OPENED,fresh.owner.state.phase)
        } finally {file.delete();fresh.files.deleteRecursively()}
    }
    @Test fun onlyActuallyInstalledMatchingIdentityClearsOldTaskAndAllowsNextUpdate() {
        val h=Harness(ReleaseDownloadController.State(ReleaseDownloadController.Phase.NEEDS_VERIFICATION,target,File("known.apk")))
        try {
            val actual=InstalledUpdateIdentity("io.openhoyi.mobile",94,false,"a".repeat(64))
            assertFalse(h.owner.reconcileInstalled(actual.copy(versionCode=93)))
            assertFalse(h.owner.reconcileInstalled(actual.copy(signerSha256="f".repeat(64))))
            assertFalse(h.owner.reconcileInstalled(null));assertTrue(h.owner.reconcileInstalled(actual))
            assertEquals(ReleaseDownloadController.Phase.IDLE,h.owner.state.phase)
            assertTrue(h.owner.start(target.copy(versionCode=95)));assertEquals(95L,h.owner.state.target?.versionCode)
            h.owner.clear();assertNull(h.owner.state.target)
        } finally {h.files.deleteRecursively()}
    }
}
