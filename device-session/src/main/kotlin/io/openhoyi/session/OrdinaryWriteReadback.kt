package io.openhoyi.session

import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepPart

/** Host owns serials/freshness; typed feedback is emitted in protocol-observation order. */
object OrdinaryWriteReadback {
    enum class Event { CONFIRMED, RECONCILED, CLEAR_FAILED }

    fun settings(tracker:SettingsWriteTracker,recovery:MachineWriteRecoveryState,
        serial:Long,frame:Settings,publish:(Event)->Unit):Boolean {
        val previous=tracker.state
        if(tracker.observe(serial,frame)) publish(Event.CONFIRMED)
        else if(previous==SettingsWriteTracker.State.UNKNOWN && tracker.state==SettingsWriteTracker.State.RECONCILED)
            publish(Event.RECONCILED)
        return clearIfConfirmed(tracker.state==SettingsWriteTracker.State.CONFIRMED,
            MachineWriteRecoveryState.Kind.SETTING,recovery,publish)
    }

    fun sleep(tracker:SleepNowTracker,recovery:MachineWriteRecoveryState,
        serial:Long,sleepState:Int,publish:(Event)->Unit):Boolean {
        val previous=tracker.state
        if(tracker.observe(serial,sleepState)) publish(Event.CONFIRMED)
        else if(previous==SleepNowTracker.State.UNKNOWN && tracker.state==SleepNowTracker.State.RECONCILED)
            publish(Event.RECONCILED)
        return clearIfConfirmed(tracker.state==SleepNowTracker.State.CONFIRMED,
            MachineWriteRecoveryState.Kind.SLEEP_NOW,recovery,publish)
    }

    fun schedule(tracker:SleepScheduleWriteTracker,recovery:MachineWriteRecoveryState,
        fresh:Boolean,firstSerial:Long,secondSerial:Long,first:SleepPart?,second:SleepPart?,
        publish:(Event)->Unit):Boolean {
        if(fresh && tracker.observe(firstSerial,secondSerial,first,second)) {
            if(tracker.state==SleepScheduleWriteTracker.State.CONFIRMED) publish(Event.CONFIRMED)
            else publish(Event.RECONCILED)
        }
        return clearIfConfirmed(tracker.state==SleepScheduleWriteTracker.State.CONFIRMED,
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE,recovery,publish)
    }

    /** True requests a host notification refresh, even when the synchronous clear fails. */
    private fun clearIfConfirmed(confirmed:Boolean,kind:MachineWriteRecoveryState.Kind,
        recovery:MachineWriteRecoveryState,publish:(Event)->Unit):Boolean {
        if(!confirmed || recovery.kind!=kind)return false
        if(!recovery.clear())publish(Event.CLEAR_FAILED)
        return true
    }
}
