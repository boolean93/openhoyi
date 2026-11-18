package io.openhoyi.mobile

import io.openhoyi.session.SettingsWriteTracker

/** Readback feedback belongs beside the setting, not in the coffee connection badge. */
internal object TemperatureWritePresentation {
    fun controlState(machineSetting:SettingsWriteTracker.State,temperature:SettingsWriteTracker.State):SettingsWriteTracker.State =
        if(temperature == SettingsWriteTracker.State.WRITING) SettingsWriteTracker.State.WRITING else machineSetting
    fun machineStatus(machineSetting:SettingsWriteTracker.State,temperature:SettingsWriteTracker.State):SettingsWriteTracker.State = machineSetting
}
