package io.openhoyi.mobile

/** An actual observation, never an interpolated or independently carried-forward metric. */
internal object ChartInspection {
    fun nearestIndex(points: List<ShotPoint>, elapsedMs: Long): Int? {
        if (points.isEmpty()) return null
        val target = elapsedMs.coerceAtLeast(0)
        val found = points.binarySearchBy(target) { it.elapsedMs }
        if (found >= 0) return found
        val after = -found - 1
        if (after == 0) return 0
        if (after == points.size) return points.lastIndex
        return if (target - points[after - 1].elapsedMs <= points[after].elapsedMs - target) after - 1 else after
    }
    fun values(point: ShotPoint): Map<ChartMetric, Float?> = linkedMapOf(
        ChartMetric.PRESSURE to (point.pressureTenthsBar / 10f).takeUnless { point.brewing == false },
        ChartMetric.WATER to point.waterTenthsMl / 10f,
        ChartMetric.WEIGHT to point.weightHundredthsGram?.div(100f),
        ChartMetric.TEMPERATURE to point.temperatureHundredthsC / 100f,
    )
}
