package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class RawImportRestoreReadinessTest {
    private fun stable(gate: RawImportRestoreReadiness, candidate: String = "root/editor/token/insets/layout") {
        assertFalse(gate.preDraw(candidate)); assertTrue(gate.drawn(candidate))
        assertFalse(gate.preDraw(candidate)); assertTrue(gate.drawn(candidate))
        assertTrue(gate.preDraw(candidate))
    }
    @Test fun consumesActualDrawsAndStopsRequestingWhenResultIsAbsent() {
        val gate = RawImportRestoreReadiness(true)
        stable(gate)
        assertFalse(gate.drawn("root/editor/token/insets/layout"))
    }
    @Test fun changedTokenInsetsOrLayoutCannotReuseDrawEvidence() {
        for (change in listOf("new-token", "hidden-ime", "new-viewport")) {
            val gate = RawImportRestoreReadiness(true)
            stable(gate)
            assertFalse(gate.preDraw(change))
            assertTrue(gate.drawn(change))
            assertFalse(gate.preDraw(change))
        }
    }
    @Test fun progressReplacesRunningSetEvenWhenPrepareWasMissed() {
        val gate = RawImportRestoreReadiness(true)
        stable(gate)
        val hiddenTransition = Any()
        assertTrue(gate.runningAnimations(setOf(hiddenTransition)))
        assertEquals(setOf(hiddenTransition), gate.animations)
        assertFalse(gate.preDraw(null)); assertFalse(gate.drawn(null))
        assertTrue(gate.runningAnimations(emptySet()))
        assertTrue(gate.animations.isEmpty())
        stable(gate)
    }
    @Test fun nativeResultNeedsNewDrawsAndNoTimerRetries() {
        val gate = RawImportRestoreReadiness(true)
        stable(gate); gate.nativeResult()
        assertEquals(0, gate.drawCount)
        stable(gate)
    }
    @Test fun cancellationAndOwnerReplacementNeverResurrectPendingWork() {
        for (userCancel in listOf(true, false)) {
            val gate = RawImportRestoreReadiness(true)
            stable(gate)
            if (userCancel) gate.cancel() else assertFalse(gate.ownsNativeWindow(false))
            assertFalse(gate.ownsNativeWindow(true))
            gate.nativeResult()
            assertFalse(gate.preDraw("same")); assertFalse(gate.drawn("same"))
        }
    }
    @Test fun savedHiddenImeDoesNotRequireActiveInputConnection() {
        assertTrue(RawImportRestoreReadiness(false).connectionReady(false))
        assertFalse(RawImportRestoreReadiness(true).connectionReady(false))
        assertTrue(RawImportRestoreReadiness(true).connectionReady(true))
    }
}
