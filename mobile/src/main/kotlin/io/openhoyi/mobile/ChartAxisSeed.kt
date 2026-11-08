package io.openhoyi.mobile

/**
 * Recipe-derived realtime axis seeds. Display-only: the values come from the selected App recipe
 * (never a machine readback) and are only a starting range; observed data may still expand it.
 */
internal object ChartAxisSeed {
    /** Empty for a manual/unknown selection; the metrics then use their default seed range. */
    fun from(profile: CurveProfile?): Map<ChartMetric, ChartRange> {
        profile ?: return emptyMap()
        val parameters = profile.parameters
        val peak = if (parameters.variableFlowLogic || parameters.firstSegmentFlowMode) null else
            listOf(parameters.target1, parameters.target2, parameters.target3, parameters.target4)
                .take(parameters.segmentCount.coerceIn(1, 4))
                .maxOrNull()
                ?.takeIf { it > 0 && it <= 120 }
        return ranges(
            peakPressureTenthsBar = peak,
            targetWeightHundredthsGram = profile.targetHundredthsGram.takeIf { it > 0 },
            temperatureC = profile.temperatureC,
        )
    }

    /** Pure seed rules shared by the live page and unit tests. Missing inputs mean "use default". */
    fun ranges(peakPressureTenthsBar: Int?, targetWeightHundredthsGram: Int?, temperatureC: Int?):
        Map<ChartMetric, ChartRange> {
        val result = linkedMapOf<ChartMetric, ChartRange>()
        peakPressureTenthsBar?.takeIf { it in 0..120 }?.let { peak ->
            result[ChartMetric.PRESSURE] = withMinSpan(ChartRange(0f, peak / 10f + 1f), ChartMetric.PRESSURE)
        }
        targetWeightHundredthsGram?.takeIf { it > 0 }?.let { target ->
            val grams = target / 100f
            result[ChartMetric.WEIGHT] = withMinSpan(ChartRange(0f, grams + maxOf(5f, grams * 0.1f)),
                ChartMetric.WEIGHT)
            result[ChartMetric.WATER] = withMinSpan(ChartRange(0f, grams * 1.5f + 10f), ChartMetric.WATER)
        }
        temperatureC?.let { temperature ->
            result[ChartMetric.TEMPERATURE] = withMinSpan(ChartRange(temperature - 5f, temperature + 5f),
                ChartMetric.TEMPERATURE)
        }
        return result
    }

    private fun withMinSpan(range: ChartRange, metric: ChartMetric): ChartRange =
        if (range.high - range.low >= metric.minSpan) range
        else ChartRange(range.low, range.low + metric.minSpan)
}
