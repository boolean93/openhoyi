package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry

class CupFlowProjectionTest {
    private fun point(ms: Long, weight: Int?) = ShotPoint(ms, 80, 0, 0, 9300, weight)
    @Test fun derivesGramsPerSecondFromWeightNotDeviceFlowField() {
        val points = listOf(point(0, 1000), point(500, 1100), point(1000, 1200))
        val flow = CupFlowProjection.values(points)
        assertNull(flow.first())
        assertEquals(2f, flow.last()!!, .001f)
    }
    @Test fun missingNegativeResetAndLongGapsBreakTheEstimate() {
        assertNull(CupFlowProjection.values(listOf(point(0, 1000), point(500, null), point(1000, 1200))).last())
        assertNull(CupFlowProjection.values(listOf(point(0, 1000), point(500, 0))).last())
        assertNull(CupFlowProjection.values(listOf(point(0, -100), point(500, 100))).last())
        assertNull(CupFlowProjection.values(listOf(point(0, 1000), point(2000, 1200))).last())
    }
    @Test fun continuousEightHundredMsSamplesKeepRealTwoGramPerSecondFlow() {
        val flow=CupFlowProjection.values(listOf(point(0,0),point(800,160),point(1600,320),point(2400,480)))
        assertNull(flow.first())
        flow.drop(1).forEach {assertNotNull(it);assertEquals(2f,it!!,.001f)}
    }
    @Test fun nearestOneSecondBaselineRetainsShortestAndFreshnessBoundaries() {
        assertNull(CupFlowProjection.values(listOf(point(0,0),point(249,50))).last())
        assertEquals(2f,CupFlowProjection.values(listOf(point(0,0),point(250,50))).last()!!,.001f)
        assertEquals(2f,CupFlowProjection.values(listOf(point(0,0),point(1500,300))).last()!!,.001f)
        assertNull(CupFlowProjection.values(listOf(point(0,0),point(1501,300))).last())
        assertNull(CupFlowProjection.values(listOf(point(500,100),point(500,120))).last())
        assertNull(CupFlowProjection.values(listOf(point(500,100),point(400,120))).last())
    }
    @Test fun preheatRetainsRawWeightButCannotBridgeIntoBrewingFlow() {
        val points=listOf(point(0,0).copy(brewing=false),point(800,160).copy(brewing=false),
            point(1600,320).copy(brewing=true),point(2400,480).copy(brewing=true),
            point(3200,640).copy(brewing=false),point(4000,800).copy(brewing=true))
        val flow=CupFlowProjection.values(points)
        assertEquals(listOf(null,null,null,2f,null,null),flow)
        assertEquals(6,points.size);assertEquals(800,points.last().weightHundredthsGram)
    }
    @Test fun preheatDoesNotEstablishCurveUse() {
        val frame = ExtractionTelemetry(7, 1, 80, 10, 9300, 20, 64, 0, ByteFrame(byteArrayOf()))
        assertTrue(CurveUseEvidence.matches(frame))
        assertFalse(CurveUseEvidence.matches(frame.copy(statusBits = 64 or 32)))
        assertFalse(CurveUseEvidence.matches(frame.copy(statusBits = 0)))
    }
}
