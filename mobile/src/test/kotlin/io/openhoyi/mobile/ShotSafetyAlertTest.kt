package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class ShotSafetyAlertTest {
    @Test fun interruptedShotRequiresPhysicalMachineCheck() {
        assertNull(ShotSafetyAlert.resource(ExtractionState.IDLE, DeviceState.FAILED))
        assertNotNull(ShotSafetyAlert.resource(ExtractionState.RUNNING, DeviceState.FAILED))
        assertNotNull(ShotSafetyAlert.resource(ExtractionState.STOP_REQUESTED, DeviceState.DISCONNECTED))
        assertTrue(DefaultStringResources.resolve(
            ShotSafetyAlert.resource(ExtractionState.OUTCOME_UNKNOWN, DeviceState.FAILED)!!, emptyArray())
            .contains("手动"))
    }

    @Test fun uncertainOutcomePersistsAfterReconnectUntilObservedEnd() {
        assertNotNull(ShotSafetyAlert.resource(ExtractionState.OUTCOME_UNKNOWN, DeviceState.READY))
        assertNull(ShotSafetyAlert.resource(ExtractionState.ENDED_OBSERVED, DeviceState.READY))
    }
}
