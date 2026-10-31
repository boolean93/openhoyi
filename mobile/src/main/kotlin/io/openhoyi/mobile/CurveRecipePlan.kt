package io.openhoyi.mobile

/** Read-only metadata for a library preview. Water is an amount, never a flow target. */
internal data class CurveRecipePlan(
    val preview: CurveStageParameters.Preview,
    val stageWaterTenthsMl: List<Int>,
    val totalWaterLimitMl: Int?,
    val preinfusionSeconds: Int?,
    val firstStageDurationSettingSeconds: Int?,
    val endMode: String?,
    val targetWeightHundredthsGram: Int?,
    val advice: String?,
) {
    val assignedWaterTenthsMl: Int get() = stageWaterTenthsMl.sum()

    companion object {
        fun from(item: CurveLibraryItem): CurveRecipePlan {
            val preview = CurveStageParameters.preview(item)
            item.factoryCurve?.let { curve ->
                return CurveRecipePlan(preview, curve.segmentFlowMl.take(curve.segmentCount).map { it * 10 },
                    curve.flowMl, curve.preinfusionSeconds, curve.firstDurationSeconds,
                    null, curve.weightTenthsGram * 10, curve.tips)
            }
            item.controlProfile?.let { profile ->
                val p = profile.parameters
                return CurveRecipePlan(preview,
                    listOf(p.firstFlowTenths, p.secondFlowTenths, p.thirdFlowTenths, p.fourthFlowTenths)
                        .take(p.segmentCount), p.maximumWaterMl, p.preinfusionSeconds, p.firstDurationSeconds,
                    profile.endMode, profile.targetHundredthsGram, null)
            }
            item.customDocument?.let { document ->
                return CurveRecipePlan(preview, document.stages.map { it.waterTenthsMl }, null, null, null,
                    null, document.targetHundredthsGram, null)
            }
            return CurveRecipePlan(preview, emptyList(), null, null, null, null, null, null)
        }
    }
}
