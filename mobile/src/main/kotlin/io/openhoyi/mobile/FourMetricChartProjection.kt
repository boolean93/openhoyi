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
    ): FourMetricChartData {
        validate(points)
        validate(reference)
        val series = ChartMetric.entries.mapNotNull { metric ->
            val current = segments(points, metric)
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
            MetricChartSeries(metric, current, historic, ChartRange(
                floor(low / metric.step) * metric.step,
                ceil(high / metric.step) * metric.step,
            ))
        }
        return FourMetricChartData(
            series,
            points.lastOrNull()?.elapsedMs,
            maxOf(1000L, points.lastOrNull()?.elapsedMs ?: 0L, reference.lastOrNull()?.elapsedMs ?: 0L),
            resets(points),
            resets(reference),
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

    private fun segments(samples: List<ShotPoint>, metric: ChartMetric): List<List<ChartValue>> {
        val result = mutableListOf<MutableList<ChartValue>>()
        var previous: ShotPoint? = null
        var active: MutableList<ChartValue>? = null
        for (point in samples) {
            val actual = value(point, metric)
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
