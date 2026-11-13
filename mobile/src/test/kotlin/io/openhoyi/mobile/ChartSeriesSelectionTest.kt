package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ChartSeriesSelectionTest {
    @Test fun defaultsPreserveFourCurvesAndInvalidPreferencesRecover() {
        assertEquals(liveMetrics.toSet(), ChartSeriesSelection.decode(null))
        assertEquals(liveMetrics.toSet(), ChartSeriesSelection.decode(setOf("obsolete")))
        assertEquals(setOf(ChartMetric.YIELD), ChartSeriesSelection.decode(setOf("YIELD", "obsolete")))
    }
    @Test fun cannotHideTheLastCurveAndSelectionDoesNotModifySamples() {
        val selected = setOf(ChartMetric.YIELD)
        assertEquals(selected, ChartSeriesSelection.toggle(selected, ChartMetric.YIELD))
        assertEquals(selected + ChartMetric.WATER, ChartSeriesSelection.toggle(selected, ChartMetric.WATER))
    }
    private fun point(ms: Long, weight: Int?) = ShotPoint(ms, 10, 10, 10, 9200, weight, brewing=true, beverageHundredthsGram=weight,
            beverageSource=weight?.let { BeverageWeightSource.CONFIRMED })
    @Test fun optionalWeightUsesGramsAndPreservesOriginalFourProjections() {
        val points = listOf(point(0, 0), point(500, 200), point(1000, 5000))
        val old = FourMetricChartProjection.build(points, emptyList(), liveFlow=true)
        val all = FourMetricChartProjection.build(points, emptyList(), liveFlow=true, includeLiveWeight=true)
        assertEquals(old, all.copy(series=all.series.filter { it.metric != ChartMetric.YIELD }))
        assertEquals(50f, all.series.single { it.metric==ChartMetric.YIELD }.current.last().last().value)
        assertEquals(1f, all.series.single { it.metric==ChartMetric.WATER }.current.last().last().value)
        assertEquals(50f, ChartInspection.values(points.last(),true,includeLiveWeight=true)[ChartMetric.YIELD])
    }
    @Test fun hiddenWeightCannotChangeOriginalAxisAnimationKindOrDuration() {
        val from = LiveAxisAnimation.AxisState(mapOf(ChartMetric.PRESSURE to ChartRange(0f, 2f),
            ChartMetric.YIELD to ChartRange(0f, 10f)), mapOf(ChartMetric.PRESSURE to 0.5f, ChartMetric.YIELD to 2f), 10000)
        val to = from.copy(ranges = from.ranges + (ChartMetric.YIELD to ChartRange(0f, 100f)), timeEndMs = 10500)
        val plan = LiveAxisAnimation.transition(ChartSeriesSelection.animationState(from, ChartSeriesSelection.defaults),
            ChartSeriesSelection.animationState(to, ChartSeriesSelection.defaults), 250)
        assertEquals(LiveAxisAnimation.TransitionKind.SCROLL, plan.kind)
        assertEquals(250L, plan.durationMs)
    }
    @Test fun missingWeightIsNeverSynthesizedAndHistoryRemainsCompatible() {
        val points = listOf(point(0,null),point(500,null))
        assertFalse(FourMetricChartProjection.build(points,emptyList(),liveFlow=true,includeLiveWeight=true)
            .series.any { it.metric == ChartMetric.YIELD })
        assertEquals(FourMetricChartProjection.build(points,emptyList()),
            FourMetricChartProjection.build(points,emptyList(),includeLiveWeight=true))
        val gaps = listOf(point(0,100),point(500,null),point(1000,300))
        val weight = FourMetricChartProjection.build(gaps,emptyList(),liveFlow=true,includeLiveWeight=true)
            .series.single { it.metric == ChartMetric.YIELD }
        assertEquals(listOf(listOf(1f),listOf(3f)), weight.current.map { it.map { value -> value.value } })
    }
}
