package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotStartObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractionStartObservationPresentationTest {
    @Test fun v2NeedsItsOwnStartEvidenceBeforeShowingRunning() {
        val view = ExtractionStartObservationPresentation
        val running = ExtractionState.RUNNING
        fun display(observation: ShotStartObservation?) =
            view.displayState(running, observation, requiresStartObservation = true, legacyFlowObserved = true)
        assertEquals(R.string.extraction_state_starting, view.stateLabel(display(null)))
        assertEquals(R.string.extraction_state_starting, view.stateLabel(display(ShotStartObservation.WAITING_FOR_MACHINE)))
        assertEquals(ExtractionState.OUTCOME_UNKNOWN, display(ShotStartObservation.NOT_CONFIRMED))
        assertEquals(R.string.extraction_state_running, view.stateLabel(display(ShotStartObservation.MACHINE_EXTRACTION_OBSERVED)))
        assertEquals(ExtractionState.STOP_REQUESTED, view.displayState(ExtractionState.STOP_REQUESTED,
            ShotStartObservation.WAITING_FOR_MACHINE, requiresStartObservation = true, legacyFlowObserved = false))
    }

    @Test fun legacyAndMockStartAfterObservedFlowAndDoNotDependOnAPollFrame() {
        val view = ExtractionStartObservationPresentation
        val running = ExtractionState.RUNNING
        assertEquals(ExtractionState.STARTING,
            view.displayState(running, null, requiresStartObservation = false, legacyFlowObserved = false))
        assertEquals(running,
            view.displayState(running, null, requiresStartObservation = false, legacyFlowObserved = true))
    }
}
