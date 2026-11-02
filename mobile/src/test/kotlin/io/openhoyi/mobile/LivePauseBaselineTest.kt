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
