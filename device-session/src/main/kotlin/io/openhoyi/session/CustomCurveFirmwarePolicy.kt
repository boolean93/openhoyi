package io.openhoyi.session

import io.openhoyi.protocol.StartParameters

/** Temporary full-document flow and pressure-follow permit 1.1.3 on official-client compatibility evidence,
 * not a claim of firmware control/stop validation. Follow has a common pre-gate in DeviceSession;
 * persistent writes use the same official field compatibility, with independent transaction guards.
 */
object CustomCurveFirmwarePolicy {
    fun requiresReviewedFlowProfile(p: StartParameters): Boolean = p.variableFlowLogic && (!p.pressureLogic || p.firstSegmentFlowMode)
    fun isPressureFollow(p: StartParameters): Boolean = p.pressureLogic && !p.variableFlowLogic
    fun permits(p: StartParameters, firmware: CoffeeFirmware?): Boolean = when {
        isPressureFollow(p) -> firmware in setOf(CoffeeFirmware(1, 1, 3), CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5))
        requiresReviewedFlowProfile(p) -> firmware in setOf(CoffeeFirmware(1, 1, 3), CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5))
        else -> true
    }
    /** Mode/version gate only; DeviceSession separately validates parameters and transaction state. */
    fun permitsStoredRecipe(p: StartParameters, firmware: CoffeeFirmware?): Boolean =
        firmware in setOf(CoffeeFirmware(1, 1, 3), CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5)) &&
            (!isPressureFollow(p) || io.openhoyi.protocol.CustomCurveStartPolicy.permits(p))
}
