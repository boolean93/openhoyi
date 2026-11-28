package io.openhoyi.mobile

import android.content.SharedPreferences

/** Display bounds only. Never changes observations, extraction controls or history. */
internal object FixedChartAxes {
    const val PREFERENCES = "chart_display"
    const val KEY = "fixed_axes"
    const val DEFAULT = false
    const val INITIAL_TIME_SECONDS_KEY = "initial_time_seconds"
    const val DEFAULT_INITIAL_TIME_SECONDS = 40

    fun initialTimeSeconds(stored: Any?): Int =
        (stored as? Int)?.takeIf { it > 0 } ?: DEFAULT_INITIAL_TIME_SECONDS

    fun readInitialTimeSeconds(preferences: SharedPreferences): Int =
        initialTimeSeconds(preferences.all[INITIAL_TIME_SECONDS_KEY])

    fun axis(metric: ChartMetric): ChartAxisScale.YAxis = when (metric) {
        ChartMetric.PRESSURE -> ChartAxisScale.YAxis(ChartRange(0f, 15f), 3f)
        ChartMetric.WATER -> ChartAxisScale.YAxis(ChartRange(0f, 100f), 20f)
        ChartMetric.FLOW -> ChartAxisScale.YAxis(ChartRange(0f, 5f), 1f)
        ChartMetric.TEMPERATURE -> ChartAxisScale.YAxis(ChartRange(80f, 100f), 5f)
        ChartMetric.YIELD, ChartMetric.WEIGHT -> ChartAxisScale.YAxis(ChartRange(0f, 60f), 10f)
    }

    data class Overflow(val time: Boolean, val metrics: Set<ChartMetric>) {
        val any get() = time || metrics.isNotEmpty()
    }
    /** All retained observations count: a transient excursion must not silently disappear. */
    fun overflow(data: FourMetricChartData, visible: Set<ChartMetric>, reference: Boolean = false): Overflow {
        val series = data.series.filter { it.metric in visible }
        fun values(item: MetricChartSeries) = (if (reference) item.reference else item.current).flatten()
        return Overflow(
            time = if (reference) series.any { values(it).any { value -> value.elapsedMs > data.timeEndMs } }
                else (data.observedThroughMs ?: 0L) > data.timeEndMs,
            metrics = series.filter { item -> values(item).any { value ->
                val range = axis(item.metric).range
                value.value < range.low || value.value > range.high
            } }.map { it.metric }.toSet(),
        )
    }
}
