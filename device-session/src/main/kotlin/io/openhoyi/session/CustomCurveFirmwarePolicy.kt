package io.openhoyi.session

import io.openhoyi.protocol.StartParameters

/** New full-document flow combinations and temporary pressure-follow execution require
 * explicitly reviewed 1.1.4/1.1.5 profiles. Follow also has a common pre-gate in DeviceSession;
 * persistent follow recipes remain disallowed independently of this temporary-start policy.
 */
object CustomCurveFirmwarePolicy {
    fun requiresReviewedFlowProfile(p: StartParameters): Boolean = p.variableFlowLogic && (!p.pressureLogic || p.firstSegmentFlowMode)
    fun isPressureFollow(p: StartParameters): Boolean = p.pressureLogic && !p.variableFlowLogic
    fun permits(p: StartParameters, firmware: CoffeeFirmware?): Boolean = when {
        isPressureFollow(p) -> firmware in setOf(CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5))
        requiresReviewedFlowProfile(p) -> firmware in setOf(CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5))
        else -> true
    }
}
