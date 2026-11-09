package io.openhoyi.mobile

import io.openhoyi.protocol.StartParameters
import org.junit.Assert.*
import org.junit.Test

class ChartAxisSeedTest {
    private fun pressureCurve(peakTenthsBar: Int, targetWeight: Int = 0, temperature: Int = 92,
        maximumWaterMl: Int = 108) =
        StartParameters(false, false, 1, 7, temperature, maximumWaterMl, false, 0,
            peakTenthsBar, peakTenthsBar, peakTenthsBar, peakTenthsBar, 350, 22, 170, 0, 0)

    @Test fun manualOrUnknownSelectionHasNoTargets() {
        assertTrue(ChartAxisSeed.from(null).isEmpty())
    }

    @Test fun pressureTargetUsesTheCurvePeakPlusTenPercentWithATenBarFloor() {
        val targets = ChartAxisSeed.ranges(peakPressureTenthsBar = 90, targetWeightHundredthsGram = null,
            temperatureC = null)
        assertEquals(ChartRange(0f, 10f), targets.getValue(ChartMetric.PRESSURE))
        // A low-pressure recipe still gets a readable 10 bar target.
        assertEquals(ChartRange(0f, 10f), ChartAxisSeed.ranges(peakPressureTenthsBar = 20,
            targetWeightHundredthsGram = null, temperatureC = null).getValue(ChartMetric.PRESSURE))
    }

    @Test fun weightTargetReservesFivePercentAboveTheRecipeWeight() {
        val targets = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = 3600, temperatureC = null)
        assertEquals(0f, targets.getValue(ChartMetric.WEIGHT).low, 0f)
        assertEquals(40f, targets.getValue(ChartMetric.WEIGHT).high, 0.001f)
    }

    @Test fun waterTargetUsesTheWeightMarginAndTheMachineWaterTarget() {
        // The machine water target wins when present (75 ml -> nice 80).
        val byMachine = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = 3600, temperatureC = null, maximumWaterMl = 75)
        assertEquals(80f, byMachine.getValue(ChartMetric.WATER).high, 0.001f)
        // Without a machine target the weight-derived estimate is used (36 g -> 49.6 -> nice 50).
        val byWeight = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = 3600, temperatureC = null)
        assertEquals(50f, byWeight.getValue(ChartMetric.WATER).high, 0.001f)
        val smallMachine = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = null, maximumWaterMl = 22)
        assertEquals(25f, smallMachine.getValue(ChartMetric.WATER).high, 0.001f)
        // No usable water information never invents a target.
        assertFalse(ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = 92).containsKey(ChartMetric.WATER))
    }

    @Test fun temperatureTargetIsTheSetpointPlusMinusTwoAndFlowTargetsTwo() {
        val targets = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = 92)
        assertEquals(ChartRange(90f, 94f), targets.getValue(ChartMetric.TEMPERATURE))
        assertEquals(ChartRange(0f, 2f), targets.getValue(ChartMetric.FLOW))
        // A measurable flow target still gets 50% headroom above the 2 g/s floor.
        assertEquals(ChartRange(0f, 3f), ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = 92,
            flowTargetTenthsMlPerSecond = 20).getValue(ChartMetric.FLOW))
    }

    @Test fun flowCurvesDoNotInventAPressureTarget() {
        val flow = StartParameters(true, true, 2, 7, 92, 70, true, 0, 20, 38, 20, 0, 160, 5, 400, 140, 0)
        val targets = ChartAxisSeed.from(CurveProfile("flow", "flow", "manual", 0, flow))
        assertFalse(targets.containsKey(ChartMetric.PRESSURE))
        assertEquals(ChartRange(90f, 94f), targets.getValue(ChartMetric.TEMPERATURE))
    }

    @Test fun curveProfileProducesExpectedTargetsFromItsParameters() {
        val profile = CurveProfile("capture-test", "cap", "manual", 2700,
            pressureCurve(peakTenthsBar = 90, targetWeight = 2700, temperature = 92), 0)
        val targets = ChartAxisSeed.from(profile)
        assertEquals(ChartRange(0f, 10f), targets[ChartMetric.PRESSURE])
        assertEquals(0f, targets.getValue(ChartMetric.WEIGHT).low, 0f)
        assertEquals(30f, targets.getValue(ChartMetric.WEIGHT).high, 0.001f)
        assertEquals(ChartRange(90f, 94f), targets[ChartMetric.TEMPERATURE])
        assertEquals(ChartRange(0f, 2f), targets[ChartMetric.FLOW])
        assertEquals(120f, targets.getValue(ChartMetric.WATER).high, 0.001f)
    }
}
