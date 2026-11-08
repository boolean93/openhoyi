package io.openhoyi.mobile

import kotlin.math.sqrt

/** Rendering only: each input is already one evidence-contiguous segment. */
internal object LiveChartGeometry {
    data class Cubic(val start: ChartValue, val control1: ChartValue, val control2: ChartValue, val end: ChartValue)
    fun revealDuration(updateGap: Long): Long = updateGap.coerceIn(80L, 500L)

    /**
     * Stable realtime time-axis boundaries: 5 s steps up to 30 s, then 10 s to 60 s, 15 s to
     * 90 s, 30 s to 180 s and whole minutes afterwards. The axis only changes when a boundary
     * is crossed, so the plot does not jitter every frame.
     */
    private val LIVE_TIME_BOUNDARIES_MS = longArrayOf(
        5_000L, 10_000L, 15_000L, 20_000L, 25_000L, 30_000L,
        40_000L, 50_000L, 60_000L,
        75_000L, 90_000L,
        120_000L, 150_000L, 180_000L,
    )

    /** Smallest staged boundary that still contains [observedEndMs]; a new cup starts at 5 s. */
    fun stagedTimeEnd(observedEndMs: Long): Long {
        require(observedEndMs >= 0) { "observed end must be nonnegative" }
        if (observedEndMs <= 5_000L) return 5_000L
        LIVE_TIME_BOUNDARIES_MS.firstOrNull { observedEndMs <= it }?.let { return it }
        val over = observedEndMs - 180_000L
        return 180_000L + ((over + 59_999L) / 60_000L) * 60_000L
    }

    /** Realtime axis never shrinks within one cup; [previous] is the last rendered boundary. */
    fun liveTimeEnd(previous: Long, observedEndMs: Long): Long =
        maxOf(previous, stagedTimeEnd(observedEndMs))

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
    /** A transient Canvas anchor only; never a stored observation or a control input. */
    fun visiblePoint(segment: List<ChartValue>, through: Long, pauseTimes: Set<Long> = emptySet()): ChartValue? {
        val first = segment.firstOrNull() ?: return null
        if (through < first.elapsedMs) return null
        if (through >= segment.last().elapsedMs) return segment.last()
        val index = segment.indexOfLast { it.elapsedMs <= through }
        val start = segment[index]; val end = segment[index+1]
        if ((start.elapsedMs in pauseTimes) != (end.elapsedMs in pauseTimes)) return ChartValue(through,start.value)
        val c = cubics(segment)[index]
        val t = (through-start.elapsedMs).toDouble()/(end.elapsedMs-start.elapsedMs)
        val u = 1-t
        return ChartValue(through,(u*u*u*c.start.value+3*u*u*t*c.control1.value+3*u*t*t*c.control2.value+t*t*t*c.end.value).toFloat())
    }

    /** Sorted screen-space centers; null is an explicit compact fallback, never overlapping labels. */
    fun labelCenters(preferred: List<Float>, top: Float, bottom: Float, spacing: Float): List<Float>? {
        require(top <= bottom && spacing >= 0 && preferred.all { it.isFinite() })
        require(preferred.zipWithNext().all { (a,b) -> a <= b })
        if (preferred.isEmpty()) return emptyList()
        if ((preferred.size-1)*spacing > bottom-top) return null
        val placed = preferred.map { it.coerceIn(top,bottom) }.toMutableList()
        for (i in 1..placed.lastIndex) placed[i] = maxOf(placed[i],placed[i-1]+spacing)
        placed[placed.lastIndex] = minOf(placed.last(),bottom)
        for (i in placed.lastIndex-1 downTo 0) placed[i] = minOf(placed[i],placed[i+1]-spacing)
        return placed
    }

}
