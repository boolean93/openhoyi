package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test

class MachineModePendingPresentationTest {
    private fun pending(owns: Boolean = true, kind: MachineWriteRecoveryState.Kind? = MachineWriteRecoveryState.Kind.SETTING,
        original: String? = "AA", current: String? = "aa", connection: DeviceState = DeviceState.READY,
        change: MachineSettingChange? = MachineSettingChange.LeverMode(false,false),
        state: SettingsWriteTracker.State = SettingsWriteTracker.State.WRITING) =
        MachineModePendingPresentation.active(owns,kind,original,current,connection,change,state)
    @Test fun onlyLiveNormalModeWriteIsProgress() {
        for(state in SettingsWriteTracker.State.entries)
            assertEquals(state.toString(),state in setOf(SettingsWriteTracker.State.WRITING,SettingsWriteTracker.State.WAITING_READBACK),pending(state=state))
    }
    @Test fun restartOldOwnerOtherDeviceAndDisconnectKeepWarning() {
        assertFalse(pending(owns=false));assertFalse(pending(kind=null));assertFalse(pending(original=null))
        assertFalse(pending(current=null));assertFalse(pending(current="BB"))
        for(connection in DeviceState.entries) if(connection!=DeviceState.READY) assertFalse(pending(connection=connection))
    }
    @Test fun unrelatedWriteAndMissingChangeKeepWarning() {
        assertFalse(pending(change=null));assertFalse(pending(change=MachineSettingChange.BrewHeating(true)))
        for(kind in MachineWriteRecoveryState.Kind.entries) if(kind!=MachineWriteRecoveryState.Kind.SETTING) assertFalse(pending(kind=kind))
    }
}
