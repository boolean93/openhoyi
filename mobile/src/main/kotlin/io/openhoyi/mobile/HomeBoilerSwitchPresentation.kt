package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.SettingsWriteTracker

/** Pending intent belongs on its own button, never in the reported heating state. */
internal object HomeBoilerSwitchPresentation {
    fun localProgress(ownsOriginal:Boolean,pending:MachineSettingChange?,state:SettingsWriteTracker.State,
        reconnecting:Boolean,manualBusy:Boolean):Boolean = ownsOriginal &&
        (pending is MachineSettingChange.BrewHeating || pending is MachineSettingChange.SteamHeating) &&
        (state in setOf(SettingsWriteTracker.State.WRITING,SettingsWriteTracker.State.WAITING_READBACK) ||
            reconnecting || manualBusy)

    fun label(brew:Boolean,reportedEnabled:Boolean?,pending:MachineSettingChange?,state:SettingsWriteTracker.State?,recovering:Boolean=false,reconnecting:Boolean=false,preheatOccupied:Boolean=false):Int {
        val target=when {
            brew && pending is MachineSettingChange.BrewHeating -> pending.enabled
            !brew && pending is MachineSettingChange.SteamHeating -> pending.enabled
            else -> null
        }
        if(target!=null) {
            if(reconnecting)return R.string.home_boiler_reconnecting
            if(recovering)return R.string.home_boiler_syncing
            if(state in setOf(SettingsWriteTracker.State.WRITING,SettingsWriteTracker.State.WAITING_READBACK))
                return if(target) R.string.home_boiler_turning_on else R.string.home_boiler_turning_off
            if(state==SettingsWriteTracker.State.UNKNOWN)return R.string.home_boiler_refresh
        }
        if(preheatOccupied)return R.string.home_boiler_preheat_occupied
        if(target!=null && state==SettingsWriteTracker.State.RECONCILED)
            return if(target) R.string.home_boiler_retry_on else R.string.home_boiler_retry_off
        return if(reportedEnabled==true) R.string.setting_off else R.string.setting_on
    }
}
