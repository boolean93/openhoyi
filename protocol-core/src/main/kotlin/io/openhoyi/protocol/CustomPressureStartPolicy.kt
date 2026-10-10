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
        // The only supported expansion is the legacy 4× target-weight ceiling (max 200g).
        return p.maximumWaterMl in totalMl..maxOf(totalMl,800)
    }
}
