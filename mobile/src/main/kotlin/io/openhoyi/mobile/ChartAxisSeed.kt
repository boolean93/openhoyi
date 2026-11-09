package io.openhoyi.mobile

/**
 * Recipe-derived target ranges for the realtime Y axes. Display-only: they come from the selected
 * App recipe (never a machine readback) and are used as the preferred expansion target, so the
 * first expansion usually lands on the final scale and the curve fills the plot. Missing or
 * untrusted recipe data stays empty and the projection falls back to its margin policy.
 */
internal object ChartAxisSeed {
    /** Empty for a manual/unknown selection (no trusted recipe target). */
    fun from(profile: CurveProfile?): Map<ChartMetric, ChartRange> {
        profile ?: return emptyMap()
        val parameters = profile.parameters
        // Pressure targets in tens of a bar; flow-first curves have no meaningful pressure peak.
        val peakTenths = if (parameters.variableFlowLogic || parameters.firstSegmentFlowMode) null else
            listOf(parameters.target1, parameters.target2, parameters.target3, parameters.target4)
                .take(parameters.segmentCount.coerceIn(1, 4))
                .maxOrNull()
                ?.takeIf { it in 1..120 }
        return ranges(
            peakPressureTenthsBar = peakTenths,
            targetWeightHundredthsGram = profile.targetHundredthsGram.takeIf { it > 0 },
            temperatureC = profile.temperatureC,
            maximumWaterMl = profile.maximumWaterMl.takeIf { it > 0 },
        )
    }

    /** Pure target rules shared with unit tests; missing inputs simply omit that metric. */
    fun ranges(
        peakPressureTenthsBar: Int?,
        targetWeightHundredthsGram: Int?,
        temperatureC: Int?,
        maximumWaterMl: Int? = null,
        flowTargetTenthsMlPerSecond: Int? = null,
    ): Map<ChartMetric, ChartRange> {
        val targets = linkedMapOf<ChartMetric, ChartRange>()
        peakPressureTenthsBar?.takeIf { it in 1..120 }?.let { peak ->
            // 9 bar peak -> 9.9 bar headroom, still a 0-10 expected range.
            targets[ChartMetric.PRESSURE] = ChartRange(0f, maxOf(peak / 10f * 1.1f, 10f))
        }
        val targetGrams = targetWeightHundredthsGram?.takeIf { it > 0 }?.let { it / 100f }
        targetGrams?.let { grams ->
            targets[ChartMetric.WEIGHT] = ChartRange(0f, maxOf(grams * 1.05f, 10f))
        }
        // Prefer the weight-derived water estimate; the machine maximum-water target is only a
        // fallback for cups that stop on water volume instead of weight.
        val waterTarget = targetGrams?.let { it * 1.1f + 10f }
            ?: maximumWaterMl?.takeIf { it > 0 }?.let { it * 1.05f }
        if (waterTarget != null) {
            targets[ChartMetric.WATER] = ChartRange(0f, maxOf(waterTarget, 20f))
        }
        temperatureC?.let { temperature ->
            targets[ChartMetric.TEMPERATURE] = ChartRange(temperature - 5f, temperature + 5f)
        }
        // Flow is often unavailable in the recipe; the usual 0-4 scale is still a useful target.
        targets[ChartMetric.FLOW] = ChartRange(0f,
            maxOf(flowTargetTenthsMlPerSecond?.let { it / 10f } ?: 0f, 4f))
        return targets
    }
}
