package io.openhoyi.mobile

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Display-only interpolation for the realtime axis animation.
 *
 * The projection always produces the committed target data; this helper only describes how the
 * already-rendered axis state moves towards it, so a stage-boundary jump becomes a short smooth
 * scale. It never mutates samples, previousRanges or stored data.
 */
internal object LiveAxisAnimation {
    /** Kept within the requested 350-450 ms range and short enough not to slow the UI timer. */
    const val DURATION_MS = 380L

    data class AxisState(
        val ranges: Map<ChartMetric, ChartRange>,
        val steps: Map<ChartMetric, Float>,
        val timeEndMs: Long,
    )

    fun state(data: FourMetricChartData): AxisState = AxisState(
        ranges = data.series.associate { it.metric to it.range },
        steps = data.series.associate { it.metric to it.step },
        timeEndMs = data.timeEndMs,
    )

    /** True when [current] already targets exactly [target], so a running animation can finish. */
    fun sameTarget(current: AxisState?, target: AxisState): Boolean = current == target

    /** Linear interpolation; [fraction] is clamped so callers can pass animator values safely. */
    fun interpolate(from: AxisState, to: AxisState, fraction: Float): AxisState {
        val f = fraction.coerceIn(0f, 1f)
        if (f >= 1f) return to
        if (f <= 0f) return from
        val ranges = to.ranges.mapValues { (metric, target) ->
            val start = from.ranges[metric]
            if (start == null) target
            else ChartRange(lerp(start.low, target.low, f), lerp(start.high, target.high, f))
        }
        val steps = to.steps.mapValues { (metric, target) ->
            val start = from.steps[metric] ?: return@mapValues target
            max(lerp(start, target, f), MIN_STEP)
        }
        return AxisState(ranges, steps, lerpTime(from.timeEndMs, to.timeEndMs, f))
    }

    /**
     * Returns [target] with the interpolated axis applied. Points, reference segments, resets,
     * observation timestamps and pause baselines stay exactly as projected.
     */
    fun apply(target: FourMetricChartData, state: AxisState): FourMetricChartData =
        target.copy(
            series = target.series.map { series ->
                val range = state.ranges[series.metric] ?: return@map series
                series.copy(range = range, step = state.steps[series.metric] ?: series.step)
            },
            timeEndMs = state.timeEndMs,
        )

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction

    private fun lerpTime(start: Long, end: Long, fraction: Float): Long =
        start + ((end - start) * fraction.toDouble()).roundToLong()

    private const val MIN_STEP = 1e-4f

    /** Locale-stable description used only by unit tests and diagnostics. */
    fun describe(state: AxisState): String = state.ranges.entries.joinToString(";") { (metric, range) ->
        String.format(Locale.ROOT, "%s[%.2f..%.2f]", metric.name, range.low, range.high)
    }
}
