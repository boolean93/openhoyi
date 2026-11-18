package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange

/** Narrow eligibility for authenticated 0x83 refresh, never permission to replay a control. */
object SettingsReadbackPolicy {
    fun isBoiler(change: MachineSettingChange?): Boolean =
        change is MachineSettingChange.BrewHeating || change is MachineSettingChange.SteamHeating
    fun isTemperature(change: MachineSettingChange?): Boolean = when (change) {
        is MachineSettingChange.BrewTemperature, is MachineSettingChange.BrewCompensation,
        is MachineSettingChange.SteamTemperature, is MachineSettingChange.StandbyTemperature,
        is MachineSettingChange.StandbyDelay -> true
        else -> false
    }
    // These writes do not reliably emit 0x83 on their own. Reuse the bounded,
    // authenticated refresh; do not extend boiler-only reconnect/reconciliation.
    fun supports(change: MachineSettingChange?): Boolean =
        isBoiler(change) || change is MachineSettingChange.LeverMode || isTemperature(change)
}
