package io.openhoyi.mobile

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateCoordinatorTest {
    private fun update(code: Long = 94): ReleaseUpdate = ReleaseUpdateIndex.parse(JSONObject(mapOf(
        "schemaVersion" to 1, "channel" to "develop-test", "repository" to "boolean93/openhoyi",
        "packageName" to "io.openhoyi.mobile", "versionCode" to code, "versionName" to "0.2.0-dev.$code",
        "apkSha256" to "b".repeat(64), "signerCertificateSha256" to "a".repeat(64),
        "declaredSourceCommit" to "c".repeat(40), "sourceCommitVerified" to false, "releaseTag" to "dev-$code",
        "publishedAt" to "2026-11-12T11:40:15Z", "apkFile" to "OpenHOYI.apk", "apkSize" to 100,
        "releaseUrl" to "https://github.com/boolean93/openhoyi/releases/tag/dev-$code",
        "downloadUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-$code/OpenHOYI.apk",
        "distributionUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-$code/distribution.json",
        "checksumsUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-$code/SHA256SUMS.txt",
    )).toString())
    private val identity = InstalledUpdateIdentity("io.openhoyi.mobile", 90, false, "a".repeat(64))
    private class Storage(var value: ReleaseUpdateCoordinator.Saved = ReleaseUpdateCoordinator.Saved()) : ReleaseUpdateCoordinator.Storage {
        var writable = true
        override fun read() = value
        override fun write(value: ReleaseUpdateCoordinator.Saved): Boolean {
            if (!writable) return false
            this.value = value
            return true
        }
    }
    private class Harness(val storage: Storage = Storage(), eligible: Boolean = true) {
        var clock = 1_000L
        var cancellations = 0
        val callbacks = mutableListOf<(Result<ReleaseUpdate>) -> Unit>()
        val owner = ReleaseUpdateCoordinator(storage, { clock }, { 1234L }, { callback ->
            callbacks += callback
            { cancellations++ }
        }, eligible)
        fun success(update: ReleaseUpdate) = callbacks.last()(Result.success(update))
        fun fail(error: Exception = IOException()) = callbacks.last()(Result.failure(error))
    }
    @Test fun dailyReservationSurvivesCancellationRestartAndForegroundChurn() {
        val h = Harness()
        h.owner.automaticCheck(); assertEquals(0, h.callbacks.size)
        h.owner.setForeground(true); h.owner.automaticCheck(); h.owner.manualCheck()
        assertEquals(1, h.callbacks.size)
        val due = h.clock + ReleaseUpdateCoordinator.DAY + 1234
        assertEquals(due, h.storage.value.nextAutoAt)
        h.owner.setForeground(false); assertEquals(1, h.cancellations)
        h.success(update()); assertNull(h.owner.state.update)
        h.owner.setForeground(true); assertEquals(1, h.callbacks.size)
        val restart = Harness(h.storage)
        restart.owner.setForeground(true); assertTrue(restart.callbacks.isEmpty())
        restart.clock = due; restart.owner.automaticCheck(); assertEquals(1, restart.callbacks.size)
    }
    @Test fun manualHasNoCooldownAndSharesAutomaticFlightEvenWhenAutomaticIsOff() {
        val h = Harness()
        h.owner.setForeground(true); h.owner.manualCheck(); assertEquals(1, h.callbacks.size)
        h.success(update()); h.owner.manualCheck(); assertEquals(2, h.callbacks.size)
        h.fail(); h.owner.manualCheck(); assertEquals(3, h.callbacks.size)
        h.success(update()); h.owner.setEnabled(false)
        h.owner.manualCheck(); assertEquals(4, h.callbacks.size)
        h.success(update()); assertNull(h.owner.promptCandidate(identity, true))
        h.owner.manualCheck(); assertEquals(5, h.callbacks.size)
    }
    @Test fun turningOffOnlyCancelsAutomaticWorkAndManualResultsRemainVisible() {
        val h = Harness(); h.owner.setForeground(true)
        assertTrue(h.owner.enabled)
        h.owner.setEnabled(false)
        assertEquals(1, h.cancellations); assertFalse(h.owner.state.checking)
        h.owner.manualCheck(); assertEquals(2, h.callbacks.size)
        h.owner.setEnabled(true); h.owner.setEnabled(false)
        assertEquals(1, h.cancellations); assertTrue(h.owner.state.checking)
        val update = update(); h.success(update)
        assertEquals(update, h.owner.state.update); assertNull(h.owner.promptCandidate(identity, true))
        assertFalse(h.storage.value.enabled)
        val restart = Harness(h.storage); restart.owner.setForeground(true)
        assertFalse(restart.owner.enabled); assertTrue(restart.callbacks.isEmpty())
        assertEquals(update, restart.owner.state.update)
        restart.owner.manualCheck(); assertEquals(1, restart.callbacks.size)
    }
    @Test fun manualJoiningAnAutomaticFlightSurvivesDisableAndReportsItsFailure() {
        val h = Harness(); h.owner.setForeground(true); h.owner.manualCheck()
        h.owner.setEnabled(false)
        assertEquals(1, h.callbacks.size); assertEquals(0, h.cancellations)
        h.fail(); assertTrue(h.owner.state.failed); assertFalse(h.owner.state.checking)
        h.owner.manualCheck(); assertEquals(2, h.callbacks.size)
        h.success(update()); assertNotNull(h.owner.state.update)
    }
    @Test fun ordinaryErrorsAreSilentAndBackOffAutomaticButManualCanRetry() {
        val h = Harness(); h.owner.setForeground(true); h.fail()
        assertFalse(h.owner.state.failed)
        assertEquals(h.clock + ReleaseUpdateCoordinator.DAY + 1234, h.storage.value.nextAutoAt)
        h.clock = h.storage.value.nextAutoAt; h.owner.automaticCheck(); h.fail()
        assertEquals(h.clock + 2 * ReleaseUpdateCoordinator.DAY + 1234, h.storage.value.nextAutoAt)
        h.owner.manualCheck(); assertEquals(3, h.callbacks.size)
        h.fail(); assertTrue(h.owner.state.failed)
    }
    @Test fun retryAfterSecondsAndHttpDateAndFallbackAreHonoredAcrossRestart() {
        assertEquals(120000L, updateRetryDelay("120", 0))
        assertEquals(0L, updateRetryDelay("0", 0))
        assertEquals(Long.MAX_VALUE, updateRetryDelay("9".repeat(80), 0))
        assertEquals(60000L, updateRetryDelay("Thu, 12 Nov 2026 12:01:00 GMT", 1794484800000L))
        assertNull(updateRetryDelay("-1", 0)); assertNull(updateRetryDelay("nonsense", 0))
        val h = Harness(); h.owner.setForeground(true); h.fail(ReleaseUpdateHttpException(429, "120"))
        h.owner.manualCheck(); assertEquals(1, h.callbacks.size); assertTrue(h.owner.state.limited)
        val restart = Harness(h.storage); restart.owner.setForeground(true); restart.owner.manualCheck()
        assertTrue(restart.callbacks.isEmpty())
        h.clock += 120000; h.owner.manualCheck(); assertEquals(2, h.callbacks.size)
        h.fail(ReleaseUpdateHttpException(429, null)); assertEquals(h.clock + 30 * 60_000, h.storage.value.rateLimitedUntil)
        h.clock = h.storage.value.rateLimitedUntil; h.owner.manualCheck()
        h.fail(ReleaseUpdateHttpException(503, "3600")); h.owner.manualCheck(); assertEquals(3, h.callbacks.size)
    }
    @Test fun serverCooldownStillAppliesWhenPersistenceFails() {
        val h = Harness(); h.owner.setForeground(true); h.storage.writable = false
        h.fail(ReleaseUpdateHttpException(429, "3600"))
        h.owner.manualCheck(); h.owner.manualCheck(); assertEquals(1, h.callbacks.size)
        h.storage.writable = true
        h.owner.setEnabled(false); h.owner.manualCheck(); assertEquals(1, h.callbacks.size)
        assertEquals(h.clock + 3600000, h.storage.value.rateLimitedUntil)
    }
    @Test fun onlyCurrentForegroundSafeMatchingInstallMayPromptAndSnoozeIsProcessScoped() {
        val h = Harness(); h.owner.setForeground(true); val candidate = update(); h.success(candidate)
        assertEquals(candidate, h.owner.promptCandidate(identity, true))
        assertNull(h.owner.promptCandidate(identity, false))
        assertNull(h.owner.promptCandidate(identity.copy(debuggable = true), true))
        assertNull(h.owner.promptCandidate(identity.copy(packageName = "io.openhoyi.mobile.mock"), true))
        assertNull(h.owner.promptCandidate(identity.copy(signerSha256 = "f".repeat(64)), true))
        assertNull(h.owner.promptCandidate(identity.copy(versionCode = 100), true))
        assertNull(h.owner.promptCandidate(null, true))
        h.owner.setForeground(false); assertNull(h.owner.promptCandidate(identity, true))
        h.owner.setForeground(true); assertEquals(candidate, h.owner.promptCandidate(identity, true))
        h.owner.dismiss(candidate); assertNull(h.owner.promptCandidate(identity, true))
        h.owner.setForeground(false); h.owner.setForeground(true); assertNull(h.owner.promptCandidate(identity, true))
        val restart = Harness(h.storage); restart.owner.setForeground(true)
        assertTrue(restart.callbacks.isEmpty()); assertEquals(candidate, restart.owner.promptCandidate(identity, true))
        h.owner.manualCheck(); h.success(update(95)); assertEquals(95L, h.owner.promptCandidate(identity, true)?.versionCode)
    }
    @Test fun disabledRequestAndRetiredCallbackCannotReplaceCurrentCandidate() {
        val h = Harness(); h.owner.setForeground(true)
        val old = h.callbacks[0]; h.owner.setEnabled(false)
        old(Result.success(update())); old(Result.failure(IOException()))
        assertNull(h.owner.state.update); assertFalse(h.owner.state.checking)
        h.owner.setEnabled(true); assertEquals(1, h.callbacks.size)
        h.owner.manualCheck(); val current = update(95); h.success(current)
        old(Result.success(update())); assertEquals(current, h.owner.state.update)
        assertFalse(h.owner.owns(update()))
    }
    @Test fun corruptCacheIsDiscardedAndDebugMockNeverAutocheckButManualWorks() {
        val h = Harness(Storage(ReleaseUpdateCoordinator.Saved(cachedIndex = "untrusted JSON")), eligible = false)
        h.owner.setForeground(true); assertTrue(h.callbacks.isEmpty()); assertNull(h.owner.state.update)
        h.owner.manualCheck(); assertEquals(1, h.callbacks.size)
        h.success(update()); assertNotNull(h.owner.state.update)
    }
    @Test fun synchronousCompletionAndStartFailureDoNotLeaveCheckingState() {
        var stops = 0
        val c = ReleaseUpdateCoordinator(Storage(), { 1L }, { 0L }, { callback ->
            callback(Result.success(update())); { stops++ }
        })
        c.setForeground(true); assertFalse(c.state.checking); assertEquals(1, stops)
        val failing = ReleaseUpdateCoordinator(Storage(), { 1L }, { 0L }, { throw IOException() })
        failing.setForeground(true); assertFalse(failing.state.checking)
        failing.manualCheck(); assertTrue(failing.state.failed)
    }
}
