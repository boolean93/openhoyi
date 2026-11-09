package io.openhoyi.mobile

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Display-only axis arithmetic shared by the realtime and history charts.
 *
 * Values are rounded outward to a "nice" step from {1, 2, 2.5, 5, 10} x 10^k so the first and
 * last major ticks always equal [ChartRange.low] and [ChartRange.high]. Time ticks use a fixed set
 * of human-readable seconds steps. Nothing here samples, interpolates or changes stored data.
 */
internal object ChartAxisScale {
    private val NICE_MANTISSAS = listOf(1f, 2f, 2.5f, 5f, 10f)
    private val NICE_TIME_STEPS_SECONDS = listOf(1, 2, 5, 10, 15, 20, 30, 60)

    /** Smallest nice step >= [rawStep]; [rawStep] must be positive and finite. */
    fun niceStep(rawStep: Float): Float {
        require(rawStep.isFinite() && rawStep > 0f) { "rawStep must be positive and finite" }
        var exponent = floor(log10(rawStep.toDouble())).toInt()
        var step = NICE_MANTISSAS.map { it * 10f.pow(exponent) }.firstOrNull { it >= rawStep }
        while (step == null) {
            exponent++
            step = NICE_MANTISSAS.map { it * 10f.pow(exponent) }.firstOrNull { it >= rawStep }
        }
        return step
    }

    /** Nice step for a span that should hold about [targetDivisions] main divisions. */
    fun niceStep(low: Float, high: Float, targetDivisions: Int = 5): Float {
        require(targetDivisions > 0)
        require(low.isFinite() && high.isFinite() && high > low) { "axis span must be finite and positive" }
        return niceStep((high - low) / targetDivisions)
    }

    /** An axis range together with the major-tick step that produced it. */
    data class YAxis(val range: ChartRange, val step: Float)

    /**
     * Outward-rounded axis containing [low]..[high] with 2..[targetDivisions] divisions.
     * A lower/upper bound is never placed inside an observed value.
     */
    fun yAxis(low: Float, high: Float, targetDivisions: Int = 5): YAxis {
        require(targetDivisions >= 2)
        require(low.isFinite() && high.isFinite()) { "axis bounds must be finite" }
        val observedLow = minOf(low, high)
        val observedHigh = maxOf(low, high)
        if (observedHigh - observedLow <= 0f) {
            // A zero-width sample still gets a real axis instead of a division by zero.
            val pad = max(abs(observedLow) * 0.1f, 1f)
            return yAxis(observedLow - pad, observedHigh + pad, targetDivisions)
        }
        var step = niceStep((observedHigh - observedLow) / targetDivisions)
        repeat(16) {
            val niceLow = noNegativeZero(floor(observedLow / step) * step)
            val niceHigh = noNegativeZero(ceil(observedHigh / step) * step)
            val divisions = ((niceHigh - niceLow) / step).roundToInt()
            // Up to one extra division keeps temperature on whole degrees instead of 2.5 steps;
            // the renderer labels only as many rows as the plot height can hold.
            if (divisions in 2..targetDivisions + 1) return YAxis(ChartRange(niceLow, niceHigh), step)
            step = if (divisions > targetDivisions) nextNiceStep(step) else previousNiceStep(step)
        }
        // Extremely wide floats: keep the observed values inside a finite range.
        val padded = max(abs(observedLow), abs(observedHigh)) * 0.25f
        return YAxis(ChartRange(noNegativeZero(observedLow - padded), noNegativeZero(observedHigh + padded)),
            ((observedHigh - observedLow) + 2 * padded) / targetDivisions)
    }

    fun yRange(low: Float, high: Float, targetDivisions: Int = 5): ChartRange =
        yAxis(low, high, targetDivisions).range

    /** Major ticks of [range], always starting at low and ending exactly at high. */
    fun majorTicks(range: ChartRange, step: Float): List<Float> {
        require(range.low.isFinite() && range.high.isFinite() && range.high > range.low)
        require(step.isFinite() && step > 0f)
        val divisions = ((range.high - range.low) / step).roundToInt().coerceAtLeast(1)
        return (0..divisions).map { index ->
            if (index == divisions) range.high else noNegativeZero(range.low + step * index)
        }
    }

    /** Unlabeled half-step grid lines between the major ticks. */
    fun minorTicks(range: ChartRange, step: Float): List<Float> {
        val half = step / 2f
        if (!half.isFinite() || half <= 0f) return emptyList()
        val count = ((range.high - range.low) / half).roundToInt()
        return (1 until count).filter { it % 2 == 1 }.map { noNegativeZero(range.low + half * it) }
    }

    /** Human-readable time step for about [targetDivisions] main divisions. */
    fun niceTimeStepMs(spanMs: Long, targetDivisions: Int = 6): Long {
        require(spanMs > 0 && targetDivisions > 0)
        val rawSeconds = spanMs / 1000.0 / targetDivisions
        val seconds = NICE_TIME_STEPS_SECONDS.firstOrNull { it >= rawSeconds }
            ?: (ceil(rawSeconds / 60.0) * 60).toInt()
        return seconds.coerceAtLeast(1) * 1000L
    }

    /**
     * Time grid. [roundUp] keeps a full final major tick (history/comparison); the realtime chart
     * keeps its existing continuous end so a growing cup never jumps its x axis.
     */
    fun timeAxis(timeEndMs: Long, targetDivisions: Int = 6, roundUp: Boolean = false): TimeAxis {
        require(timeEndMs > 0) { "time end must be positive" }
        val span = timeEndMs.coerceAtLeast(1_000L)
        val step = niceTimeStepMs(span, targetDivisions)
        val end = if (roundUp) ceil(span / step.toDouble()).toLong() * step else span
        val major = mutableListOf<Long>()
        var tick = 0L
        while (tick <= end) { major += tick; tick += step }
        if (major.last() != end) major += end
        val minor = mutableListOf<Long>()
        var half = step / 2
        while (half < end) { if (half % step != 0L) minor += half; half += step / 2 }
        return TimeAxis(end, step, major, minor)
    }

    data class TimeAxis(val endMs: Long, val stepMs: Long, val majorTicks: List<Long>, val minorTicks: List<Long>)

    private fun nextNiceStep(step: Float): Float = niceStep(step * 1.01f)

    /** Next smaller step from {1, 2, 2.5, 5, 10} x 10^k; used only when a span is too coarse. */
    internal fun previousNiceStep(step: Float): Float {
        require(step.isFinite() && step > 0f) { "step must be positive and finite" }
        // Float pow() can return a neighbouring exponent's value within a few ulps, so compare
        // with a small relative margin instead of raw equality.
        val boundary = step - step * 1e-4f
        var exponent = floor(log10(step.toDouble())).toInt()
        NICE_MANTISSAS.map { it * 10f.pow(exponent) }.lastOrNull { it < boundary }?.let { return it }
        exponent--
        return NICE_MANTISSAS.map { it * 10f.pow(exponent) }.lastOrNull { it < boundary } ?: (step / 10f)
    }
    /** Keeps the numeric value but drops a negative zero so a history label cannot read "-0.0". */
    internal fun noNegativeZero(value: Float): Float = value + 0f
}
