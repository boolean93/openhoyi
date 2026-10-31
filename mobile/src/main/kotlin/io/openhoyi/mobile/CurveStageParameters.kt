package io.openhoyi.mobile

/** Pressure previews never reinterpret unverified flow-mode targets as bar. */
internal object CurveStageParameters {
    enum class Unit { PRESSURE_BAR, FLOW_ML_PER_SECOND, RAW_UNKNOWN }
    data class Stage(val target: Int, val unit: Unit)
    data class Preview(val stages: List<Stage>) {
        val renderable: Boolean get() = stages.isNotEmpty() && stages.all { stage -> when (stage.unit) {
            Unit.PRESSURE_BAR -> stage.target in 0..255
            Unit.FLOW_ML_PER_SECOND -> stage.target in 0..255
            Unit.RAW_UNKNOWN -> false
        } }
        val mixed: Boolean get() = stages.map { it.unit }.distinct().size > 1
        val hasFlow: Boolean get() = stages.any { it.unit == Unit.FLOW_ML_PER_SECOND }
        val hasPressure: Boolean get() = stages.any { it.unit == Unit.PRESSURE_BAR }
        val pressureAxisMax: Float get() {
            val max = stages.filter { it.unit == Unit.PRESSURE_BAR }.maxOfOrNull { it.target } ?: 0
            return listOf(12f, 16f, 20f, 30f).first { max / 10f <= it }
        }
        val flowAxisMax: Float get() {
            val max = stages.filter { it.unit == Unit.FLOW_ML_PER_SECOND }.maxOfOrNull { it.target } ?: 0
            return listOf(6f, 10f, 20f, 30f).first { max / 10f <= it }
        }
    }
    data class SharedScale(val pressureMaxBar: Float, val flowMaxMlPerSecond: Float) {
        companion object {
            fun from(items: List<CurveLibraryItem>): SharedScale {
                val stages = items.flatMap { preview(it).stages }
                val pressure = stages.filter { it.unit == Unit.PRESSURE_BAR && it.target in 0..255 }
                    .maxOfOrNull { it.target } ?: 0
                val flow = stages.filter { it.unit == Unit.FLOW_ML_PER_SECOND && it.target in 0..255 }
                    .maxOfOrNull { it.target } ?: 0
                return SharedScale(listOf(12f, 16f, 20f, 30f).first { pressure / 10f <= it },
                    listOf(6f, 10f, 20f, 30f).first { flow / 10f <= it })
            }
        }
    }

    /** Wire target slots are tenths; per-stage mode, never pressureLogic, supplies the unit. */
    fun preview(item: CurveLibraryItem): Preview {
        item.customDocument?.let { document ->
            val unit = if (document.controlMode == CustomCurveDocument.ControlMode.PRESSURE)
                Unit.PRESSURE_BAR else Unit.RAW_UNKNOWN
            return Preview(document.stages.map { Stage(it.target, unit) })
        }
        item.factoryCurve?.let { curve ->
            return Preview(curve.targets.take(curve.segmentCount).mapIndexed { index, target ->
                Stage(target, if (curve.variableFlowLogic && index > 0) Unit.FLOW_ML_PER_SECOND else Unit.PRESSURE_BAR)
            })
        }
        item.controlProfile?.parameters?.let { parameters ->
            val targets = listOf(parameters.target1, parameters.target2, parameters.target3, parameters.target4)
            return Preview(targets.take(parameters.segmentCount).mapIndexed { index, target ->
                val flow = parameters.variableFlowLogic && (index > 0 || parameters.firstSegmentFlowMode)
                Stage(target, if (flow) Unit.FLOW_ML_PER_SECOND else Unit.PRESSURE_BAR)
            })
        }
        return Preview(emptyList())
    }

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
