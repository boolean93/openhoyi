package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HistoryPlotBudgetTest {
    @Test fun liveBandsPreserveTheActualBoundsAndReserveNinetySixDp() {
        val line=61.2504f;val density=2.25f
        val height=HistoryPlotBudget.minimumHeight(line,density,true,96f)
        assertEquals(445,height)
        val actual=height-HistoryPlotBudget.top(line,density,true)-HistoryPlotBudget.bottom(line,density,true)
        assertTrue(actual/density>=96f)
        assertTrue((actual-1)/density<96f)
    }
    @Test fun historicalBoundsKeepTheirDistinctAxisBands() {
        val line=50f;val density=2f
        assertEquals(107f,HistoryPlotBudget.top(line,density,false),0f)
        assertEquals(173f,HistoryPlotBudget.bottom(line,density,false),0f)
        assertEquals(472,HistoryPlotBudget.minimumHeight(line,density,false,96f))
        assertEquals(382,HistoryPlotBudget.minimumHeight(line,density,true,96f))
    }
    @Test fun bothModesKeepThePlotFloorAtFractionalDensityAndLargeFonts() {
        for(density in listOf(1f,2.25f,3.5f))for(line in listOf(17.1f,61.2504f,90.75f))for(live in listOf(false,true)) {
            val height=HistoryPlotBudget.minimumHeight(line,density,live,96f)
            assertTrue(height-HistoryPlotBudget.top(line,density,live)-HistoryPlotBudget.bottom(line,density,live)>=96*density)
        }
    }
    @Test fun callerPlotFloorScalesWithoutChangingAxisBands() {
        assertEquals(176,HistoryPlotBudget.minimumHeight(20f,1f,true,96f))
        assertEquals(236,HistoryPlotBudget.minimumHeight(20f,1f,true,156f))
    }
    @Test fun invalidMeasurementsFailInsteadOfAdvertisingAPlotFloor() {
        for(values in listOf(floatArrayOf(0f,1f,96f),floatArrayOf(20f,0f,96f),floatArrayOf(20f,1f,-1f))) {
            try {HistoryPlotBudget.minimumHeight(values[0],values[1],true,values[2]);fail("invalid budget accepted")}
            catch(expected:IllegalArgumentException) { /* fail closed */ }
        }
    }
}
