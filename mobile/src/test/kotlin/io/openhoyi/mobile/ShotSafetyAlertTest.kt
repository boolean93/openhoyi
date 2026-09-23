package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotSafetyAlertTest {
    @Test fun interruptedShotRequiresPhysicalMachineCheck() {
        assertNull(ShotSafetyAlert.message(ExtractionState.IDLE, DeviceState.FAILED))
        assertNotNull(ShotSafetyAlert.message(ExtractionState.RUNNING, DeviceState.FAILED))
        assertNotNull(ShotSafetyAlert.message(ExtractionState.STOP_REQUESTED, DeviceState.DISCONNECTED))
        assertTrue(ShotSafetyAlert.message(ExtractionState.OUTCOME_UNKNOWN, DeviceState.FAILED)!!
            .contains("手动"))
    }

    @Test fun uncertainOutcomePersistsAfterReconnectUntilObservedEnd() {
        assertNotNull(ShotSafetyAlert.message(ExtractionState.OUTCOME_UNKNOWN, DeviceState.READY))
        assertNull(ShotSafetyAlert.message(ExtractionState.ENDED_OBSERVED, DeviceState.READY))
    }
}
