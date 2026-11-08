package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class CurveUsageLedgerTest {
    private class Memory : CurveUsageLedger.Storage {
        var value = ""
        var fail = false
        override fun read() = value
        override fun write(value: String) { check(!fail); this.value = value }
    }
    @Test fun duplicateShotCountsOnceAndSurvivesRestart() {
        val disk = Memory()
        val ledger = CurveUsageLedger(disk)
        assertTrue(ledger.record("shot-1", "capture-1", 1000))
        assertFalse(ledger.record("shot-1", "capture-1", 1000))
        assertEquals(CurveUsageStats(1, 1000), CurveUsageLedger(disk).stats("capture-1"))
        assertThrows(IllegalArgumentException::class.java) { ledger.record("shot-1", "capture-2", 1000) }
    }
    @Test fun failedStorageDoesNotPublishAnUnwrittenUse() {
        val disk = Memory()
        val ledger = CurveUsageLedger(disk)
        disk.fail = true
        assertThrows(IllegalStateException::class.java) { ledger.record("shot-1", "capture-1", 1000) }
        assertEquals(CurveUsageStats(0, null), ledger.stats("capture-1"))
        disk.fail = false
        assertTrue(ledger.record("shot-1", "capture-1", 1000))
    }
    @Test fun corruptStorageIsNotSilentlyReset() {
        val disk = Memory().apply { value = "broken data" }
        assertThrows(IllegalArgumentException::class.java) { CurveUsageLedger(disk) }
        assertEquals("broken data", disk.value)
    }
    @Test fun observedRunningCountsButRejectedStartDoesNot() {
        val ledger = CurveUsageLedger(Memory())
        val disk = object : ShotHistory.Storage {
            var value = ""
            override fun read() = value
            override fun write(value: String) { this.value = value }
        }
        var id = 0
        val history = ShotHistory(disk, now = { 1000L }, newId = { "shot-${++id}" },
            onObservedUse = { ledger.record(it.id, it.curveId, it.startedAtMs) })
        history.begin("capture-1")
        history.transition(io.openhoyi.session.ExtractionState.IDLE, null, null)
        assertEquals(0L, ledger.stats("capture-1").count)
        history.begin("capture-1")
        history.transition(io.openhoyi.session.ExtractionState.RUNNING, null, null)
        assertEquals(0L, ledger.stats("capture-1").count)
        repeat(2) { history.observeRunning() }
        history.transition(io.openhoyi.session.ExtractionState.OUTCOME_UNKNOWN, null, null)
        assertEquals(1L, ledger.stats("capture-1").count)
        assertEquals(ShotHistory.Status.UNKNOWN, history.entries.single { it.id == "shot-2" }.status)
        val restarted = ShotHistory(disk, now = { 40L * 24 * 60 * 60 * 1000 },
            onObservedUse = { ledger.record(it.id, it.curveId, it.startedAtMs) })
        assertTrue(restarted.entries.isEmpty())
        assertEquals(1L, ledger.stats("capture-1").count)
    }
    @Test fun manualShotsAreNotPresetUses() {
        val ledger = CurveUsageLedger(Memory())
        assertThrows(IllegalArgumentException::class.java) { ledger.record("manual-shot", "manual", 1000) }
    }
    @Test fun latestObservedUseComesFromPermanentRecordsByStartTime() {
        val disk = Memory()
        val ledger = CurveUsageLedger(disk)
        assertNull(ledger.latest())
        ledger.record("shot-newer", "capture-2", 3000)
        ledger.record("shot-older", "capture-1", 1000)
        assertEquals(CurveUsage("shot-newer", "capture-2", 3000), ledger.latest())
        assertEquals(ledger.latest(), CurveUsageLedger(disk).latest())
    }
    @Test fun observedDraftFingerprintSurvivesRestartAndCannotBeReplaced() {
        val disk = Memory()
        val ledger = CurveUsageLedger(disk)
        val fingerprint = "a".repeat(64)
        ledger.record("shot-1", "draft-curve", 1000)
        assertTrue(ledger.attachFingerprint("shot-1", fingerprint))
        assertFalse(ledger.attachFingerprint("shot-1", fingerprint))
        assertFalse(ledger.record("shot-1", "draft-curve", 1000))
        assertEquals(CurveUsage("shot-1", "draft-curve", 1000, fingerprint), ledger.latest())
        assertEquals(ledger.latest(), CurveUsageLedger(disk).latest())
        assertThrows(IllegalArgumentException::class.java) {
            ledger.attachFingerprint("shot-1", "b".repeat(64))
        }
        assertEquals(fingerprint, ledger.latest()?.fingerprint)
    }
    @Test fun failedFingerprintWriteLeavesPriorRecordIntact() {
        val disk = Memory()
        val ledger = CurveUsageLedger(disk)
        ledger.record("shot-1", "draft-curve", 1000)
        disk.fail = true
        assertThrows(IllegalStateException::class.java) { ledger.attachFingerprint("shot-1", "a".repeat(64)) }
        assertNull(ledger.latest()?.fingerprint)
    }
}
