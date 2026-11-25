package io.openhoyi.mobile

/** Frozen readings from this cup only. Missing scale evidence must not fall back to pump water. */
internal data class BrewCompletionSummary(
    val feedback: BrewFeedbackResult,
    val averageScaleFlowGramsPerSecond: Double? = null,
    val outputGrams: Double? = null,
    val averagePressureBar: Double? = null,
    val endCupCount: Int? = null,
) {
    companion object {
        fun create(
            feedback: BrewFeedbackResult,
            points: List<ShotPoint>,
            terminalBeverageHundredthsGram: Int? = null,
            endCupCount: Int? = null,
        ): BrewCompletionSummary {
            val brewing = points.filter { it.brewing == true }
            return BrewCompletionSummary(
                feedback = feedback,
                averageScaleFlowGramsPerSecond = brewing.mapNotNull { it.scaleFlowHundredths }.average()
                    .takeIf { it.isFinite() }?.div(100.0),
                outputGrams = terminalBeverageHundredthsGram?.div(100.0),
                averagePressureBar = brewing.map { it.pressureTenthsBar }.average().takeIf { it.isFinite() }?.div(10.0),
                endCupCount = endCupCount,
            )
        }
    }
}
