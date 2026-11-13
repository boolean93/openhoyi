package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class FixedChartAxesTest {
    private fun point(ms: Long, pressure: Int = 90, water: Int = 350, temperature: Int = 9200,
        weight: Int = 3600, yield: Int? = 3600) = ShotPoint(ms, pressure, 0, water, temperature, weight,
            0, true, beverageHundredthsGram = yield)
    private fun project(points: List<ShotPoint>, reference: List<ShotPoint> = emptyList()) =
        FourMetricChartProjection.build(points, reference, liveFlow = true, includeLiveWeight = true,
            fixedLiveAxes = true)

    @Test fun defaultOffRetainsOriginalDynamicAxes() {
        assertFalse(FixedChartAxes.DEFAULT)
        for (time in listOf(5000L, 12000L)) {
            val points = listOf(point(time))
            assertEquals(FourMetricChartProjection.build(points, emptyList(), liveFlow = true),
                FourMetricChartProjection.build(points, emptyList(), liveFlow = true, fixedLiveAxes = false))
            assertEquals(maxOf(10000L, time), FourMetricChartProjection.build(points, emptyList(), liveFlow = true).timeEndMs)
        }
    }
    @Test fun everyAxisIsConstantAcrossTimeSeedsPriorRangesAndReference() {
        for (time in listOf(0L, 1000L, 10000L, 30000L, 39000L, 72000L)) {
            val data = FourMetricChartProjection.build(listOf(point(time)), listOf(point(90000, temperature = 6000)),
                previousRanges = ChartMetric.entries.associateWith { ChartRange(-100f, 500f) },
                liveFlow = true, includeLiveWeight = true, fixedLiveAxes = true,
                expectedLiveRanges = ChartMetric.entries.associateWith { ChartRange(0f, 200f) },
                expandAllLiveAxes = true)
            assertEquals(0L, data.timeStartMs)
            assertEquals(40000L, data.timeEndMs)
            data.series.forEach { series ->
                assertEquals(FixedChartAxes.axis(series.metric).range, series.range)
                assertEquals(FixedChartAxes.axis(series.metric).step, series.step, 0f)
            }
        }
    }
    @Test fun overflowNeverClampsOrDiscardsSamplesAndSurvivesReturnInside() {
        val points = listOf(point(0, 130, 1010, 7900, 0, 6100), point(1000, weight = 500), point(41000))
        val data = project(points)
        val overflow = FixedChartAxes.overflow(data, selectableLiveMetrics.toSet())
        assertTrue(overflow.time)
        assertEquals(setOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.FLOW, ChartMetric.TEMPERATURE,
            ChartMetric.YIELD), overflow.metrics)
        assertEquals(13f, data.series.first { it.metric == ChartMetric.PRESSURE }.current.first().first().value, 0f)
        assertEquals(61f, data.series.first { it.metric == ChartMetric.YIELD }.current.first().first().value, 0f)
        assertEquals(41000L, data.observedThroughMs)
        assertEquals(3, data.series.first { it.metric == ChartMetric.WATER }.current.flatten().size)
    }
    @Test fun referenceOverflowAndHiddenMetricsAreSeparate() {
        val data = project(listOf(point(1000)), listOf(point(41000, temperature = 7900)))
        assertFalse(FixedChartAxes.overflow(data, selectableLiveMetrics.toSet()).any)
        assertTrue(FixedChartAxes.overflow(data, selectableLiveMetrics.toSet(), reference = true).any)
        val excursion = project(listOf(point(1000, pressure = 130)))
        assertFalse(FixedChartAxes.overflow(excursion, setOf(ChartMetric.WATER)).any)
        assertEquals(setOf(ChartMetric.PRESSURE), FixedChartAxes.overflow(excursion, setOf(ChartMetric.PRESSURE)).metrics)
    }
    @Test fun boundaryValuesAreInsideAndNegativeYieldIsRetained() {
        assertFalse(FixedChartAxes.overflow(project(listOf(point(40000, 120, 1000, 10000, yield = 6000))),
            selectableLiveMetrics.toSet()).any)
        val negative = project(listOf(point(1000, yield = -100)))
        assertEquals(setOf(ChartMetric.YIELD), FixedChartAxes.overflow(negative, setOf(ChartMetric.YIELD)).metrics)
    }
    @Test fun historyIgnoresFixedLiveFlagAndMissingYieldIsNotInvented() {
        val points = listOf(point(72000, yield = null))
        assertEquals(FourMetricChartProjection.build(points, points),
            FourMetricChartProjection.build(points, points, fixedLiveAxes = true))
        assertFalse(project(points).series.any { it.metric == ChartMetric.YIELD })
    }
}
