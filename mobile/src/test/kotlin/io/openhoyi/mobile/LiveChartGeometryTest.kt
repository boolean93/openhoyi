package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class LiveChartGeometryTest {
    private fun value(c: LiveChartGeometry.Cubic, t: Double): Double {
        val u = 1 - t
        return u*u*u*c.start.value + 3*u*u*t*c.control1.value + 3*u*t*t*c.control2.value + t*t*t*c.end.value
    }
    @Test fun preservesEveryObservedEndpointAndLocalExtremaWithoutOvershoot() {
        val samples = listOf(0f, 9f, 1f, 1f, 12f, -3f, 2f).mapIndexed { i, v -> ChartValue(i * 700L, v) }
        val curves = LiveChartGeometry.cubics(samples)
        assertEquals(samples.zipWithNext(), curves.map { it.start to it.end })
        curves.forEach { c ->
            val low = minOf(c.start.value, c.end.value) - .00001
            val high = maxOf(c.start.value, c.end.value) + .00001
            for (step in 0..100) assertTrue(value(c, step / 100.0) in low..high)
        }
    }
    @Test fun monotoneSegmentStaysMonotoneForIrregularSampleSpacing() {
        val samples = listOf(ChartValue(0,0f), ChartValue(10,1f), ChartValue(1000,2f), ChartValue(1400,20f))
        LiveChartGeometry.cubics(samples).forEach { c ->
            val values = (0..100).map { value(c, it/100.0) }
            assertTrue(values.zipWithNext().all { (a,b) -> a <= b + .00001 })
        }
        assertTrue(LiveChartGeometry.cubics(samples.take(1)).isEmpty())
    }
    @Test fun doesNotJoinEvidenceSegmentsOrChangeTheirObservations() {
        val points = listOf(
            ShotPoint(0,90,100,100,9300,0,null,true),
            ShotPoint(1000,80,100,200,9300,null,null,true),
            ShotPoint(3000,70,100,20,9300,100,null,true),
            ShotPoint(4000,60,100,40,9300,200,null,true))
        val original = points.toList()
        val projection = FourMetricChartProjection.build(points, emptyList())
        for (series in projection.series) for (segment in series.current) {
            val curves = LiveChartGeometry.cubics(segment)
            assertEquals((segment.size-1).coerceAtLeast(0), curves.size)
            assertTrue(curves.all { it.end.elapsedMs-it.start.elapsedMs <= 1500 })
        }
        assertEquals(2, projection.series.first { it.metric==ChartMetric.WATER }.current.size)
        assertEquals(2, projection.series.first { it.metric==ChartMetric.WEIGHT }.current.size)
        assertEquals(original, points)
    }
    @Test fun labelsAtBothExtremesMoveInwardWithoutColliding() {
        for (targets in listOf(listOf(0f,0f,100f,100f),listOf(0f,0f,0f,0f),listOf(100f,100f,100f,100f))) {
            val centers = requireNotNull(LiveChartGeometry.labelCenters(targets,10f,90f,20f))
            assertTrue(centers.first() >= 10f && centers.last() <= 90f)
            assertTrue(centers.zipWithNext().all { (a,b) -> b-a >= 20f })
        }
        assertEquals(listOf(10f,30f,70f,90f),LiveChartGeometry.labelCenters(listOf(0f,0f,100f,100f),10f,90f,20f))
        assertNull(LiveChartGeometry.labelCenters(listOf(0f,0f,100f,100f),10f,60f,20f))
    }
    @Test fun timeBudgetIsStableWithinThirtySecondBlocksAndIncludesLongReferences() {
        assertEquals(30000L, LiveChartGeometry.timeEnd(0))
        assertEquals(30000L, LiveChartGeometry.timeEnd(24600))
        assertEquals(30000L, LiveChartGeometry.timeEnd(30000))
        assertEquals(60000L, LiveChartGeometry.timeEnd(30001))
        assertEquals(90000L, LiveChartGeometry.timeEnd(81000))
    }
}
