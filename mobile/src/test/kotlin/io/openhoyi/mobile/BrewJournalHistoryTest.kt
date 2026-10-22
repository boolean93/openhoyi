package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class BrewJournalHistoryTest {
    private class JournalStore : BrewJournal.Storage {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
    private class HistoryStore : ShotHistory.Storage {
        var value = ""
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
    private fun ShotHistory.Entry.observation() = BrewJournal.Observation(id, curveId, startedAtMs, endedAtMs, elapsedMs, status.name, reason, weightHundredthsGram)

    @Test fun restartAndThirtyDayEvictionDoNotDeletePersonalNotesOrInferCompletion() {
        val durable = JournalStore()
        val journal = BrewJournal(durable)
        val bounded = HistoryStore()
        val history = ShotHistory(bounded, now = { 1_000_000L }, newId = { "shot" }, onEntryChanged = { journal.observe(it.observation()) })
        history.begin("manual", slot = 6)
        history.transition(ExtractionState.RUNNING, null, null)
        journal.edit("shot", BrewJournal.Notes(taste = "Keep forever"))
        ShotHistory(bounded, now = { 1_002_000L }, onEntryChanged = { journal.observe(it.observation()) })
        assertEquals("UNKNOWN", journal.find("shot")!!.observation.status)
        assertNull(journal.find("shot")!!.observation.endedAtMs)
        assertNull(journal.find("shot")!!.observation.weightHundredthsGram)
        // Independently bounded recent history is gone; no samples are needed to recover notes.
        val evicted = ShotHistory(bounded, now = { 1_000_000L + 31L * 24 * 60 * 60 * 1000 }, onEntryChanged = { journal.observe(it.observation()) })
        assertTrue(evicted.entries.isEmpty())
        val restored = BrewJournal(durable)
        assertEquals("Keep forever", restored.find("shot")!!.notes.taste)
        assertEquals("UNKNOWN", restored.find("shot")!!.observation.status)
    }
    @Test fun failingJournalObserverDoesNotBreakHistoryOrMachineTransitions() {
        val history = ShotHistory(HistoryStore(), now = { 1_000_000L }, onEntryChanged = { error("journal disk full") })
        history.begin("manual", slot = 6)
        history.transition(ExtractionState.RUNNING, null, null)
        history.transition(ExtractionState.OUTCOME_UNKNOWN, "Disconnected", null)
        assertEquals(ShotHistory.Status.UNKNOWN, history.entries.single().status)
        history.transition(ExtractionState.ENDED_OBSERVED, "MANUAL", 3600, 1_005_000L)
        assertEquals(ShotHistory.Status.ENDED, history.entries.single().status)
        assertEquals(3600, history.entries.single().weightHundredthsGram)
    }
}
