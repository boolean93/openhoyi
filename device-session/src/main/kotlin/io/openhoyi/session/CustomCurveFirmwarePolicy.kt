package io.openhoyi.session

import io.openhoyi.protocol.StartParameters

/** New full-document flow combinations not observed on 1.1.3 require an explicitly reviewed 1.1.4 or 1.1.5 profile.
 * Existing factory/captured frame proof and legacy pressure permits are separate paths.
 */
object CustomCurveFirmwarePolicy {
    fun requiresReviewedFlowProfile(p: StartParameters): Boolean = p.variableFlowLogic && (!p.pressureLogic || p.firstSegmentFlowMode)
    fun permits(p: StartParameters, firmware: CoffeeFirmware?): Boolean = !requiresReviewedFlowProfile(p) || firmware in setOf(CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5))
}
