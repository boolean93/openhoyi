package io.openhoyi.mobile

import kotlin.math.ceil
import kotlin.math.floor

internal enum class ChartMetric(val unit: String, val floor: Float, val ceiling: Float, val step: Float) {
    PRESSURE("bar", 0f, 12f, 1f),
    WATER("ml", 0f, 60f, 10f),
    WEIGHT("g", 0f, 50f, 10f),
    TEMPERATURE("°C", 80f, 100f, 5f),
}

internal data class ChartValue(val elapsedMs: Long, val value: Float)
internal data class ChartRange(val low: Float, val high: Float)
internal data class MetricChartSeries(
    val metric: ChartMetric,
    val current: List<List<ChartValue>>,
    val reference: List<List<ChartValue>>,
    val range: ChartRange,
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
     * Weight is the raw observed scale reading, not an inferred beverage yield.
     * timeEndMs is an axis bound, while observedThroughMs describes only current observations.
     */
    fun build(
        points: List<ShotPoint>,
        reference: List<ShotPoint>,
        previousRanges: Map<ChartMetric, ChartRange> = emptyMap(),
        livePauseBaseline: Boolean = false,
    ): FourMetricChartData {
        validate(points)
        validate(reference)
        val series = ChartMetric.entries.mapNotNull { metric ->
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
            val low = minOf(metric.floor, values.minOrNull() ?: metric.floor, old?.low ?: metric.floor)
            val high = maxOf(metric.ceiling, values.maxOrNull() ?: metric.ceiling, old?.high ?: metric.ceiling)
            // Float division/multiplication can round inward at large magnitudes.
            val roundedLow = floor(low / metric.step) * metric.step
            val roundedHigh = ceil(high / metric.step) * metric.step
            MetricChartSeries(metric, current, historic, ChartRange(
                minOf(low, roundedLow),
                maxOf(high, roundedHigh),
            ))
        }
        return FourMetricChartData(
            series,
            points.lastOrNull()?.elapsedMs,
            maxOf(1000L, points.lastOrNull()?.elapsedMs ?: 0L, reference.lastOrNull()?.elapsedMs ?: 0L),
            resets(points),
            resets(reference),
            if (livePauseBaseline) pauseBaselines(points) else emptySet(),
        )
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
        var previous: ShotPoint? = null
        var active: MutableList<ChartValue>? = null
        val pauses = if (livePauseBaseline && metric == ChartMetric.PRESSURE) pauseBaselines(samples) else emptySet()
        for (point in samples) {
            val actual = if (point.elapsedMs in pauses) 0f else value(point, metric)
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
