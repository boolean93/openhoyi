package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotSampleCoverageTest {
    private class Disk : ShotHistory.Storage {
        var text = ""
        override fun read() = text
        override fun write(value: String) { text = value }
    }
    private fun frame(bits: Int = 64) = ExtractionTelemetry(1, -1, 90, 300, 9200, 20, bits, 0, ByteFrame(byteArrayOf()))
    private fun point(time: Long, brewing: Boolean? = true) = ShotPoint(time, 90, 20, 300, 9200, null, brewing = brewing)

    @Test fun coverageUsesFirstAndLastValidSamplesNotRecordingOriginOrContiguousSum() {
        val series = ShotSeries()
        series.begin("one", 1000)
        series.machine(frame(96), 1100, null, null)
        series.machine(frame(), 6000, null, null)
        series.machine(frame(), 6500, null, null)
        // A gap does not reset the original observed head or subtract the missing interval.
        series.machine(frame(), 26000, null, null)
        series.machine(frame(96), 30000, null, null)
        assertEquals(20_000L, series.observedSampleCoverageMs)
        assertNull(series.observedMachineElapsedSeconds)
        series.rebind("persisted-one")
        assertEquals(20_000L, series.observedSampleCoverageMs)
        series.finish()
        series.begin("two", 40000)
        assertNull(series.observedSampleCoverageMs)
        series.machine(frame(), 45000, null, null)
        assertNull(series.observedSampleCoverageMs)
    }
    @Test fun legacyUnknownPhaseAndSingleOrDuplicateSamplesCannotInventCoverage() {
        assertNull(observedSampleCoverage(emptyList()))
        assertNull(observedSampleCoverage(listOf(point(5000))))
        assertNull(observedSampleCoverage(listOf(point(5000), point(5000))))
        assertNull(observedSampleCoverage(listOf(point(0, null), point(30000, null))))
        assertNull(observedSampleCoverage(listOf(point(0, false), point(30000, false))))
        assertEquals(20_000L, observedSampleCoverage(listOf(point(0, false), point(5000), point(25000), point(31000, false))))
    }
    @Test fun fallbackSurvivesRestartAndJournalRoundtripWithoutBecomingMachineTime() {
        val disk = Disk()
        val history = ShotHistory(disk, now = { 1_000_000 }, newId = { "cup" })
        history.begin("capture-1")
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 1_031_200)
        history.attachTimingEvidence("cup", null, 20_000)
        val entry = ShotHistory(disk, now = { 1_040_000 }).entries.single()
        assertNull(entry.observedMachineElapsedSeconds)
        assertEquals(20_000L, entry.observedSampleCoverageMs)
        assertEquals(R.string.history_sample_coverage, entry.timingLabel)
        assertEquals("20.0", entry.timingValue)
        var json: String? = null
        val storage = object : BrewJournal.Storage { override fun read() = json; override fun write(value: String) { json = value } }
        BrewJournal(storage).observe(entry.journalObservation())
        assertEquals(entry.observedSampleCoverageMs, BrewJournal(storage).entries().single().observation.historySummary().observedSampleCoverageMs)
        val machine = entry.copy(observedMachineElapsedSeconds = 27)
        assertEquals(R.string.history_duration, machine.timingLabel)
        assertEquals("27", machine.timingValue)
    }
    @Test fun restartBackfillsOnlyObservedOldSampleBoundsAndPreservesLegacyIdentity() {
        val disk = Disk()
        val history = ShotHistory(disk, now = { 1_000_000 }, newId = { "cup" })
        history.begin("capture-1")
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 1_031_200)
        disk.text = disk.text.trimEnd().split('\t').take(10).joinToString("\t") + "\n"
        val restored = ShotHistory(disk, now = { 1_040_000 })
        restored.recoverSampleCoverage("other-cup", listOf(point(0), point(50000)))
        restored.recoverSampleCoverage("cup", listOf(point(0, null), point(31000, null)))
        assertNull(restored.entries.single().observedSampleCoverageMs)
        restored.recoverSampleCoverage("cup", listOf(point(5000), point(25000)))
        val entry = ShotHistory(disk, now = { 1_040_000 }).entries.single()
        assertEquals(0, entry.timingEvidenceVersion)
        assertEquals(20_000L, entry.observedSampleCoverageMs)
        assertNull(entry.observedMachineElapsedSeconds)
        assertEquals(R.string.history_sample_coverage, entry.timingLabel)
        assertEquals(31_200L, entry.elapsedMs)
    }
    @Test fun decimationCannotMoveTheObservedHead() {
        val series = ShotSeries()
        series.begin("cup", 0)
        for (i in 0..4000) series.machine(frame(), 5000L + i * 10L, null, null)
        assertEquals(40_000L, series.observedSampleCoverageMs)
    }
}
