package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotRecoveryState
import org.junit.Assert.*
import org.junit.Test

class ShotSafetyAlertTest {
    @Test fun liveShotOwnersSuppressOnlyTheirOwnRecoveryReminder() {
        val address="AA:BB:CC:DD:EE:FF"
        val storage=object:ShotRecoveryState.Storage {
            var saved=ShotRecoveryState.Record(false,null)
            override fun read()=saved
            override fun write(record:ShotRecoveryState.Record):Boolean { saved=record;return true }
        }
        val recovery=ShotRecoveryState(storage)
        assertTrue(recovery.arm(address))
        val owner=requireNotNull(recovery.captureOwnership())
        val restart=R.string.machine_recovery_shot_restart
        fun reminder(shot:ExtractionState, coffee:DeviceState=DeviceState.READY,
            app:ShotRecoveryState.Ownership?=null, manual:ShotRecoveryState.Ownership?=null,
            newManualRecord:Boolean=false) =
                ShotSafetyAlert.recoveryResource(recovery,shot,coffee,app,manual,newManualRecord)

        for(shot in listOf(ExtractionState.STARTING,ExtractionState.RUNNING,ExtractionState.STOP_REQUESTED,
            ExtractionState.ENDED_OBSERVED)) assertNull(reminder(shot,app=owner))
        assertEquals(restart,reminder(ExtractionState.RUNNING,DeviceState.DISCONNECTED,app=owner))
        assertEquals(restart,reminder(ExtractionState.OUTCOME_UNKNOWN,app=owner))
        assertEquals(restart,reminder(ExtractionState.IDLE,app=owner))
        assertNull(reminder(ExtractionState.IDLE,manual=owner,newManualRecord=true))
        assertEquals(restart,reminder(ExtractionState.IDLE,manual=owner))
        assertEquals(restart,reminder(ExtractionState.IDLE,DeviceState.DISCONNECTED,manual=owner,newManualRecord=true))
        assertEquals(restart,reminder(ExtractionState.OUTCOME_UNKNOWN,manual=owner,newManualRecord=true))
        assertEquals(restart,ShotSafetyAlert.recoveryResource(ShotRecoveryState(storage),ExtractionState.RUNNING,
            DeviceState.READY,owner,null,false))
        assertTrue(recovery.clear(owner))
        assertNull(reminder(ExtractionState.IDLE))
    }

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
