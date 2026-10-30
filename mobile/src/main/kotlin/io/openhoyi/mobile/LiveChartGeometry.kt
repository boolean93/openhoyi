package io.openhoyi.mobile

import kotlin.math.sqrt

/** Rendering only: each input is already one evidence-contiguous segment. */
internal object LiveChartGeometry {
    data class Cubic(val start: ChartValue, val control1: ChartValue, val control2: ChartValue, val end: ChartValue)
    fun timeEnd(observedEnd: Long): Long = maxOf(30_000L, ((observedEnd + 29_999L) / 30_000L) * 30_000L)

    /** Fritsch–Carlson limited Hermite: exact endpoints, no invented extrema. */
    fun cubics(segment: List<ChartValue>): List<Cubic> {
        if (segment.size < 2) return emptyList()
        require(segment.zipWithNext().all { (a, b) -> b.elapsedMs > a.elapsedMs })
        val slopes = segment.zipWithNext().map { (a, b) -> (b.value - a.value).toDouble() / (b.elapsedMs - a.elapsedMs) }
        val tangent = DoubleArray(segment.size) { i -> when (i) {
            0 -> slopes.first()
            segment.lastIndex -> slopes.last()
            else -> if (slopes[i - 1] * slopes[i] <= 0) 0.0 else (slopes[i - 1] + slopes[i]) / 2
        } }
        for (i in slopes.indices) {
            val slope = slopes[i]
            if (slope == 0.0) { tangent[i] = 0.0; tangent[i + 1] = 0.0; continue }
            val a = tangent[i] / slope; val b = tangent[i + 1] / slope
            val radius = sqrt(a * a + b * b)
            if (radius > 3) {
                tangent[i] = 3 * a / radius * slope
                tangent[i + 1] = 3 * b / radius * slope
            }
        }
        return segment.zipWithNext().mapIndexed { i, (a, b) ->
            val third = (b.elapsedMs - a.elapsedMs) / 3.0
            Cubic(a, ChartValue(a.elapsedMs + third.toLong(), (a.value + third * tangent[i]).toFloat()),
                ChartValue(b.elapsedMs - third.toLong(), (b.value - third * tangent[i + 1]).toFloat()), b)
        }
    }
}
