package io.openhoyi.session

import io.openhoyi.protocol.StartParameters

/** New full-document flow combinations not observed on 1.1.3 require the exact 1.1.4 profile.
 * Existing factory/captured frame proof and legacy pressure permits are separate paths.
 */
object CustomCurveFirmwarePolicy {
    fun requires114(p: StartParameters): Boolean = p.variableFlowLogic && (!p.pressureLogic || p.firstSegmentFlowMode)
    fun permits(p: StartParameters, firmware: CoffeeFirmware?): Boolean = !requires114(p) || firmware == CoffeeFirmware(1, 1, 4)
}
