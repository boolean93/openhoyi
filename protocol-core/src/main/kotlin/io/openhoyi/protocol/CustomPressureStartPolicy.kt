package io.openhoyi.protocol

/** Parameter subset compared with the independent legacy encoder, not a generic wire permit.
 * The product host must separately authorize the current stored recipe on every dispatch.
 */
object CustomPressureStartPolicy {
    fun permits(p:StartParameters):Boolean {
        if(p.pressureLogic || p.variableFlowLogic || p.firstSegmentFlowMode ||
            p.preinfusionSeconds!=0 || p.firstDurationSeconds!=0 || p.temperatureC !in 75..105 ||
            p.segmentCount !in 1..4 || (p.slot !in 1..5 && p.slot!=7))return false
        val targets=listOf(p.target1,p.target2,p.target3,p.target4)
        val water=listOf(p.firstFlowTenths,p.secondFlowTenths,p.thirdFlowTenths,p.fourthFlowTenths)
        if(targets.take(p.segmentCount).any {it !in 0..120} ||
            water.take(p.segmentCount).any {it !in 10..65530 || it%10!=0} ||
            targets.drop(p.segmentCount).any {it!=0} || water.drop(p.segmentCount).any {it!=0})return false
        val totalMl=water.sum()/10
        // Legacy APKs store an independent cumulative-water threshold, including below the
        // stage allocation. Firmware consumes it separately; keep the existing upper bound.
        return p.maximumWaterMl in 1..maxOf(totalMl,800)
    }
}
