package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class BrewJournalTest {
    private class Store : BrewJournal.Storage {
        var document: String? = null
        var fail = false
        override fun read() = document
        override fun write(value: String) { if (fail) error("disk full"); document = value }
    }
    private fun shot(id: String = "shot") = BrewJournal.Observation(id, "curve", 100, null, null, "UNKNOWN", "Disconnected", null)
    private fun rejects(block: () -> Unit) = assertTrue(runCatching(block).isFailure)
    @Test fun replayRepairsMissedTerminalWriteWithoutLosingNotesOrDowngradingKnownEnd() {
        val store=Store();var journal=BrewJournal(store)
        journal.observe(shot().copy(status="RUNNING",reason=null))
        journal.edit("shot",BrewJournal.Notes(taste="Sweet"))
        val terminal=shot().copy(status="ENDED",endedAtMs=200,elapsedMs=100,weightHundredthsGram=3500)
        store.fail=true;rejects {journal.observe(terminal)};store.fail=false
        journal=BrewJournal(store)
        assertEquals("UNKNOWN",journal.find("shot")!!.observation.status)
        journal.reconcileRecent(listOf(terminal))
        assertEquals(terminal,journal.find("shot")!!.observation)
        assertEquals("Sweet",journal.find("shot")!!.notes.taste)
        journal.reconcileRecent(listOf(shot()))
        assertEquals(terminal,journal.find("shot")!!.observation)
    }
    @Test fun restartKeepsUnknownAndNotesIndependentlyOfSampleCache() {
        val store = Store()
        val journal = BrewJournal(store)
        journal.observe(shot())
        val notes = BrewJournal.Notes("bean", "Ethiopia", 18_000, "Fine", "Matched", "Sweet", "Coarser")
        journal.edit("shot", notes)
        val restarted = BrewJournal(store)
        assertEquals(notes, restarted.find("shot")!!.notes)
        assertEquals("UNKNOWN", restarted.find("shot")!!.observation.status)
        assertNull(restarted.find("shot")!!.observation.weightHundredthsGram)
        // No dependency on history/sample storage; deleting its separate cache cannot remove this record.
        assertEquals(1, restarted.entries().size)
        assertTrue(restarted.exportJson().contains("Coarser"))
    }
    @Test fun repeatedObservationPreservesEditsAndCannotChangeShotIdentity() {
        val store = Store()
        val journal = BrewJournal(store)
        journal.observe(shot())
        journal.edit("shot", BrewJournal.Notes(taste = "Bright"))
        val before = store.document
        journal.observe(shot())
        assertEquals(before, store.document)
        journal.observe(shot().copy(status = "ENDED", endedAtMs = 200, elapsedMs = 100, weightHundredthsGram = 3500))
        assertEquals("Bright", journal.find("shot")!!.notes.taste)
        assertEquals(3500, journal.find("shot")!!.observation.weightHundredthsGram)
        rejects { journal.observe(shot().copy(curveId = "other")) }
        rejects { journal.edit("missing", BrewJournal.Notes()) }
        assertEquals(1, journal.entries().size)
    }
    @Test fun failedObservationOrEditDoesNotChangeMemoryOrDocument() {
        val store = Store()
        val journal = BrewJournal(store)
        journal.observe(shot())
        val before = store.document
        store.fail = true
        rejects { journal.edit("shot", BrewJournal.Notes(taste = "Lost")) }
        rejects { journal.observe(shot().copy(status = "RUNNING")) }
        rejects { journal.observe(shot("second")) }
        assertEquals(before, store.document)
        assertEquals("", journal.find("shot")!!.notes.taste)
        assertEquals("UNKNOWN", journal.find("shot")!!.observation.status)
        assertEquals(1, journal.entries().size)
    }
    @Test fun explicitMigrationAddsOnlyMissingShotsWithoutRewritingNewerObservations() {
        val journal = BrewJournal(Store())
        journal.observe(shot().copy(status = "ENDED", endedAtMs = 200))
        journal.edit("shot", BrewJournal.Notes(taste = "Keep"))
        assertEquals(1, journal.importRecent(listOf(shot(), shot("old"))))
        assertEquals("ENDED", journal.find("shot")!!.observation.status)
        assertEquals("Keep", journal.find("shot")!!.notes.taste)
        assertEquals(0, journal.importRecent(listOf(shot(), shot("old"))))
    }
    @Test fun restartWithEvictedRecentHistoryMarksUnresolvedObservationUnknown() {
        val store = Store()
        val original = BrewJournal(store)
        original.observe(shot().copy(status = "RUNNING"))
        original.edit("shot", BrewJournal.Notes(taste = "Keep"))
        val restored = BrewJournal(store)
        assertEquals("UNKNOWN", restored.find("shot")!!.observation.status)
        assertNull(restored.find("shot")!!.observation.endedAtMs)
        assertEquals("Keep", restored.find("shot")!!.notes.taste)
    }
    @Test fun restartPersistenceFailureDoesNotEraseOriginalDocument() {
        val store = Store()
        BrewJournal(store).observe(shot().copy(status = "RUNNING"))
        val originalDocument = store.document
        store.fail = true
        rejects { BrewJournal(store) }
        assertEquals(originalDocument, store.document)
    }
    @Test fun trailingInputIsRejectedEvenWhenParsedPayloadChecksumIsValid() {
        val store = Store()
        BrewJournal(store).observe(shot())
        store.document += " extra"
        rejects { BrewJournal(store) }
    }
    @Test fun damagedAndUnsupportedDocumentsRejectWithoutClearing() {
        listOf("", "not-json", "{}", "{\"schema\":9,\"entries\":[]}", "{\"schema\":1,\"entries\":[{}]}").forEach {
            val store = Store().apply { document = it }
            rejects { BrewJournal(store) }
            assertEquals(it, store.document)
        }
        val store = Store()
        val journal = BrewJournal(store)
        rejects { journal.observe(shot().copy(status = "ASSUMED_SUCCESS")) }
        journal.observe(shot())
        rejects { journal.edit("shot", BrewJournal.Notes(doseMg = -1)) }
    }
}
