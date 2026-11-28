package io.openhoyi.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCupSelectionTest {
    @Test fun prefersPreviousMatchingCurveWithoutSelectingIt() {
        val records = listOf(shot("newer", "curve", 60), shot("a", "curve", 50),
            shot("other", "other", 40), shot("previous", "curve", 30), shot("older", "curve", 20))
        val selection = HistoryCupSelection(firstId = "a")
        assertEquals(listOf("previous", "newer", "other", "older"), selection.candidates(records).map { it.id })
        assertNull(selection.confirmedPair(records))
        assertNull(selection.choose("a", records).confirmedPair(records))
        assertEquals("a" to "other", selection.choose("other", records).confirmedPair(records))
    }

    @Test fun missingSelectionsNeverFallBackToUnchosenCups() {
        val records = listOf(shot("a"), shot("b"))
        val first = HistoryCupSelection(firstId = "not-saved").choose("a", records)
        assertNull(first.confirmedPair(records))
        val pair = first.choose("b", records)
        assertEquals("a" to "b", pair.confirmedPair(records))
        assertNull(pair.confirmedPair(listOf(records[0], shot("replacement"))))
        assertEquals(HistoryCupSelection(), pair.reconcile(listOf(records[1])))
    }

    private fun shot(id: String, curve: String = "curve", startedAt: Long = 0) =
        BrewJournal.Observation(id, curve, startedAt, null, null, "ENDED", null, null)
}
