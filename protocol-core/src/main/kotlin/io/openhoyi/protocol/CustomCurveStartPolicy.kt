package io.openhoyi.protocol

/** Complete, explicitly configured recipes only. The host must still match the current saved
 * document at dispatch. This does not register frames or relax the legacy pressure-only policy.
 */
object CustomCurveStartPolicy {
    fun permits(p: StartParameters): Boolean {
        if (p.temperatureC !in 75..105 || p.segmentCount !in 2..4 || p.slot != 7 ||
            p.preinfusionSeconds !in 0..127 || p.firstDurationSeconds !in 0..254 ||
            (p.firstSegmentFlowMode && !p.variableFlowLogic)) return false
        val targets = listOf(p.target1, p.target2, p.target3, p.target4)
        val water = listOf(p.firstFlowTenths, p.secondFlowTenths, p.thirdFlowTenths, p.fourthFlowTenths)
        if (targets.any { it !in 0..255 } || water.any { it !in 0..65535 } ||
            water.take(p.segmentCount).any { it !in 0..9990 || it % 10 != 0 }) return false
        if (p.variableFlowLogic) {
            if (p.segmentCount !in 2..4 || p.firstDurationSeconds == 255 ||
                (p.firstDurationSeconds == 0 && p.firstFlowTenths == 0)) return false
            if (water.take(p.segmentCount).any { it > 9990 } || water.take(p.segmentCount).drop(1).any { it == 0 }) return false
        } else if (p.pressureLogic || water.take(p.segmentCount).any { it < 10 }) return false
        for (index in 0 until p.segmentCount) {
            val flow = p.variableFlowLogic && (index > 0 || p.firstSegmentFlowMode)
            val range = when { flow -> 10..100; p.variableFlowLogic -> 5..100; index == 0 -> 5..120; else -> 10..120 }
            if (targets[index] !in range) return false
        }
        val assigned = water.take(p.segmentCount).sum() / 10
        return p.maximumWaterMl in 1..maxOf(assigned, 800)
    }
}
