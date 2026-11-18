package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.SettingsWriteTracker.State
import io.openhoyi.session.SleepNowTracker
import org.junit.Assert.*
import org.junit.Test

class TemperatureWritePresentationTest {
    @Test fun temperatureFeedbackNeverReplacesCoffeeStatus() {
        for(temperature in State.entries) {
            val status=TemperatureWritePresentation.machineStatus(State.IDLE,temperature)
            assertEquals(R.string.task_shell_machine_awake,TaskShellDeviceStatus.coffee(
                false,null,status,false,true,DeviceState.READY,0))
            assertEquals(R.string.device_state_disconnected,TaskShellDeviceStatus.coffee(
                false,null,status,false,true,DeviceState.DISCONNECTED,null))
            assertEquals(R.string.task_shell_machine_unavailable,TaskShellDeviceStatus.coffee(
                false,null,status,true,true,DeviceState.READY,0))
            assertEquals(R.string.task_shell_machine_extraction,TaskShellDeviceStatus.coffee(
                true,null,status,false,true,DeviceState.READY,0))
            assertEquals(R.string.sleep_status_unknown,TaskShellDeviceStatus.coffee(
                false,SleepNowTracker.State.UNKNOWN,status,false,true,DeviceState.READY,0))
        }
    }
    @Test fun readbackTimeoutDoesNotDisableControlsButWireWriteRemainsSingleFlight() {
        for(temperature in listOf(State.WAITING_READBACK,State.UNKNOWN,State.FAILED,State.RECONCILED))
            assertEquals(State.IDLE,TemperatureWritePresentation.controlState(State.IDLE,temperature))
        assertEquals(State.WRITING,TemperatureWritePresentation.controlState(State.IDLE,State.WRITING))
        assertEquals(State.UNKNOWN,TemperatureWritePresentation.controlState(State.UNKNOWN,State.UNKNOWN))
        assertEquals(State.WAITING_READBACK,TemperatureWritePresentation.machineStatus(State.WAITING_READBACK,State.UNKNOWN))
    }
}
