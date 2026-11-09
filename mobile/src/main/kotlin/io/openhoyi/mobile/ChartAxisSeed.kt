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
            // 9-10 bar peaks stay on the 0-10 target; only a genuinely higher recipe rounds up
            // to the next nice boundary (e.g. 10.5 -> 12), never through a 1.1 multiplier.
            ChartAxisScale.niceBoundary(maxOf(peak / 10f, 10f))?.let { boundary ->
                targets[ChartMetric.PRESSURE] = ChartRange(0f, boundary.high)
            }
        }
        val targetGrams = targetWeightHundredthsGram?.takeIf { it > 0 }?.let { it / 100f }
        targetGrams?.let { grams ->
            ChartAxisScale.niceBoundary(maxOf(grams * 1.05f, 10f))?.let { boundary ->
                targets[ChartMetric.WEIGHT] = ChartRange(0f, boundary.high)
            }
        }
        // Prefer the machine water target (it is what the cup actually stops on); fall back to
        // the weight-derived estimate only when no machine water target is available.
        val waterTarget = maximumWaterMl?.takeIf { it > 0 }?.let { it * 1.05f }
            ?: targetGrams?.let { it * 1.1f + 10f }
        if (waterTarget != null) {
            ChartAxisScale.niceBoundary(maxOf(waterTarget, 20f))?.let { boundary ->
                targets[ChartMetric.WATER] = ChartRange(0f, boundary.high)
            }
        }
        temperatureC?.let { temperature ->
            // A tight +-2 C target keeps the actual temperature curve readable instead of flat.
            targets[ChartMetric.TEMPERATURE] = ChartRange((temperature - 2).toFloat(),
                (temperature + 2).toFloat())
        }
        // Flow curves are usually well under 2 g/s; a broader target flattens the curve.
        val flowTarget = flowTargetTenthsMlPerSecond?.let { it / 10f * 1.5f } ?: 0f
        ChartAxisScale.niceBoundary(maxOf(flowTarget, 2f))?.let { boundary ->
            targets[ChartMetric.FLOW] = ChartRange(0f, boundary.high)
        }
        return targets
    }
}
