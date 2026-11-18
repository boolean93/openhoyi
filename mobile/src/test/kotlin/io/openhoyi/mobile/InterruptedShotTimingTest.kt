package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InterruptedShotTimingTest {
    private class Disk : ShotHistory.Storage {
        var text = ""
        var fail = false
        override fun read() = text
        override fun write(value: String) { check(!fail); text = value }
    }
    private fun Disk.openJournal() = BrewJournal(object : BrewJournal.Storage {
        override fun read() = this@openJournal.text.takeIf { it.isNotEmpty() }
        override fun write(value: String) = this@openJournal.write(value)
    })
    private fun frame(seconds: Int) =
        ExtractionTelemetry(1, seconds, 90, 300, 9200, 20, 64, 0, ByteFrame(byteArrayOf()))

    @Test fun fastUnknownThenObservedEndKeepsPreInterruptionTimerAndContinuousCoverage() {
        val recent = Disk()
        val permanent = Disk()
        val journal = permanent.openJournal()
        val ids = listOf("interrupted", "normal").iterator()
        val history = ShotHistory(recent, now = { 100_000L }, newId = ids::next,
            onEntryChanged = { journal.observe(it.journalObservation()) })
        val cup = ShotSeries()
        val id = history.begin("capture-1")
        cup.begin(id, 0)
        cup.machine(frame(12), 12_000, null, null)
        cup.machine(frame(14), 14_000, null, null)
        cup.observeAppTimingUncertainty(true)
        cup.machine(frame(19), 19_000, null, null)
        // The watcher saw only the settled idle; the interruption still comes from the controller.
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 121_000,
            timing = cup.timingEvidence(true))
        val restored = ShotHistory(recent, now = { 122_000L }).entries.single()
        assertEquals(ShotHistory.Status.ENDED, restored.status)
        assertEquals(true, restored.machineTimingInterrupted)
        assertEquals(14, restored.observedMachineElapsedSeconds)
        assertEquals(7_000L, restored.observedSampleCoverageMs)
        assertEquals(21_000L, restored.elapsedMs)
        assertEquals(R.string.history_pre_interruption_duration, restored.timingLabel)
        assertEquals("14", restored.timingValue)
        assertEquals(4, JSONObject(permanent.text).getInt("schema"))
        assertEquals(restored.journalObservation(),
            permanent.openJournal().find(id)?.observation)

        val next = history.begin("capture-1")
        cup.begin(next, 30_000)
        cup.machine(frame(17), 47_000, null, null)
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 150_000,
            timing = cup.timingEvidence(false))
        assertEquals(false, history.entries.first { it.id == next }.machineTimingInterrupted)
        assertEquals(R.string.history_duration, history.entries.first { it.id == next }.timingLabel)
    }

    @Test fun absentPreInterruptionTimeCannotBeBackfilledAfterRecoveryOrReplay() {
        val disk = Disk()
        val history = ShotHistory(disk, now = { 200L }, newId = { "cup" })
        history.begin("capture-1")
        val series = ShotSeries()
        series.begin("cup", 0)
        series.observeAppTimingUncertainty(true)
        series.machine(frame(19), 100, null, null)
        history.transition(ExtractionState.OUTCOME_UNKNOWN, null, null, 300,
            timing = series.timingEvidence(true))
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 400,
            timing = ShotTimingEvidence("cup", 28, null, false))
        val observed = history.entries.single().journalObservation()
        assertEquals(true, observed.machineTimingInterrupted)
        assertNull(observed.observedMachineElapsedSeconds)
        val diskJournal = Disk()
        val journal = diskJournal.openJournal()
        journal.observe(observed)
        journal.edit("cup", BrewJournal.Notes(taste = "kept"))
        journal.reconcileRecent(listOf(observed.copy(machineTimingInterrupted = null, observedMachineElapsedSeconds = 29)))
        journal.observe(observed.copy(machineTimingInterrupted = false, observedMachineElapsedSeconds = 31))
        val restored = diskJournal.openJournal().find("cup")!!
        assertEquals(true, restored.observation.machineTimingInterrupted)
        assertNull(restored.observation.observedMachineElapsedSeconds)
        assertEquals("kept", restored.notes.taste)
    }

    @Test fun writesTerminalStateAndEvidenceTogetherOrDoesNotNotify() {
        val disk = Disk()
        val events = mutableListOf<ShotHistory.Entry>()
        val history = ShotHistory(disk, now = { 100L }, newId = { "cup" }, onEntryChanged = { events += it })
        history.begin("capture-1")
        val evidence = ShotTimingEvidence("cup", 14, null, true)
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 140,
            timing = evidence.copy(shotId = "previous"))
        assertEquals(ShotHistory.Status.STARTING, history.entries.single().status)
        disk.fail = true
        assertTrue(runCatching {
            history.transition(ExtractionState.ENDED_OBSERVED, null, null, 150, timing = evidence)
        }.isFailure)
        assertEquals(listOf(ShotHistory.Status.STARTING), events.map { it.status })
        assertNull(history.entries.single().machineTimingInterrupted)
        disk.fail = false
        history.transition(ExtractionState.ENDED_OBSERVED, null, null, 150, timing = evidence)
        assertEquals(listOf(ShotHistory.Status.STARTING, ShotHistory.Status.ENDED), events.map { it.status })
        assertEquals(true, events.last().machineTimingInterrupted)
        assertEquals(14, events.last().observedMachineElapsedSeconds)
    }

    @Test fun failedAbandonKeepsActiveManualIdAndRetriesSameSnapshot() {
        val disk = Disk()
        val ids = listOf("manual", "next").iterator()
        val history = ShotHistory(disk, now = { 100L }, newId = ids::next)
        history.begin("manual", slot = 6)
        val evidence = ShotTimingEvidence("manual", 14, null, true)
        disk.fail = true
        assertTrue(runCatching { history.abandon("manual", "lost", timing = evidence) }.isFailure)
        assertEquals(ShotHistory.Status.STARTING, history.entries.single().status)
        disk.fail = false
        history.abandon("manual", "lost", timing = evidence)
        assertEquals(ShotHistory.Status.UNKNOWN, history.entries.single().status)
        assertEquals(14, history.entries.single().observedMachineElapsedSeconds)
        assertEquals("next", history.begin("capture-1"))
    }

    @Test fun legacyEndedStaysUnclassifiedButProcessRecoveryMarksUnfinishedShot() {
        val disk = Disk()
        val legacy = ShotHistory(disk, now = { 100L }, newId = { "legacy" })
        legacy.begin("capture-1")
        legacy.transition(ExtractionState.ENDED_OBSERVED, null, null, 250)
        legacy.attachTimingEvidence("legacy", 14)
        ShotHistory(disk, now = { 300L }, newId = { "unfinished" }).begin("capture-1")
        val restored = ShotHistory(disk, now = { 300L }).entries.associateBy { it.id }
        assertNull(restored.getValue("legacy").machineTimingInterrupted)
        assertEquals(R.string.history_duration, restored.getValue("legacy").timingLabel)
        assertEquals(ShotHistory.Status.UNKNOWN, restored.getValue("unfinished").status)
        assertEquals(true, restored.getValue("unfinished").machineTimingInterrupted)
        assertNull(restored.getValue("unfinished").observedMachineElapsedSeconds)
        val journalDisk = Disk()
        journalDisk.openJournal().observe(restored.getValue("legacy").journalObservation())
        assertEquals(3, JSONObject(journalDisk.text).getInt("schema"))
        assertNull(journalDisk.openJournal().entries().single().observation.machineTimingInterrupted)

        val oldUnknown = Disk()
        val h = ShotHistory(oldUnknown, now = { 300L }, newId = { "old-unknown" })
        h.begin("capture-1")
        h.abandon("old-unknown", "lost", timing = ShotTimingEvidence("old-unknown", 14, null, true))
        oldUnknown.text = oldUnknown.text.removeSuffix("\ttrue\n") + "\n" // 13-field historical row
        val old = ShotHistory(oldUnknown, now = { 300L }).entries.single()
        assertEquals(ShotHistory.Status.UNKNOWN, old.status)
        assertNull(old.machineTimingInterrupted)
        assertEquals(R.string.history_duration, old.timingLabel)
    }

    @Test fun historyExportAndAiKeepIncompleteSecondsSeparateFromDuration() {
        val entry = ShotHistory.Entry("cup", "capture-1", 100, 250, 150, ShotHistory.Status.ENDED,
            null, null, observedMachineElapsedSeconds = 14, timingEvidenceVersion = 1,
            machineTimingInterrupted = true)
        val bytes = ByteArrayOutputStream()
        ShotHistoryArchive.write(listOf(entry), { emptyList() }, bytes)
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) {
                val file = zip.nextEntry ?: break
                files[file.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(files.getValue("history.csv").contains("observedSampleCoverageMs,machineTimingInterrupted"))
        assertTrue(files.getValue("history.csv").contains(",14,1,,true"))
        assertTrue(files.getValue("README.txt").contains("中断前观察值"))
        val row = AiReadOnlyQuery.history(listOf(entry.journalObservation()), null, 0, 10)
            .getJSONArray("items").getJSONObject(0)
        assertTrue(row.isNull("observedDurationSeconds"))
        assertEquals(14, row.getInt("preInterruptionMachineElapsedSeconds"))
        assertTrue(row.getBoolean("machineTimingInterrupted"))
    }
}
