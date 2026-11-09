package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class LiveAxisAnimationTest {
    private fun series(metric: ChartMetric, low: Float, high: Float, step: Float) =
        MetricChartSeries(metric, listOf(listOf(ChartValue(0, low), ChartValue(1_000, high))),
            emptyList(), ChartRange(low, high), step)

    private fun data(timeEndMs: Long, vararg series: MetricChartSeries) = FourMetricChartData(
        series = series.toList(),
        observedThroughMs = 1_000,
        timeEndMs = timeEndMs,
        waterResetsMs = emptyList(),
        referenceWaterResetsMs = emptyList(),
        pauseBaselineMs = setOf(500L),
    )

    @Test fun midpointInterpolatesEveryRangeStepAndTimeEnd() {
        val from = LiveAxisAnimation.state(data(5_000,
            series(ChartMetric.PRESSURE, 0f, 2f, 0.5f)))
        val to = LiveAxisAnimation.state(data(10_000,
            series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        val mid = LiveAxisAnimation.interpolate(from, to, 0.5f)
        assertEquals(ChartRange(0f, 5f), mid.ranges.getValue(ChartMetric.PRESSURE))
        assertEquals(1.25f, mid.steps.getValue(ChartMetric.PRESSURE), 1e-6f)
        assertEquals(7_500L, mid.timeEndMs)
    }

    @Test fun fractionsOutsideZeroOneAreClamped() {
        val from = LiveAxisAnimation.state(data(5_000, series(ChartMetric.WATER, 0f, 10f, 2f)))
        val to = LiveAxisAnimation.state(data(10_000, series(ChartMetric.WATER, 0f, 20f, 5f)))
        assertEquals(from, LiveAxisAnimation.interpolate(from, to, -2f))
        assertEquals(to, LiveAxisAnimation.interpolate(from, to, 3f))
    }

    @Test fun aMetricMissingFromTheStartSnapsToItsTarget() {
        val from = LiveAxisAnimation.state(data(5_000, series(ChartMetric.WATER, 0f, 10f, 2f)))
        val to = LiveAxisAnimation.state(data(10_000, series(ChartMetric.WATER, 0f, 20f, 5f),
            series(ChartMetric.FLOW, 0f, 4f, 1f)))
        val mid = LiveAxisAnimation.interpolate(from, to, 0.5f)
        assertEquals(ChartRange(0f, 4f), mid.ranges.getValue(ChartMetric.FLOW))
    }

    @Test fun applyKeepsSamplesObservationResetsAndPauseBaselines() {
        val target = data(10_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f))
        val state = LiveAxisAnimation.AxisState(
            ranges = mapOf(ChartMetric.PRESSURE to ChartRange(0f, 4f)),
            steps = mapOf(ChartMetric.PRESSURE to 1f),
            timeEndMs = 7_000L)
        val rendered = LiveAxisAnimation.apply(target, state)
        assertEquals(ChartRange(0f, 4f), rendered.series.single().range)
        assertEquals(1f, rendered.series.single().step, 0f)
        assertEquals(7_000L, rendered.timeEndMs)
        assertEquals(target.series.single().current, rendered.series.single().current)
        assertEquals(target.referenceWaterResetsMs, rendered.referenceWaterResetsMs)
        assertEquals(setOf(500L), rendered.pauseBaselineMs)
        assertEquals(target.observedThroughMs, rendered.observedThroughMs)
    }

    @Test fun identityInterpolationReturnsTheTargetStateExactly() {
        val from = LiveAxisAnimation.state(data(5_000, series(ChartMetric.TEMPERATURE, 90f, 95f, 1f)))
        val to = LiveAxisAnimation.state(data(10_000, series(ChartMetric.TEMPERATURE, 92f, 100f, 2f)))
        assertEquals(to, LiveAxisAnimation.interpolate(from, to, 1f))
        assertEquals(to, LiveAxisAnimation.interpolate(to, to, 0.4f))
    }

    @Test fun stepNeverBecomesNonPositiveDuringInterpolation() {
        val from = LiveAxisAnimation.state(data(5_000, series(ChartMetric.WATER, 0f, 10f, 2f)))
        val to = LiveAxisAnimation.state(data(10_000, series(ChartMetric.WATER, 0f, 20f, 5f)))
        for (fraction in listOf(0f, 0.01f, 0.5f, 0.99f, 1f)) {
            assertTrue(LiveAxisAnimation.interpolate(from, to, fraction)
                .steps.getValue(ChartMetric.WATER) > 0f)
        }
    }

    @Test fun sameTargetDetectsIdenticalTargetsOnly() {
        val a = LiveAxisAnimation.state(data(10_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        val b = LiveAxisAnimation.state(data(10_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        val c = LiveAxisAnimation.state(data(10_000, series(ChartMetric.PRESSURE, 0f, 8f, 2.5f)))
        assertTrue(LiveAxisAnimation.sameTarget(a, b))
        assertFalse(LiveAxisAnimation.sameTarget(a, c))
        assertFalse(LiveAxisAnimation.sameTarget(null, a))
    }

    @Test fun redirectionStartsFromTheCurrentInterpolatedState() {
        val from = LiveAxisAnimation.state(data(5_000, series(ChartMetric.PRESSURE, 0f, 2f, 0.5f)))
        val firstTarget = LiveAxisAnimation.state(data(10_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        val current = LiveAxisAnimation.interpolate(from, firstTarget, 0.5f)
        val secondTarget = LiveAxisAnimation.state(data(15_000, series(ChartMetric.PRESSURE, 0f, 12f, 2.5f)))
        val redirected = LiveAxisAnimation.interpolate(current, secondTarget, 0.5f)
        assertEquals(8.5f, redirected.ranges.getValue(ChartMetric.PRESSURE).high, 1e-4f)
        assertEquals(11_250L, redirected.timeEndMs)
    }

    private fun dataAt(startMs: Long, endMs: Long, vararg series: MetricChartSeries) =
        FourMetricChartData(series.toList(), observedThroughMs = endMs, timeEndMs = endMs,
            waterResetsMs = emptyList(), referenceWaterResetsMs = emptyList(), timeStartMs = startMs)

    @Test fun windowStartInterpolatesTogetherWithTheEnd() {
        val from = LiveAxisAnimation.state(dataAt(0, 10_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        val to = LiveAxisAnimation.state(dataAt(15_000, 25_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        val mid = LiveAxisAnimation.interpolate(from, to, 0.5f)
        assertEquals(7_500L, mid.timeStartMs)
        assertEquals(17_500L, mid.timeEndMs)
        assertEquals(7_500L, LiveAxisAnimation.apply(dataAt(15_000, 25_000,
            series(ChartMetric.PRESSURE, 0f, 8f, 2f)), mid).timeStartMs)
    }

    @Test fun sameTargetIncludesTheWindowStart() {
        val a = LiveAxisAnimation.state(dataAt(0, 10_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        val b = LiveAxisAnimation.state(dataAt(1_000, 11_000, series(ChartMetric.PRESSURE, 0f, 8f, 2f)))
        assertFalse(LiveAxisAnimation.sameTarget(a, b))
    }

    @Test fun animationDurationStaysInTheRequestedSmoothRange() {
        assertTrue(LiveAxisAnimation.DURATION_MS in 350L..450L)
    }
}
