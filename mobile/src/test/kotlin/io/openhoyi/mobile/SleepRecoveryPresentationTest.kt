package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.SleepNowTracker.State
import org.junit.Assert.*
import org.junit.Test

class SleepRecoveryPresentationTest {
    @Test fun requestStateWinsOverOldAwakeTelemetry(){
        assertEquals(R.string.sleep_status_writing,SleepPresentation.status(State.WRITING,0))
        assertEquals(R.string.sleep_status_waiting,SleepPresentation.status(State.WAITING_ASLEEP,0))
        for(raw in listOf(0,1,null))assertEquals(R.string.sleep_status_unknown,SleepPresentation.status(State.UNKNOWN,raw))
    }
    @Test fun sleepNeedsFreshEvidenceOrExplicitlyQualifiedSameSessionHistory(){
        assertEquals(R.string.task_shell_machine_asleep,SleepPresentation.status(State.CONFIRMED,1))
        assertEquals(R.string.sleep_status_last_confirmed,SleepPresentation.status(State.CONFIRMED,null,1))
        assertEquals(R.string.sleep_status_last_confirmed_hint,SleepPresentation.hint(State.CONFIRMED,null,1))
        assertEquals(R.string.task_shell_machine_awake,SleepPresentation.status(State.CONFIRMED,0))
        assertEquals(R.string.task_shell_machine_unconfirmed,SleepPresentation.status(State.CONFIRMED,null,0))
        assertEquals(R.string.task_shell_machine_unconfirmed,SleepPresentation.status(State.CONFIRMED,null,null))
        assertNull(SleepPresentation.hint(State.CONFIRMED,null,0))
    }
    @Test fun helpAppearsAtEightSecondsOrSecondFailureAndSurvivesRetry(){
        val p=CoffeeRecoveryPresentation();p.observe(DeviceState.CONNECTING,0)
        assertNull(p.hint(DeviceState.SYNCHRONIZING,7999))
        assertEquals(R.string.coffee_connection_no_state_hint,p.hint(DeviceState.SYNCHRONIZING,8000))
        p.observe(DeviceState.FAILED,8001);p.observe(DeviceState.CONNECTING,9000)
        assertEquals(R.string.coffee_connection_recovery_hint,p.hint(DeviceState.CONNECTING,9000))
        p.observe(DeviceState.READY,9001);assertNull(p.hint(DeviceState.READY,9001))
        p.observe(DeviceState.CONNECTING,10_000);p.observe(DeviceState.FAILED,10_100);p.observe(DeviceState.FAILED,10_200)
        assertEquals(R.string.coffee_connection_recovery_hint,p.hint(DeviceState.FAILED,10_200))
        p.reset();assertNull(p.hint(DeviceState.DISCONNECTED,10_300))
    }
}
