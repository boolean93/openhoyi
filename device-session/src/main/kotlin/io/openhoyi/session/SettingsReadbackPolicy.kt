package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange

/** Narrow eligibility for authenticated 0x83 refresh, never permission to replay a control. */
object SettingsReadbackPolicy {
    fun isBoiler(change: MachineSettingChange?): Boolean =
        change is MachineSettingChange.BrewHeating || change is MachineSettingChange.SteamHeating
    fun supports(change: MachineSettingChange?): Boolean = isBoiler(change) || change is MachineSettingChange.LeverMode
}
