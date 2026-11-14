package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.DeviceState
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.SettingsWriteTracker

/** Display-only: durable protection and all command gates remain unchanged. */
internal object MachineModePendingPresentation {
    fun active(ownsWrite: Boolean, kind: MachineWriteRecoveryState.Kind?, originalAddress: String?,
        currentAddress: String?, connection: DeviceState, change: MachineSettingChange?,
        state: SettingsWriteTracker.State): Boolean =
        ownsWrite && kind == MachineWriteRecoveryState.Kind.SETTING && originalAddress != null &&
            currentAddress?.equals(originalAddress, ignoreCase = true) == true && connection == DeviceState.READY &&
            change is MachineSettingChange.LeverMode &&
            state in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
}
