package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class ShotSafetyPolicyTest {
    @Test fun unknownOutcomeStillNeedsInspectionAfterReconnect() {
        assertEquals(ShotSafetyPolicy.Reason.OUTCOME_UNKNOWN,
            ShotSafetyPolicy.reason(ExtractionState.OUTCOME_UNKNOWN, DeviceState.READY))
        DeviceState.entries.filter { it != DeviceState.READY }.forEach { connection ->
            assertEquals(ShotSafetyPolicy.Reason.UNKNOWN_AND_DISCONNECTED,
                ShotSafetyPolicy.reason(ExtractionState.OUTCOME_UNKNOWN, connection))
        }
    }

    @Test fun interruptedStartRunAndStopRequirePhysicalMachineInspection() {
        listOf(ExtractionState.STARTING, ExtractionState.RUNNING, ExtractionState.STOP_REQUESTED).forEach { shot ->
            assertNull(ShotSafetyPolicy.reason(shot, DeviceState.READY))
            DeviceState.entries.filter { it != DeviceState.READY }.forEach { connection ->
                assertEquals(ShotSafetyPolicy.Reason.ACTIVE_AND_DISCONNECTED,
                    ShotSafetyPolicy.reason(shot, connection))
            }
        }
    }

    @Test fun idleOrObservedEndDoesNotInventAnExtractionWarning() {
        listOf(ExtractionState.IDLE, ExtractionState.ENDED_OBSERVED).forEach { shot ->
            DeviceState.entries.forEach { connection -> assertNull(ShotSafetyPolicy.reason(shot, connection)) }
        }
    }
}
