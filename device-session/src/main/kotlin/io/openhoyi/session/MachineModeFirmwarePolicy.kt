package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange

/** Fourth global mode is reviewed against the exact 1.1.4/1.1.5 write/readback/control chain. */
object MachineModeFirmwarePolicy {
    fun permits(change: MachineSettingChange.LeverMode, firmware: CoffeeFirmware?): Boolean =
        !change.flow || change.pressure || firmware in setOf(CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5))
}
