package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation.State
import io.openhoyi.session.DeviceState
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.protocol.MachineSettingChange
import org.junit.Assert.*
import org.junit.Test

class PreheatStatusPresentationTest {
    private fun status(preparation:State,atTarget:Boolean=false,connection:DeviceState=DeviceState.READY,
        unavailable:Boolean=false,setting:SettingsWriteTracker.State?=null,sleep:SleepNowTracker.State?=null)=
        TaskShellDeviceStatus.coffee(false,sleep,setting,unavailable,true,connection,0,
            preparation=preparation,preparationAtTarget=atTarget)

    @Test fun normalProgressIsNotDisconnectionAndLatchedReadyNeedsCurrentTemperature() {
        for(state in listOf(State.WRITING,State.WAITING_TEMP))
            assertEquals(R.string.auto_preheat_heating,status(state))
        assertEquals(R.string.task_shell_machine_preheat_ready,status(State.READY,true))
        assertEquals(R.string.home_boiler_preheat_occupied,status(State.READY,false))
        assertEquals(R.string.device_state_disconnected,status(State.READY,true,DeviceState.DISCONNECTED))
    }
    @Test fun higherPriorityUnknownAlarmAndAbnormalPreparationRemainVisible() {
        assertEquals(R.string.task_shell_machine_unknown,status(State.READY,true,setting=SettingsWriteTracker.State.UNKNOWN))
        assertEquals(R.string.sleep_status_unknown,status(State.READY,true,sleep=SleepNowTracker.State.UNKNOWN))
        assertEquals(R.string.task_shell_machine_unavailable,status(State.READY,true,unavailable=true))
        for(state in listOf(State.UNKNOWN,State.FAILED,State.CANCELLING,State.CANCEL_WRITTEN))
            assertEquals(R.string.task_shell_machine_unavailable,status(state,true))
    }
    @Test fun boilerReasonNeedsHealthyIdleAndCannotOverridePendingBoilerRecovery() {
        for(state in listOf(State.WRITING,State.WAITING_TEMP,State.READY)) {
            assertTrue(PreheatStatusPresentation.boilerOccupied(state,true,true,false))
            assertFalse(PreheatStatusPresentation.boilerOccupied(state,false,true,false))
            assertFalse(PreheatStatusPresentation.boilerOccupied(state,true,false,false))
            assertFalse(PreheatStatusPresentation.boilerOccupied(state,true,true,true))
        }
        assertFalse(PreheatStatusPresentation.boilerOccupied(State.UNKNOWN,true,true,false))
        assertEquals(R.string.home_boiler_preheat_occupied,
            HomeBoilerSwitchPresentation.label(true,true,null,null,preheatOccupied=true))
        assertEquals(R.string.home_boiler_refresh,HomeBoilerSwitchPresentation.label(true,true,
            MachineSettingChange.BrewHeating(false),SettingsWriteTracker.State.UNKNOWN,preheatOccupied=true))
    }
}
