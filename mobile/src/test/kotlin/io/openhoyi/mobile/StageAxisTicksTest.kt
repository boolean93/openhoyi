package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class StageAxisTicksTest {
    @Test fun fullHeightKeepsOriginalScale() {
        assertEquals(listOf(0, 3, 6, 9, 12), StageAxisTicks.forHeight(240f, 24f))
    }
    @Test fun crowdedAxisKeepsEndpointsWithoutOverlappingLabels() {
        for (gap in listOf(18f, 24f, 32f, 48f)) for (height in 1..300) {
            val ticks = StageAxisTicks.forHeight(height.toFloat(), gap)
            if (height < gap) assertTrue(ticks.isEmpty()) else {
                assertEquals(0, ticks.first())
                assertEquals(12, ticks.last())
                ticks.zipWithNext().forEach { (a, b) ->
                    assertTrue("height=$height gap=$gap ticks=$ticks", height * (b-a) / 12f >= gap)
                }
            }
        }
    }
    @Test fun narrowPlotUsesLegibleIntermediateTicks() {
        assertEquals(listOf(0, 6, 12), StageAxisTicks.forHeight(80f, 30f))
        assertEquals(listOf(0, 12), StageAxisTicks.forHeight(45f, 30f))
        assertEquals(emptyList<Int>(), StageAxisTicks.forHeight(0f, 30f))
    }
}
