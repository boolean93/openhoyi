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
        assertEquals(37.8f, targets.getValue(ChartMetric.WEIGHT).high, 0.001f)
    }

    @Test fun waterTargetUsesTheWeightMarginAndTheMachineWaterTarget() {
        val byWeight = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = 3600, temperatureC = null, maximumWaterMl = 108)
        assertEquals(49.6f, byWeight.getValue(ChartMetric.WATER).high, 0.001f)
        val byMachine = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = null, maximumWaterMl = 22)
        assertEquals(23.1f, byMachine.getValue(ChartMetric.WATER).high, 0.001f)
        // No usable water information never invents a target.
        assertFalse(ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = 92).containsKey(ChartMetric.WATER))
    }

    @Test fun temperatureTargetIsTheSetpointPlusMinusFiveAndFlowDefaultsToFour() {
        val targets = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = 92)
        assertEquals(ChartRange(87f, 97f), targets.getValue(ChartMetric.TEMPERATURE))
        assertEquals(ChartRange(0f, 4f), targets.getValue(ChartMetric.FLOW))
    }

    @Test fun flowCurvesDoNotInventAPressureTarget() {
        val flow = StartParameters(true, true, 2, 7, 92, 70, true, 0, 20, 38, 20, 0, 160, 5, 400, 140, 0)
        val targets = ChartAxisSeed.from(CurveProfile("flow", "flow", "manual", 0, flow))
        assertFalse(targets.containsKey(ChartMetric.PRESSURE))
        assertEquals(ChartRange(87f, 97f), targets.getValue(ChartMetric.TEMPERATURE))
    }

    @Test fun curveProfileProducesExpectedTargetsFromItsParameters() {
        val profile = CurveProfile("capture-test", "cap", "manual", 2700,
            pressureCurve(peakTenthsBar = 90, targetWeight = 2700, temperature = 92), 0)
        val targets = ChartAxisSeed.from(profile)
        assertEquals(ChartRange(0f, 10f), targets[ChartMetric.PRESSURE])
        assertEquals(0f, targets.getValue(ChartMetric.WEIGHT).low, 0f)
        assertEquals(28.35f, targets.getValue(ChartMetric.WEIGHT).high, 0.001f)
        assertEquals(ChartRange(87f, 97f), targets[ChartMetric.TEMPERATURE])
        assertEquals(ChartRange(0f, 4f), targets[ChartMetric.FLOW])
    }
}
