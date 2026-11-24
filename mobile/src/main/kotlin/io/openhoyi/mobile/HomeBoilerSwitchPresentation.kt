package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.SettingsWriteTracker

/** Pending intent and the reported heating state are presented separately. */
internal object HomeBoilerSwitchPresentation {
    fun localProgress(ownsOriginal:Boolean,pending:MachineSettingChange?,state:SettingsWriteTracker.State,
        reconnecting:Boolean,manualBusy:Boolean):Boolean = ownsOriginal &&
        (pending is MachineSettingChange.BrewHeating || pending is MachineSettingChange.SteamHeating) &&
        (state in setOf(SettingsWriteTracker.State.WRITING,SettingsWriteTracker.State.WAITING_READBACK) ||
            reconnecting || manualBusy)

    private fun target(brew: Boolean, pending: MachineSettingChange?): Boolean? = when {
        brew && pending is MachineSettingChange.BrewHeating -> pending.enabled
        !brew && pending is MachineSettingChange.SteamHeating -> pending.enabled
        else -> null
    }

    fun pendingAppearance(brew: Boolean, pending: MachineSettingChange?, state: SettingsWriteTracker.State?,
        ownsPending: Boolean, recoveryRunning: Boolean = false): Boolean =
        ownsPending && target(brew, pending) != null &&
            (state == SettingsWriteTracker.State.WRITING || state == SettingsWriteTracker.State.WAITING_READBACK ||
                state == SettingsWriteTracker.State.UNKNOWN && recoveryRunning)

    fun showInline(label: Int): Boolean = when (label) {
        R.string.home_boiler_refresh, R.string.home_setting_failed,
        R.string.home_boiler_retry_on, R.string.home_boiler_retry_off,
        R.string.home_boiler_preheat_occupied, R.string.home_boiler_schedule_saving -> true
        else -> false
    }

    fun label(brew:Boolean,reportedEnabled:Boolean?,pending:MachineSettingChange?,state:SettingsWriteTracker.State?,recovering:Boolean=false,reconnecting:Boolean=false,preheatOccupied:Boolean=false, admissionReason:Int?=null):Int {
        val target = target(brew, pending)
        if(target!=null) {
            if(reconnecting)return R.string.home_boiler_reconnecting
            if(recovering)return R.string.home_boiler_syncing
            if(state==SettingsWriteTracker.State.WRITING || state==SettingsWriteTracker.State.WAITING_READBACK)
                return if(target) R.string.home_boiler_turning_on else R.string.home_boiler_turning_off
            if(state==SettingsWriteTracker.State.UNKNOWN)return R.string.home_boiler_refresh
            if(state==SettingsWriteTracker.State.FAILED)return R.string.home_setting_failed
        }
        if(preheatOccupied)return R.string.home_boiler_preheat_occupied
        if(admissionReason!=null)return admissionReason
        if(target!=null && state==SettingsWriteTracker.State.RECONCILED)
            return if(target) R.string.home_boiler_retry_on else R.string.home_boiler_retry_off
        return when (reportedEnabled) {
            true -> R.string.setting_on
            false -> R.string.setting_off
            null -> R.string.settings_unknown
        }
    }
}
