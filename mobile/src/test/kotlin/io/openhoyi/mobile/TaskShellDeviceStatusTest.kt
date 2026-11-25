package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotStartObservation
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TaskShellDeviceStatusTest {
    @Test fun unknownResultCannotBeHiddenByFreshAwakeOrAsleepTelemetry() {
        for (raw in listOf(0, 1)) {
            assertEquals(R.string.sleep_status_unknown, TaskShellDeviceStatus.coffee(
                false, SleepNowTracker.State.UNKNOWN, null, false, true, DeviceState.READY, raw))
            assertEquals(R.string.task_shell_machine_unknown, TaskShellDeviceStatus.coffee(
                false, null, SettingsWriteTracker.State.UNKNOWN, false, true, DeviceState.READY, raw))
        }
    }

    @Test fun appExtractionUsesSameShotEvidenceAndKeepsManualAndSafetyPrecedence() {
        fun status(shot:ExtractionState, observation:ShotStartObservation?=null,
            manual:Boolean=false, connection:DeviceState=DeviceState.READY, alarm:Boolean=false,
            requiresStartObservation:Boolean=true, legacyFlowObserved:Boolean=false) = TaskShellDeviceStatus.coffee(
                active=manual,sleep=null,setting=null,unavailable=false,running=true,connection=connection,sleepRaw=0,
                alarmBlocked=alarm,shot=shot,shotObservation=observation,
                requiresStartObservation=requiresStartObservation,legacyFlowObserved=legacyFlowObserved)

        assertEquals(R.string.extraction_state_starting,status(ExtractionState.STARTING))
        assertEquals(R.string.extraction_state_starting,status(ExtractionState.RUNNING,ShotStartObservation.WAITING_FOR_MACHINE))
        assertEquals(R.string.task_shell_machine_extraction,status(ExtractionState.RUNNING,ShotStartObservation.MACHINE_EXTRACTION_OBSERVED))
        assertEquals(R.string.extraction_state_stopping,status(ExtractionState.STOP_REQUESTED))
        assertEquals(R.string.extraction_state_unknown,status(ExtractionState.RUNNING,ShotStartObservation.NOT_CONFIRMED))
        assertEquals(R.string.task_shell_machine_extraction,status(ExtractionState.IDLE,manual=true))
        assertEquals(R.string.device_state_disconnected,status(ExtractionState.RUNNING,connection=DeviceState.DISCONNECTED))
        assertEquals(R.string.task_shell_machine_unavailable,status(ExtractionState.RUNNING,alarm=true))
        assertEquals(R.string.task_shell_machine_extraction,status(ExtractionState.RUNNING,
            requiresStartObservation=false,legacyFlowObserved=true))
    }

    @Test fun routineSettingsPhasesDoNotReplaceCurrentMachineStatus() {
        for (phase in listOf(SettingsWriteTracker.State.WRITING,SettingsWriteTracker.State.WAITING_READBACK)) {
            assertEquals(R.string.task_shell_machine_awake,TaskShellDeviceStatus.coffee(
                false,null,phase,false,true,DeviceState.READY,0))
            assertEquals(R.string.task_shell_machine_asleep,TaskShellDeviceStatus.coffee(
                false,null,phase,false,true,DeviceState.READY,1))
        }
    }

    @Test fun everyConnectionPhaseBlocksRepeatedReconnect() {
        for (state in listOf(DeviceState.CONNECTING, DeviceState.DISCOVERING,
            DeviceState.SUBSCRIBING, DeviceState.INITIALIZING, DeviceState.SYNCHRONIZING)) {
            assertFalse(TaskShellDeviceStatus.canReconnect(state, false))
            assertEquals(R.string.device_state_connecting, TaskShellDeviceStatus.scale(state, true))
        }
        assertFalse(TaskShellDeviceStatus.canReconnect(DeviceState.DISCONNECTED, true))
    }

    @Test fun suppressionOnlyDescribesAnActuallyDisconnectedScale() {
        assertEquals(R.string.task_shell_scale_manually_disconnected,
            TaskShellDeviceStatus.scale(DeviceState.DISCONNECTED, true))
        assertEquals(R.string.device_state_disconnected,
            TaskShellDeviceStatus.scale(DeviceState.DISCONNECTED, false))
        for (state in DeviceState.entries.filter { it != DeviceState.DISCONNECTED }) {
            assertEquals(TaskShellDeviceStatus.scale(state, false), TaskShellDeviceStatus.scale(state, true))
        }
    }

    @Test fun connectionAndMissingSleepEvidenceCannotClaimAwake() {
        assertEquals(R.string.device_state_connecting, TaskShellDeviceStatus.coffee(
            false, null, null, false, true, DeviceState.CONNECTING, 0))
        assertEquals(R.string.task_shell_machine_unconfirmed, TaskShellDeviceStatus.coffee(
            false, null, null, false, true, DeviceState.READY, null))
    }
    @Test fun realDisconnectAndAlarmPrecedeOldSettingsFeedbackAndRecoverOnReconnect() {
        for(setting in listOf(SettingsWriteTracker.State.WRITING,SettingsWriteTracker.State.WAITING_READBACK,
            SettingsWriteTracker.State.UNKNOWN)) {
            assertEquals(R.string.device_state_disconnected,TaskShellDeviceStatus.coffee(
                false,null,setting,true,true,DeviceState.DISCONNECTED,null))
            assertEquals(R.string.device_state_connecting,TaskShellDeviceStatus.coffee(
                false,null,setting,true,true,DeviceState.CONNECTING,null))
            assertEquals(R.string.task_shell_machine_unavailable,TaskShellDeviceStatus.coffee(
                false,null,setting,true,true,DeviceState.READY,0,alarmBlocked=true))
        }
        assertEquals(R.string.device_state_disconnected,TaskShellDeviceStatus.coffee(
            true,null,null,false,true,DeviceState.DISCONNECTED,null))
        assertEquals(R.string.task_shell_machine_awake,TaskShellDeviceStatus.coffee(
            false,null,SettingsWriteTracker.State.CONFIRMED,false,true,DeviceState.READY,0))
    }

}
