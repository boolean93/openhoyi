package io.openhoyi.mobile

import io.openhoyi.session.SettingsWriteTracker

/** Readback feedback belongs beside the setting, not in the coffee connection badge. */
internal object TemperatureWritePresentation {
    fun feedbackState(machineSetting:SettingsWriteTracker.State,temperature:SettingsWriteTracker.State,
        lastWasTemperature:Boolean,restartTemperaturePending:Boolean,hasCurrentMachineSetting:Boolean):SettingsWriteTracker.State =
        if(lastWasTemperature || restartTemperaturePending && !hasCurrentMachineSetting) temperature else machineSetting
    fun controlState(machineSetting:SettingsWriteTracker.State,temperature:SettingsWriteTracker.State):SettingsWriteTracker.State =
        if(temperature == SettingsWriteTracker.State.WRITING) SettingsWriteTracker.State.WRITING else machineSetting
    fun machineStatus(machineSetting:SettingsWriteTracker.State,temperature:SettingsWriteTracker.State):SettingsWriteTracker.State = machineSetting
}
