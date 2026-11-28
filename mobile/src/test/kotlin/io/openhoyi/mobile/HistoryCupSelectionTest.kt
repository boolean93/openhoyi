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

    @Test fun comparesOnlyObservedEndValuesAndKeepsRealZeros() {
        val first = shot("a", seconds = 28, weight = 3759)
        val second = shot("b", seconds = 30, weight = 3800)
        assertEquals(2L, HistoryCupOutcome.difference(first, second).machineSeconds)
        assertEquals(-41L, HistoryCupOutcome.difference(second, first).scaleHundredths)
        val noTimer = second.copy(observedMachineElapsedSeconds = null, weightHundredthsGram = 0)
        assertNull(HistoryCupOutcome.difference(first, noTimer).machineSeconds)
        assertEquals(-3759L, HistoryCupOutcome.difference(first, noTimer).scaleHundredths)
        assertNull(HistoryCupOutcome.difference(first, second.copy(machineTimingInterrupted = true)).machineSeconds)
        assertNull(HistoryCupOutcome.difference(first, second.copy(status = "UNKNOWN")).machineSeconds)
    }

    private fun shot(id: String, curve: String = "curve", startedAt: Long = 0, seconds: Int? = null, weight: Int? = null) =
        BrewJournal.Observation(id, curve, startedAt, null, 33500, "ENDED", null, weight, seconds,
            observedSampleCoverageMs = 27900L)
}
