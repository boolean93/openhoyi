package io.openhoyi.mobile

import io.openhoyi.protocol.CustomCurveStartPolicy
import io.openhoyi.protocol.StartParameters

/** The schema-3 path preserves all four wire slots and scale-stop compensation. */
object CustomCurveAdapter {
    fun profile(document: CustomCurveDocument, scaleConnected: Boolean, slot: Int = 7): CurveProfile? {
        val c = document.configuration ?: return CustomPressureCurveAdapter.profile(document, scaleConnected, slot)
        if (runCatching { document.validate() }.isFailure || slot != 7 ||
            document.targetHundredthsGram % 10 != 0 || c.compensationHundredthsGram % 10 != 0 ||
            document.stages.any { it.waterTenthsMl % 10 != 0 } ||
            (document.targetHundredthsGram > 0 && c.compensationHundredthsGram >= document.targetHundredthsGram)) return null
        val totalMl = document.totalWaterLimitMl ?: document.assignedWaterTenthsMl / 10
        val maximumMl = if (scaleConnected && document.targetHundredthsGram > 0)
            maxOf(totalMl, (document.targetHundredthsGram + 24) / 25) else totalMl
        val s = document.allStages
        val p = StartParameters(c.pressureLogic, c.variableFlowLogic, document.stages.size, slot,
            document.temperatureC, maximumMl, c.firstSegmentFlowMode, c.preinfusionSeconds,
            s[0].target, s[1].target, s[2].target, s[3].target, s[0].waterTenthsMl,
            c.firstDurationSeconds, s[1].waterTenthsMl, s[2].waterTenthsMl, s[3].waterTenthsMl)
        if (!CustomCurveStartPolicy.permits(p)) return null
        return CurveProfile(document.id, document.name,
            if (scaleConnected && document.targetHundredthsGram > 0) "达到目标重量后停止" else "由咖啡机按水量结束",
            if (scaleConnected) document.targetHundredthsGram else 0, p,
            if (scaleConnected) c.compensationHundredthsGram else 0, scaleConnected)
    }
}
