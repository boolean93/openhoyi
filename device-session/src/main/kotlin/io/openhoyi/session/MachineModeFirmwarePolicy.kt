package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange

/** 1.1.3 follows the official client command path; 1.1.4/1.1.5 additionally have
 * offline firmware write/readback/control evidence. Neither is hardware acceptance. */
object MachineModeFirmwarePolicy {
    fun permits(change: MachineSettingChange.LeverMode, firmware: CoffeeFirmware?): Boolean =
        !change.flow || change.pressure || firmware in setOf(CoffeeFirmware(1, 1, 3), CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5))
}
