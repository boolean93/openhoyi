package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class FixedChartAxesTest {
    private fun point(ms: Long, pressure: Int = 90, water: Int = 350, temperature: Int = 9200,
        weight: Int = 3600, yield: Int? = 3600) = ShotPoint(ms, pressure, 0, water, temperature, weight,
            0, true, beverageHundredthsGram = yield)
    private fun project(points: List<ShotPoint>, reference: List<ShotPoint> = emptyList(),
        initialSeconds: Int = FixedChartAxes.DEFAULT_INITIAL_TIME_SECONDS) =
        FourMetricChartProjection.build(points, reference, liveFlow = true, includeLiveWeight = true,
            fixedLiveAxes = true, fixedInitialTimeSeconds = initialSeconds)

    @Test fun defaultOffRetainsOriginalDynamicAxes() {
        assertFalse(FixedChartAxes.DEFAULT)
        for (time in listOf(5000L, 12000L)) {
            val points = listOf(point(time))
            assertEquals(FourMetricChartProjection.build(points, emptyList(), liveFlow = true),
                FourMetricChartProjection.build(points, emptyList(), liveFlow = true, fixedLiveAxes = false,
                    fixedInitialTimeSeconds = 60))
            assertEquals(maxOf(10000L, time), FourMetricChartProjection.build(points, emptyList(), liveFlow = true).timeEndMs)
        }
    }
    @Test fun configuredSixtySecondStartExtendsWithTheCup() {
        for ((observed, expectedEnd) in listOf(55_000L to 60_000L, 60_000L to 60_000L, 65_000L to 65_000L)) {
            val data = project(listOf(point(observed)), listOf(point(90_000L)), initialSeconds = 60)
            assertEquals(0L, data.timeStartMs)
            assertEquals(expectedEnd, data.timeEndMs)
        }
    }
    @Test fun missingOrInvalidStoredDurationFallsBackAndMaxIntKeepsGridBounded() {
        for (stored in listOf(null, "60", 0, -1, Int.MAX_VALUE.toLong() + 1)) {
            assertEquals(40, FixedChartAxes.initialTimeSeconds(stored))
        }
        assertEquals(1, FixedChartAxes.initialTimeSeconds(1))
        val end = project(emptyList(), initialSeconds = Int.MAX_VALUE).timeEndMs
        assertEquals(Int.MAX_VALUE * 1_000L, end)
        val scale = ChartAxisScale.timeAxis(end)
        assertEquals(end, scale.majorTicks.last())
        assertTrue(scale.majorTicks.size <= 8 && scale.minorTicks.size <= 8)
    }
    @Test fun timeKeepsTheStartAndShowsTheWholeCupWhileMetricAxesStayFixed() {
        for (time in listOf(0L, 39_900L, 40_000L, 40_250L, 72_000L)) {
            val data = FourMetricChartProjection.build(listOf(point(time)), listOf(point(90000, temperature = 6000)),
                previousRanges = ChartMetric.entries.associateWith { ChartRange(-100f, 500f) },
                liveFlow = true, includeLiveWeight = true, fixedLiveAxes = true,
                expectedLiveRanges = ChartMetric.entries.associateWith { ChartRange(0f, 200f) },
                expandAllLiveAxes = true)
            assertEquals(0L, data.timeStartMs)
            assertEquals(maxOf(40_000L, time), data.timeEndMs)
            data.series.forEach { series ->
                assertEquals(FixedChartAxes.axis(series.metric).range, series.range)
                assertEquals(FixedChartAxes.axis(series.metric).step, series.step, 0f)
            }
        }
    }
    @Test fun overflowNeverClampsOrDiscardsSamplesAndSurvivesReturnInside() {
        val points = listOf(point(0, 160, 1010, 7900, 0, 6100), point(1000, weight = 550), point(41000))
        val data = project(points)
        val overflow = FixedChartAxes.overflow(data, selectableLiveMetrics.toSet())
        assertFalse(overflow.time)
        assertEquals(setOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.FLOW, ChartMetric.TEMPERATURE,
            ChartMetric.YIELD), overflow.metrics)
        assertEquals(16f, data.series.first { it.metric == ChartMetric.PRESSURE }.current.first().first().value, 0f)
        assertEquals(61f, data.series.first { it.metric == ChartMetric.YIELD }.current.first().first().value, 0f)
        assertEquals(41000L, data.observedThroughMs)
        assertEquals(3, data.series.first { it.metric == ChartMetric.WATER }.current.flatten().size)
    }
    @Test fun referenceOverflowAndHiddenMetricsAreSeparate() {
        val reference = listOf(point(41_000))
        val idle = project(emptyList(), reference)
        assertEquals(40_000L, idle.timeEndMs)
        assertTrue(FixedChartAxes.overflow(idle, selectableLiveMetrics.toSet(), reference = true).time)
        val data = project(listOf(point(1000)), reference)
        assertFalse(FixedChartAxes.overflow(data, selectableLiveMetrics.toSet()).any)
        assertTrue(FixedChartAxes.overflow(data, selectableLiveMetrics.toSet(), reference = true).time)
        val caughtUp = project(listOf(point(41_000)), reference)
        assertFalse(FixedChartAxes.overflow(caughtUp, selectableLiveMetrics.toSet(), reference = true).any)
        val excursion = project(listOf(point(1000, pressure = 151)))
        assertFalse(FixedChartAxes.overflow(excursion, setOf(ChartMetric.WATER)).any)
        assertEquals(setOf(ChartMetric.PRESSURE), FixedChartAxes.overflow(excursion, setOf(ChartMetric.PRESSURE)).metrics)
    }
    @Test fun boundaryValuesAreInsideAndNegativeYieldIsRetained() {
        assertFalse(FixedChartAxes.overflow(project(listOf(point(40000, 150, 1000, 10000, yield = 6000))),
            selectableLiveMetrics.toSet()).any)
        val negative = project(listOf(point(1000, yield = -100)))
        assertEquals(setOf(ChartMetric.YIELD), FixedChartAxes.overflow(negative, setOf(ChartMetric.YIELD)).metrics)
    }
    @Test fun pressureAndScaleFlowUseNewTicksWithoutChangingObservedValues() {
        val atLimit = project(listOf(point(0, weight = 0), point(1000, pressure = 150, weight = 500)))
        val pressure = atLimit.series.first { it.metric == ChartMetric.PRESSURE }
        val flow = atLimit.series.first { it.metric == ChartMetric.FLOW }
        assertEquals(ChartRange(0f, 15f), pressure.range)
        assertEquals(3f, pressure.step, 0f)
        assertEquals(ChartRange(0f, 5f), flow.range)
        assertEquals(1f, flow.step, 0f)
        val visible = setOf(ChartMetric.PRESSURE, ChartMetric.FLOW)
        assertFalse(FixedChartAxes.overflow(atLimit, visible).any)

        val beyond = project(listOf(point(0, weight = 0), point(1000, pressure = 151, weight = 501)))
        assertEquals(visible, FixedChartAxes.overflow(beyond, visible).metrics)
        assertEquals(15.1f, beyond.series.first { it.metric == ChartMetric.PRESSURE }.current.flatten().last().value, 0f)
        assertEquals(5.01f, beyond.series.first { it.metric == ChartMetric.FLOW }.current.flatten().last().value, 0f)
    }
    @Test fun historyIgnoresFixedLiveFlagAndMissingYieldIsNotInvented() {
        val points = listOf(point(72000, yield = null))
        assertEquals(FourMetricChartProjection.build(points, points),
            FourMetricChartProjection.build(points, points, fixedLiveAxes = true, fixedInitialTimeSeconds = 90))
        assertFalse(project(points).series.any { it.metric == ChartMetric.YIELD })
    }
}
