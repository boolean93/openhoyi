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
    // Weight retains independent grams, never inferred from machine water volume.
    WEIGHT("g", 10f, 0f, 10f, 0f, 40f, true),
    YIELD("g", 10f, 0f, 10f, 0f, 40f, true),
    TEMPERATURE("°C", 5f, 90f, 95f, 88f, 98f, false),
    FLOW("g/s", 2f, 0f, 1f, 0f, 4f, true),
}

/** Realtime Y axes expand only after observed data reaches 85% of the current range. */
internal const val LIVE_EXPANSION_THRESHOLD = 0.85f
/** One expansion step reserves 40% above the observed peak and grows the old high by 60%. */
internal const val LIVE_EXPANSION_HEADROOM = 1.4f
internal const val LIVE_EXPANSION_FACTOR = 1.6f
/** The observed peak must sit in the upper 8% of the axis, otherwise the curve looks flat. */
internal const val LIVE_MAX_OBSERVED_FRACTION = 0.92f

internal val historicalMetrics = listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.WEIGHT, ChartMetric.TEMPERATURE)
internal val liveMetrics = listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.FLOW, ChartMetric.TEMPERATURE)

internal val selectableLiveMetrics = liveMetrics + ChartMetric.YIELD

internal data class ChartValue(val elapsedMs: Long, val value: Float, val estimated: Boolean = false)
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
    /** Left edge of the displayed time window; the realtime chart scrolls a fixed 10 s window. */
    val timeStartMs: Long = 0L,
)

/** Display-only projection: no interpolation, sampling changes, or inferred cup tare. */
internal object FourMetricChartProjection {
    /**
     * [previousRanges] belongs to the caller's same-shot display session; reset it for a new shot.
     * The realtime projection starts from the small per-metric defaults and grows only from the
     * current cup's observed values; the reference cup is still drawn but must not pre-size the
     * axes. With no current observation yet (idle preview) the reference cup supplies the axis and
     * time extent so the previous curve stays fully previewable. The history/comparison projection
     * adapts to the whole observed cup (current + reference).
     * Historical weight remains raw scale weight. Live flow is the display-only scale derivative.
     * timeEndMs is an axis bound, while observedThroughMs describes only current observations.
     */
    fun build(
        points: List<ShotPoint>,
        reference: List<ShotPoint>,
        previousRanges: Map<ChartMetric, ChartRange> = emptyMap(),
        livePauseBaseline: Boolean = false,
        liveFlow: Boolean = false,
        /** Realtime throttle: when false only a clipping (must) expansion is allowed. */
        allowLiveExpansion: Boolean = true,
        /** Recipe-derived final targets; the first needed expansion usually lands directly here. */
        expectedLiveRanges: Map<ChartMetric, ChartRange> = emptyMap(),
        /** Last rendered steps, so a throttled ("held") frame keeps range and step unchanged. */
        previousSteps: Map<ChartMetric, Float> = emptyMap(),
        /** First expansion of a cup: expand every recipe-targeted live axis in this one pass. */
        expandAllLiveAxes: Boolean = false,
        includeLiveWeight: Boolean = false,
    ): FourMetricChartData {
        validate(points)
        validate(reference)
        expectedLiveRanges.forEach { (metric, range) ->
            require(range.low.isFinite() && range.high.isFinite() && range.low < range.high) {
                "Expected live range for $metric must be finite and increasing"
            }
        }
        // A live cup with no observations yet previews the reference cup; once the current cup
        // has any point, its own progress alone drives both axes.
        val referenceDrivesLiveAxes = liveFlow && points.isEmpty()
        val series = (if (liveFlow) { if (includeLiveWeight) selectableLiveMetrics else liveMetrics } else historicalMetrics + listOfNotNull(ChartMetric.YIELD.takeIf { points.any { it.beverageHundredthsGram != null } || reference.any { it.beverageHundredthsGram != null } })).mapNotNull { metric ->
            val current = segments(points, metric, livePauseBaseline)
            val historic = segments(reference, metric)
            if (metric in setOf(ChartMetric.WEIGHT, ChartMetric.YIELD) && current.isEmpty() && historic.isEmpty()) {
                return@mapNotNull null
            }
            val axisValues = if (!liveFlow || referenceDrivesLiveAxes) current + historic else current
            val values = axisValues.flatten().map { it.value }
            val old = previousRanges[metric]
            if (old != null) {
                require(old.low.isFinite() && old.high.isFinite() && old.low < old.high)
            }
            val axis = axis(metric, values, old, live = liveFlow,
                allowLiveExpansion = allowLiveExpansion,
                expected = expectedLiveRanges[metric],
                previousStep = previousSteps[metric],
                expandAll = expandAllLiveAxes && current.isNotEmpty())
            MetricChartSeries(metric, current, historic, axis.range, axis.step)
        }
        val referenceEnd = if (!liveFlow || referenceDrivesLiveAxes)
            reference.lastOrNull()?.elapsedMs ?: 0L else 0L
        val observedEnd = maxOf(1000L, points.lastOrNull()?.elapsedMs ?: 0L, referenceEnd)
        // The realtime chart keeps the whole cup visible: a 10 s minimum end, then continuous
        // scale-out as the observed duration grows. An idle preview with no current observation
        // still shows the full reference cup from zero.
        val timeStart: Long
        val timeEnd: Long
        if (liveFlow) {
            // The realtime time axis always starts at 0: the first 10 s stay 0-10 and after that
            // the end continuously follows the observed duration, so the whole cup stays visible
            // and is smoothly compressed instead of the window scrolling.
            timeStart = 0L
            timeEnd = if (referenceDrivesLiveAxes)
                maxOf(10_000L, reference.lastOrNull()?.elapsedMs ?: 0L)
            else maxOf(10_000L, points.lastOrNull()?.elapsedMs ?: 0L)
        } else {
            // Historical/comparison charts snap the whole-cup end to a readable time step.
            timeStart = 0L
            timeEnd = ChartAxisScale.timeAxis(observedEnd, roundUp = true).endMs
        }
        return FourMetricChartData(
            series,
            points.lastOrNull()?.elapsedMs,
            timeEnd,
            resets(points),
            resets(reference),
            if (livePauseBaseline) pauseBaselines(points) else emptySet(),
            timeStart,
        )
    }

    private fun axis(metric: ChartMetric, values: List<Float>, old: ChartRange?,
        live: Boolean, allowLiveExpansion: Boolean = true,
        expected: ChartRange? = null, previousStep: Float? = null,
        expandAll: Boolean = false): ChartAxisScale.YAxis {
        if (live) {
            val observedMax = values.maxOrNull()
            val observedMin = values.minOrNull()
            if (old == null) {
                // Small initial range; a first observation that already exceeds it (clipping)
                // expands immediately and generously. The cup's first sync expansion also expands
                // every recipe-targeted axis right away.
                val insideInitial = observedMax == null || (observedMax <= metric.initialHigh &&
                    observedMin!! >= metric.initialLow)
                if (insideInitial && !(expandAll && expected != null)) {
                    return ChartAxisScale.yAxis(metric.initialLow, metric.initialHigh,
                        targetDivisions = 5)
                }
                return expandLive(metric, metric.initialLow, metric.initialHigh,
                    observedMin, observedMax,
                    highTriggered = (observedMax ?: metric.initialHigh) > metric.initialHigh ||
                        (expandAll && expected != null),
                    lowTriggered = (observedMin ?: metric.initialLow) < metric.initialLow ||
                        (expandAll && expected != null && !metric.anchorZero),
                    targetDivisions = 4, expected = expected)
            }
            val span = old.high - old.low
            val highReached = observedMax != null &&
                observedMax >= old.low + span * LIVE_EXPANSION_THRESHOLD
            val lowReached = !metric.anchorZero && observedMin != null &&
                observedMin <= old.low + span * (1f - LIVE_EXPANSION_THRESHOLD)
            val highClipped = observedMax != null && observedMax > old.high
            val lowClipped = observedMin != null && observedMin < old.low
            val mustExpand = highClipped || lowClipped
            // A throttled observation keeps the current range unchanged: identical targets mean
            // no animation, which is what removes the multi-axis random ticks.
            if (!mustExpand && !(allowLiveExpansion && (highReached || lowReached))) {
                val step = previousStep ?: ChartAxisScale.stepForRange(old)
                if (!(expandAll && expected != null)) return ChartAxisScale.YAxis(old, step)
            }
            return expandLive(metric, old.low, old.high, observedMin, observedMax,
                highTriggered = highClipped || (allowLiveExpansion && highReached) ||
                    (expandAll && expected != null),
                lowTriggered = lowClipped || (allowLiveExpansion && lowReached) ||
                    (expandAll && expected != null && !metric.anchorZero),
                targetDivisions = 4, expected = expected)
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

    /**
     * One generous expansion step: a zero-anchored physical scale reserves 40% above the observed
     * peak and grows the old high by 60%; temperature keeps its interval shape and expands the
     * triggered side by span-relative margins. The nice rounding happens afterwards.
     */
    private fun expandLive(metric: ChartMetric, low: Float, high: Float,
        observedMin: Float?, observedMax: Float?, highTriggered: Boolean, lowTriggered: Boolean,
        targetDivisions: Int, expected: ChartRange?): ChartAxisScale.YAxis {
        // Trigger decisions are made by the caller from the real (possibly initial) span; only a
        // margin span below is floored to the metric minimum.
        val marginSpan = (high - low).coerceAtLeast(metric.minSpan)
        var newLow = low
        var newHigh = high
        if (expected != null && metric == ChartMetric.TEMPERATURE) {
            // Temperature adopts the integer recipe target (+-2 C) directly and is exempt from the
            // observed/newHigh<=0.92 fill rule (approaching the setpoint bound is normal; a true
            // overshoot is covered by the integer ceiling below). Minimum span still applies.
            var targetHigh = expected.high
            var targetLow = expected.low
            if (targetHigh - targetLow < metric.minSpan) targetLow = targetHigh - metric.minSpan
            if (observedMax != null && observedMax > targetHigh)
                targetHigh = ChartAxisScale.niceCeil(observedMax, 1f)
            if (observedMin != null && observedMin < targetLow)
                targetLow = kotlin.math.floor(observedMin)
            return ChartAxisScale.yAxis(minOf(low, targetLow), maxOf(high, targetHigh),
                targetDivisions = 5)
        } else if (expected != null) {
            // Zero-anchored physical scales adopt the already-nice recipe boundary directly; a
            // genuine overshoot extends it on the same nice grid instead of re-running a coarse
            // yAxis() that could jump from 10 to 15.
            var targetLow = expected.low
            var rawHigh = expected.high
            if (observedMax != null && observedMax > rawHigh)
                rawHigh = maxOf(observedMax * 1.2f, rawHigh * 1.2f)
            if (observedMin != null && observedMin < targetLow)
                targetLow = -maxOf(-observedMin * 1.2f, -targetLow * 1.2f)
            // Round the raw cover requirement to the same nice-boundary + 2-6 nice divisions rule
            // used by the recipe seeds, so the rendered major ticks never show 8.8/11.25/17.5.
            rawHigh = maxOf(rawHigh, high, observedMax ?: rawHigh)
            val boundary = ChartAxisScale.niceBoundary(rawHigh)
            val adoptedHigh = boundary?.high ?: rawHigh
            newLow = minOf(low, targetLow)
            val range = ChartRange(newLow, adoptedHigh)
            val step = when {
                low == 0f && newLow == 0f && boundary != null -> boundary.step
                else -> ChartAxisScale.stepForNiceRange(range)
            }
            return ChartAxisScale.YAxis(range, if (step > 0f) step else ChartAxisScale.stepForNiceRange(range))
        } else if (metric.anchorZero) {
            if (highTriggered && observedMax != null)
                newHigh = maxOf(observedMax * LIVE_EXPANSION_HEADROOM,
                    if (high > 0f) high * LIVE_EXPANSION_FACTOR else observedMax * LIVE_EXPANSION_HEADROOM)
            if (lowTriggered && observedMin != null)
                newLow = minOf(observedMin * LIVE_EXPANSION_HEADROOM,
                    if (low < 0f) low * LIVE_EXPANSION_FACTOR else observedMin * LIVE_EXPANSION_HEADROOM)
        } else {
            if (highTriggered && observedMax != null)
                newHigh = maxOf(observedMax + marginSpan * 0.4f, high + marginSpan * 0.6f)
            if (lowTriggered && observedMin != null)
                newLow = minOf(observedMin - marginSpan * 0.4f, low - marginSpan * 0.6f)
        }
        newHigh = maxOf(newHigh, observedMax ?: newHigh)
        newLow = minOf(newLow, observedMin ?: newLow)
        // Never let a zero-anchored peak look flat: raise until observed/newHigh <= 0.92.
        observedMax?.let { peak ->
            if (metric.anchorZero && newHigh > 0f &&
                peak / newHigh > LIVE_MAX_OBSERVED_FRACTION)
                newHigh = peak / LIVE_MAX_OBSERVED_FRACTION
        }
        return ChartAxisScale.yAxis(newLow, newHigh, targetDivisions)
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
        ChartMetric.YIELD -> point.beverageHundredthsGram?.div(100f)
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
            active.add(ChartValue(point.elapsedMs, actual, metric == ChartMetric.YIELD && point.beverageSource == BeverageWeightSource.ESTIMATED))
            previous = point
        }
        return result.map { it.toList() }
    }
}
