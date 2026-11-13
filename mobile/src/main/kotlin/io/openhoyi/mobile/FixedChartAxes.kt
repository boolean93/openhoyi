package io.openhoyi.mobile

/** Display bounds only. Never changes observations, extraction controls or history. */
internal object FixedChartAxes {
    const val PREFERENCES = "chart_display"
    const val KEY = "fixed_axes"
    const val DEFAULT = false
    const val TIME_END_MS = 60_000L
    fun axis(metric: ChartMetric): ChartAxisScale.YAxis = when (metric) {
        ChartMetric.PRESSURE -> ChartAxisScale.YAxis(ChartRange(0f, 12f), 2f)
        ChartMetric.WATER -> ChartAxisScale.YAxis(ChartRange(0f, 100f), 20f)
        ChartMetric.FLOW -> ChartAxisScale.YAxis(ChartRange(0f, 4f), 1f)
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
            time = if (reference) series.any { values(it).any { value -> value.elapsedMs > TIME_END_MS } }
                else (data.observedThroughMs ?: 0L) > TIME_END_MS,
            metrics = series.filter { item -> values(item).any { value ->
                val range = axis(item.metric).range
                value.value < range.low || value.value > range.high
            } }.map { it.metric }.toSet(),
        )
    }
}
