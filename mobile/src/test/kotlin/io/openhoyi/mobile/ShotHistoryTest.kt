package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotHistoryTest {
    private class Memory : ShotHistory.Storage {
        var value: String = ""
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }

    @Test fun unknownOutcomeCanLaterBecomeObservedEnd() {
        val disk = Memory()
        val history = ShotHistory(disk, now = { 1_000_000L }, newId = { "shot-1" })
        history.begin("capture-1", 1_000_000L)
        history.transition(ExtractionState.RUNNING, null, null, 1_002_000L)
        history.transition(ExtractionState.OUTCOME_UNKNOWN, null, null, 1_004_000L)
        assertEquals(ShotHistory.Status.UNKNOWN, history.entries.single().status)
        history.transition(ExtractionState.ENDED_OBSERVED, "MANUAL", 2780, 1_007_000L)
        val entry = history.entries.single()
        assertEquals(ShotHistory.Status.ENDED, entry.status)
        assertEquals(7_000L, entry.elapsedMs)
        assertEquals("MANUAL", entry.reason)
        assertEquals(2780, entry.weightHundredthsGram)
        assertEquals(entry, ShotHistory(disk, now = { 1_010_000L }).entries.single())
    }

    @Test fun processRestartMarksUnfinishedShotUnknownWithoutClaimingEnd() {
        val disk = Memory()
        ShotHistory(disk, now = { 1_000_000L }, newId = { "shot-2" }).begin("capture-2", 1_000_000L)
        val restarted = ShotHistory(disk, now = { 1_005_000L })
        assertEquals(ShotHistory.Status.UNKNOWN, restarted.entries.single().status)
        assertEquals("进程中断", restarted.entries.single().reason)
        assertNull(restarted.entries.single().endedAtMs)
    }

    @Test fun malformedRowsAreIgnoredAndHistoryIsBounded() {
        val disk = Memory()
        disk.value = "garbage\n"
        val history = ShotHistory(disk, now = { 10_000_000L }, newId = { "fixed" })
        assertTrue(history.entries.isEmpty())
        history.begin("capture-3", 10_000_000L)
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 10_001_000L)
        assertEquals(1, history.entries.size)
    }

    @Test fun capturedAndFactoryCurvesCanEnterHistoryButUnknownIdsCannot() {
        val history = ShotHistory(Memory(), now = { 1_000_000L })
        history.begin("factory-v3-001")
        assertEquals("factory-v3-001", history.entries.single().curveId)
        history.transition(ExtractionState.ENDED_OBSERVED, null, null)
        assertThrows(IllegalArgumentException::class.java) { history.begin("factory-v3-101") }
    }

    @Test fun retainsAtMostFiveHundredRecentEntries() {
        val disk = Memory()
        var time = 100_000_000L
        var next = 0
        val history = ShotHistory(disk, now = { time }, newId = { "shot-${++next}" })
        repeat(501) {
            history.begin("capture-1")
            time += 1000
            history.transition(ExtractionState.ENDED_OBSERVED, null, null)
        }
        assertEquals(500, history.entries.size)
        assertNull(history.entries.find { it.id == "shot-1" })
        assertNotNull(history.entries.find { it.id == "shot-501" })
    }
}
