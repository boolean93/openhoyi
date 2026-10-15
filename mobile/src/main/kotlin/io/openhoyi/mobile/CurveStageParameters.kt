package io.openhoyi.mobile

/** Pressure previews never reinterpret unverified flow-mode targets as bar. */
internal object CurveStageParameters {
    fun pressureTargets(item:CurveLibraryItem):List<Int> {
        val values=item.customDocument?.let {
            if(it.controlMode==CustomCurveDocument.ControlMode.PRESSURE) it.stages.map {stage->stage.target} else emptyList()
        } ?: item.factoryCurve?.let {
            if(it.variableFlowLogic) emptyList() else it.targets.take(it.segmentCount)
        } ?: item.controlProfile?.parameters?.let {
            if(it.variableFlowLogic || it.firstSegmentFlowMode) emptyList()
            else listOf(it.target1,it.target2,it.target3,it.target4).take(it.segmentCount)
        }.orEmpty()
        return values.takeIf {it.size in 1..4 && it.all {target->target in 0..120}}.orEmpty()
    }
    fun pressureFraction(targetTenthsBar:Int):Float {
        require(targetTenthsBar in 0..120)
        return targetTenthsBar/120f
    }
}
