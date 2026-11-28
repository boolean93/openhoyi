package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class LivePauseBaselineTest {
    private fun point(ms: Long, brewing: Boolean?, bits: Int?, pressure: Int = 90) =
        ShotPoint(ms,pressure,10,(ms/10).toInt(),9300,100,brewing=brewing,statusBits=bits)
    @Test fun confirmedContiguousPauseHasLiveZeroWithoutChangingRawOrHistoricalPressure() {
        val points = listOf(point(0,true,64),point(500,false,0,77),point(1000,false,0,66),point(1500,true,64))
        val old = points.toList()
        val live = FourMetricChartProjection.build(points,emptyList(),livePauseBaseline=true)
        assertEquals(setOf(500L,1000L),live.pauseBaselineMs)
        assertEquals(listOf(9f,0f,0f,9f),live.series.first { it.metric==ChartMetric.PRESSURE }.current.single().map { it.value })
        val historical = FourMetricChartProjection.build(points,emptyList())
        assertTrue(historical.pauseBaselineMs.isEmpty())
        assertEquals(2,historical.series.first { it.metric==ChartMetric.PRESSURE }.current.size)
        assertEquals(old,points); assertEquals(77,points[1].pressureTenthsBar)
    }
    @Test fun observedBloomUsesRawPressureInLiveCompletedAndReferenceTraces() {
        // A valve-open bloom frame reports the wait bit together with actual pressure.
        val points = listOf(point(3000, true, 81, 20), point(3300, false, 113, 2),
            point(3500, false, 113, 0), point(7100, false, 113, 0),
            point(7300, false, 113, 1), point(7500, true, 81, 24))
        val expected = listOf(listOf(2f, 0.2f, 0f), listOf(0f, 0.1f, 2.4f))
        for (live in listOf(true, false)) {
            val result = FourMetricChartProjection.build(points, points, livePauseBaseline = live, liveFlow = live)
            val pressure = result.series.single { it.metric == ChartMetric.PRESSURE }
            assertEquals(expected, pressure.current.map { segment -> segment.map { it.value } })
            assertEquals(expected, pressure.reference.map { segment -> segment.map { it.value } })
            assertTrue(result.pauseBaselineMs.isEmpty())
        }
    }

    @Test fun chartAndInspectionDistinguishAnObservedBloomFromOlderRecords() {
        val points = listOf(point(0, false, 113, 0), point(200, true, 81, 20),
            point(400, false, null, 0), point(600, true, 81, 24))
        val result = FourMetricChartProjection.build(points, emptyList())
        val pressure = result.series.single { it.metric == ChartMetric.PRESSURE }
        assertEquals(listOf(listOf(0L, 200L), listOf(600L)), pressure.current.map { it.map { sample -> sample.elapsedMs } })
        assertEquals(0f, ChartInspection.values(points.first())[ChartMetric.PRESSURE])
        assertNull(ChartInspection.values(points[2])[ChartMetric.PRESSURE])
        assertEquals(listOf(0f, 2f, null, 2.4f), RealtimeShotProjection.values(points))
    }

    @Test fun neverGuessesPauseFromLegacyFalseBrewWaitOrGap() {
        for (points in listOf(
            listOf(point(0,false,0),point(500,false,0)),
            listOf(point(0,true,64),point(500,false,null)),
            listOf(point(0,true,64),point(500,false,32)),
            listOf(point(0,true,64),point(1501,false,0),point(2000,false,0)),
            listOf(point(0,true,64),point(500,null,null),point(1000,false,0)))) {
            assertTrue(FourMetricChartProjection.pauseBaselines(points).isEmpty())
        }
    }
    @Test fun visiblePauseEdgesRemainStepsNotPrematurePressureRamps() {
        val values = listOf(ChartValue(0,9f),ChartValue(500,0f),ChartValue(1000,0f),ChartValue(1500,8f))
        val pauses = setOf(500L,1000L)
        assertEquals(9f,LiveChartGeometry.visiblePoint(values,499,pauses)!!.value,0f)
        assertEquals(0f,LiveChartGeometry.visiblePoint(values,500,pauses)!!.value,0f)
        assertEquals(0f,LiveChartGeometry.visiblePoint(values,1499,pauses)!!.value,0f)
        assertEquals(8f,LiveChartGeometry.visiblePoint(values,1500,pauses)!!.value,0f)
    }
}
