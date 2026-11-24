package io.openhoyi.mobile

import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotStartObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractionStartObservationPresentationTest {
    @Test fun writeSuccessCannotShowRunningWithoutMachineEvidence() {
        val view = ExtractionStartObservationPresentation
        val running = ExtractionState.RUNNING
        assertEquals(ExtractionState.STARTING, view.displayState(running, null, false))
        assertEquals(ExtractionState.STARTING,
            view.displayState(running, ShotStartObservation.WAITING_FOR_MACHINE, true))
        assertEquals(ExtractionState.OUTCOME_UNKNOWN,
            view.displayState(running, ShotStartObservation.NOT_CONFIRMED, true))
        assertEquals(running, view.displayState(running, ShotStartObservation.MACHINE_EXTRACTION_OBSERVED, false))
        assertEquals(running, view.displayState(running, null, true))
        assertEquals(ExtractionState.STOP_REQUESTED,
            view.displayState(ExtractionState.STOP_REQUESTED, ShotStartObservation.WAITING_FOR_MACHINE, false))
    }
}
