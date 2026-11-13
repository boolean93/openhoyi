package io.openhoyi.mobile

/** Display preference only. Never passed to recording or device control. */
internal object ChartSeriesSelection {
    val defaults: Set<ChartMetric> = liveMetrics.toSet()
    fun decode(names: Set<String>?): Set<ChartMetric> = names?.mapNotNull { name ->
        selectableLiveMetrics.firstOrNull { it.name == name }
    }?.toSet()?.takeIf { it.isNotEmpty() } ?: defaults
    fun animationState(state: LiveAxisAnimation.AxisState, visible: Set<ChartMetric>): LiveAxisAnimation.AxisState =
        if (ChartMetric.YIELD in visible) state else state.copy(
            ranges = state.ranges - ChartMetric.YIELD, steps = state.steps - ChartMetric.YIELD)
    fun toggle(current: Set<ChartMetric>, metric: ChartMetric): Set<ChartMetric> =
        if (metric !in current) current + metric else (current - metric).takeIf { it.isNotEmpty() } ?: current
}
