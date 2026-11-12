package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotTimingEvidenceTest {
    private class Disk : ShotHistory.Storage {
        var text = ""
        override fun read() = text
        override fun write(value: String) { text = value }
    }
    private fun frame(seconds: Int, bits: Int = 64) = ExtractionTelemetry(1, seconds, 90, 300, 9200, 20, bits, 0, ByteFrame(byteArrayOf()))
    @Test fun machine27IsNotRecord31Point2AndSurvivesHistoryAndJournalRoundtrip() {
        val disk = Disk()
        val history = ShotHistory(disk, now = { 1_000_000 }, newId = { "cup" })
        history.begin("capture-1")
        val series = ShotSeries().apply { begin("cup", 100); machine(frame(27), 27_500, null, null) }
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 1_031_200)
        history.attachTimingEvidence(series.finish()!!.first, series.observedMachineElapsedSeconds)
        val restored = ShotHistory(disk, now = { 1_040_000 }).entries.single()
        assertEquals(31_200L, restored.elapsedMs)
        assertEquals(27, restored.observedMachineElapsedSeconds)
        assertEquals("27", restored.timingValue)
        var json: String? = null
        val storage = object : BrewJournal.Storage { override fun read() = json; override fun write(value: String) { json = value } }
        val journal = BrewJournal(storage)
        journal.observe(restored.journalObservation())
        assertEquals(restored.observedMachineElapsedSeconds, BrewJournal(storage).entries().single().observation.historySummary().observedMachineElapsedSeconds)
    }
    @Test fun oldRowsAreLegacyNewMissingDoesNotFallback() {
        val disk = Disk()
        val history = ShotHistory(disk, now = { 1_000_000 }, newId = { "cup" })
        history.begin("capture-1")
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 1_031_200)
        assertNull(history.entries.single().timingValue)
        disk.text = disk.text.trimEnd().split('\t').take(10).joinToString("\t") + "\n"
        val legacy = ShotHistory(disk, now = { 1_040_000 }).entries.single()
        assertEquals(0, legacy.timingEvidenceVersion)
        assertEquals("31.2", legacy.timingValue)
    }
    @Test fun onlyCurrentCupValidFramesProvideTimeAndInterruptedSamplingDoesNotInventEnd() {
        val series = ShotSeries()
        series.begin("one", 100)
        series.machine(frame(27), 27_500, null, null)
        series.machine(frame(0, 96), 28_000, null, null)
        series.machine(frame(65536), 28_100, null, null)
        assertEquals(27, series.observedMachineElapsedSeconds)
        series.finish()
        series.machine(frame(99), 30_000, null, null)
        assertEquals(27, series.observedMachineElapsedSeconds)
        series.begin("two", 31_000)
        assertNull(series.observedMachineElapsedSeconds)
        series.finish()
        assertNull(series.observedMachineElapsedSeconds)
    }
    @Test fun wallClockJumpCannotChangeObservedMachineTime() {
        val disk = Disk()
        var wall = 1_000_000L
        val history = ShotHistory(disk, now = { wall }, newId = { "cup" })
        history.begin("capture-1")
        val series = ShotSeries().apply { begin("cup", 100); machine(frame(27), 27_500, null, null) }
        wall -= 10_000
        history.transition(ExtractionState.ENDED_OBSERVED, null, null)
        history.attachTimingEvidence(series.finish()!!.first, series.observedMachineElapsedSeconds)
        assertEquals(27, history.entries.single().observedMachineElapsedSeconds)
        assertEquals(0L, history.entries.single().elapsedMs)
    }
}
