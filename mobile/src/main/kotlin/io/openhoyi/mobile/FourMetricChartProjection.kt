package io.openhoyi.mobile

/**
 * Fixed physical units and the display-only range policy per metric.
 *
 * [initialLow]/[initialHigh] are the small realtime starting range; the observed values then
 * drive it outward (only-expand). [emptyLow]/[emptyHigh] keep the established history fallback for
 * a cup without samples. [minSpan] stops a nearly flat cup from being magnified into a misleading
 * huge swing, and [anchorZero] keeps the low-frequency physical scales (pressure/water/weight/flow)
 * anchored around zero while temperature keeps its observed interval.
 */
internal enum class ChartMetric(
    val unit: String,
    val minSpan: Float,
    val initialLow: Float,
    val initialHigh: Float,
    val emptyLow: Float,
    val emptyHigh: Float,
    val anchorZero: Boolean,
) {
    PRESSURE("bar", 4f, 0f, 2f, 0f, 10f, true),
    WATER("ml", 10f, 0f, 10f, 0f, 40f, true),
    WEIGHT("g", 10f, 0f, 10f, 0f, 40f, true),
    TEMPERATURE("°C", 5f, 90f, 95f, 88f, 98f, false),
    FLOW("g/s", 2f, 0f, 1f, 0f, 4f, true),
}

internal val historicalMetrics = listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.WEIGHT, ChartMetric.TEMPERATURE)
internal val liveMetrics = listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.FLOW, ChartMetric.TEMPERATURE)

internal data class ChartValue(val elapsedMs: Long, val value: Float)
internal data class ChartRange(val low: Float, val high: Float)
internal data class MetricChartSeries(
    val metric: ChartMetric,
    val current: List<List<ChartValue>>,
    val reference: List<List<ChartValue>>,
    val range: ChartRange,
    /** Major-tick step of [range]; range.low/high are exact multiples of it. */
    val step: Float,
)

internal data class FourMetricChartData(
    val series: List<MetricChartSeries>,
    val observedThroughMs: Long?,
    val timeEndMs: Long,
    val waterResetsMs: List<Long>,
    val referenceWaterResetsMs: List<Long>,
    val pauseBaselineMs: Set<Long> = emptySet(),
)

/** Display-only projection: no interpolation, sampling changes, or inferred cup tare. */
internal object FourMetricChartProjection {
    /**
     * [previousRanges] belongs to the caller's same-shot display session; reset it for a new shot.
     * The realtime projection starts from the small per-metric defaults and grows only from
     * observed values; the history/comparison projection adapts to the whole observed cup.
     * Historical weight remains raw scale weight. Live flow is the display-only scale derivative.
     * timeEndMs is an axis bound, while observedThroughMs describes only current observations.
     */
    fun build(
        points: List<ShotPoint>,
        reference: List<ShotPoint>,
        previousRanges: Map<ChartMetric, ChartRange> = emptyMap(),
        livePauseBaseline: Boolean = false,
        liveFlow: Boolean = false,
    ): FourMetricChartData {
        validate(points)
        validate(reference)
        val series = (if (liveFlow) liveMetrics else historicalMetrics).mapNotNull { metric ->
            val current = segments(points, metric, livePauseBaseline)
            val historic = segments(reference, metric)
            if (metric == ChartMetric.WEIGHT && current.isEmpty() && historic.isEmpty()) {
                return@mapNotNull null
            }
            val values = (current + historic).flatten().map { it.value }
            val old = previousRanges[metric]
            if (old != null) {
                require(old.low.isFinite() && old.high.isFinite() && old.low < old.high)
            }
            val axis = axis(metric, values, old, live = liveFlow)
            MetricChartSeries(metric, current, historic, axis.range, axis.step)
        }
        val observedEnd = maxOf(1000L, points.lastOrNull()?.elapsedMs ?: 0L,
            reference.lastOrNull()?.elapsedMs ?: 0L)
        return FourMetricChartData(
            series,
            points.lastOrNull()?.elapsedMs,
            // The realtime x axis stays continuous (LiveChartGeometry animates it); historical and
            // comparison charts snap their end to a whole time step so every tick is readable.
            if (liveFlow) observedEnd else ChartAxisScale.timeAxis(observedEnd, roundUp = true).endMs,
            resets(points),
            resets(reference),
            if (livePauseBaseline) pauseBaselines(points) else emptySet(),
        )
    }

    private fun axis(metric: ChartMetric, values: List<Float>, old: ChartRange?,
        live: Boolean): ChartAxisScale.YAxis {
        if (live) {
            // Observation-driven start: a small default range that only expands with data.
            val low = minOf(metric.initialLow, values.minOrNull() ?: metric.initialLow,
                old?.low ?: metric.initialLow)
            val high = maxOf(metric.initialHigh, values.maxOrNull() ?: metric.initialHigh,
                old?.high ?: metric.initialHigh)
            return ChartAxisScale.yAxis(low, high, targetDivisions = 5)
        }
        var low: Float
        var high: Float
        if (values.isEmpty()) {
            low = minOf(metric.emptyLow, old?.low ?: metric.emptyLow)
            high = maxOf(metric.emptyHigh, old?.high ?: metric.emptyHigh)
        } else {
            low = values.min()
            high = values.max()
            if (metric.anchorZero) {
                low = minOf(low, 0f)
                high = maxOf(high, 0f)
            }
            if (high - low < metric.minSpan) {
                if (metric.anchorZero) {
                    high = low + metric.minSpan
                } else {
                    val center = (low + high) / 2f
                    low = center - metric.minSpan / 2f
                    high = center + metric.minSpan / 2f
                }
            }
            old?.let {
                low = minOf(low, it.low)
                high = maxOf(high, it.high)
            }
        }
        return ChartAxisScale.yAxis(low, high, targetDivisions = 5)
    }

    private fun validate(samples: List<ShotPoint>) {
        require(samples.all { it.elapsedMs >= 0 } &&
            samples.zipWithNext().all { (a, b) -> a.elapsedMs < b.elapsedMs }) {
            "Chart samples must have nonnegative, strictly increasing elapsed times"
        }
    }

    private fun resets(samples: List<ShotPoint>): List<Long> =
        samples.zipWithNext().mapNotNull { (a, b) ->
            b.elapsedMs.takeIf { b.waterTenthsMl < a.waterTenthsMl }
        }

    private fun value(point: ShotPoint, metric: ChartMetric): Float? = when (metric) {
        ChartMetric.PRESSURE -> (point.pressureTenthsBar / 10f).takeUnless { point.brewing == false }
        ChartMetric.WATER -> point.waterTenthsMl / 10f
        ChartMetric.WEIGHT -> point.weightHundredthsGram?.div(100f)
        ChartMetric.FLOW -> null // Derived from the complete sample timeline in segments().
        ChartMetric.TEMPERATURE -> point.temperatureHundredthsC / 100f
    }

    /** A zero here is a confirmed closed-valve phase baseline, not a measured pressure. */
    fun pauseBaselines(samples: List<ShotPoint>): Set<Long> {
        val result = linkedSetOf<Long>()
        var activeCup = false
        var previous: ShotPoint? = null
        for (point in samples) {
            val adjacent = previous?.let { point.elapsedMs - it.elapsedMs in 1..1500 } == true
            if (!adjacent) activeCup = false
            val bits = point.statusBits
            if (activeCup && adjacent && bits != null && bits and 64 == 0 && bits and 32 == 0 && point.brewing == false)
                result += point.elapsedMs
            if (point.brewing == true) activeCup = true
            else if (point.elapsedMs !in result) activeCup = false
            previous = point
        }
        return result
    }

    private fun segments(samples: List<ShotPoint>, metric: ChartMetric, livePauseBaseline: Boolean = false): List<List<ChartValue>> {
        val result = mutableListOf<MutableList<ChartValue>>()
        val flow = if (metric == ChartMetric.FLOW) CupFlowProjection.values(samples) else emptyList()
        var previous: ShotPoint? = null
        var active: MutableList<ChartValue>? = null
        val pauses = if (livePauseBaseline && metric == ChartMetric.PRESSURE) pauseBaselines(samples) else emptySet()
        for ((index, point) in samples.withIndex()) {
            val actual = if (metric == ChartMetric.FLOW) flow[index]
                else if (point.elapsedMs in pauses) 0f else value(point, metric)
            if (actual == null) {
                active = null
                previous = point
                continue
            }
            val prior = previous
            if (prior == null || point.elapsedMs - prior.elapsedMs > 1500 ||
                (metric == ChartMetric.WATER && point.waterTenthsMl < prior.waterTenthsMl)) {
                active = null
            }
            if (active == null) {
                active = mutableListOf()
                result.add(active)
            }
            active.add(ChartValue(point.elapsedMs, actual))
            previous = point
        }
        return result.map { it.toList() }
    }
}
