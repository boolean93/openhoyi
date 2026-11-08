package io.openhoyi.mobile

import io.openhoyi.protocol.StartParameters
import org.junit.Assert.*
import org.junit.Test

class ChartAxisSeedTest {
    private fun pressureCurve(peakTenthsBar: Int, targetWeight: Int = 0, temperature: Int = 92) =
        StartParameters(false, false, 1, 7, temperature, 108, false, 0, peakTenthsBar, peakTenthsBar,
            peakTenthsBar, peakTenthsBar, 350, 22, 170, 0, 0)

    @Test fun manualOrUnknownSelectionHasNoSeedAndKeepsMetricDefaults() {
        assertTrue(ChartAxisSeed.from(null).isEmpty())
    }

    @Test fun pressureSeedUsesTheCurvePeakPlusHeadroom() {
        val seed = ChartAxisSeed.ranges(peakPressureTenthsBar = 90, targetWeightHundredthsGram = null,
            temperatureC = null)
        assertEquals(ChartRange(0f, 10f), seed.getValue(ChartMetric.PRESSURE))
        assertEquals(setOf(ChartMetric.PRESSURE), seed.keys)
    }

    @Test fun weightAndWaterSeedFollowTheRecipeTarget() {
        val seed = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = 2700, temperatureC = null)
        assertEquals(ChartRange(0f, 32f), seed.getValue(ChartMetric.WEIGHT))
        assertEquals(ChartRange(0f, 50.5f), seed.getValue(ChartMetric.WATER))
    }

    @Test fun temperatureSeedIsTheSetpointPlusMinusFiveDegrees() {
        val seed = ChartAxisSeed.ranges(peakPressureTenthsBar = null,
            targetWeightHundredthsGram = null, temperatureC = 93)
        assertEquals(ChartRange(88f, 98f), seed.getValue(ChartMetric.TEMPERATURE))
    }

    @Test fun tinyTargetsStillKeepTheMinimumSpan() {
        val seed = ChartAxisSeed.ranges(peakPressureTenthsBar = 5,
            targetWeightHundredthsGram = 100, temperatureC = null)
        assertTrue(seed.getValue(ChartMetric.PRESSURE).let { it.high - it.low } >=
            ChartMetric.PRESSURE.minSpan)
        assertTrue(seed.getValue(ChartMetric.WEIGHT).let { it.high - it.low } >=
            ChartMetric.WEIGHT.minSpan)
        assertTrue(seed.getValue(ChartMetric.WATER).let { it.high - it.low } >=
            ChartMetric.WATER.minSpan)
    }

    @Test fun curveProfileProducesPressureWeightWaterAndTemperatureSeeds() {
        val profile = CurveProfile("capture-test", "cap", "manual", 2700,
            pressureCurve(peakTenthsBar = 90, targetWeight = 2700), 0)
        val seed = ChartAxisSeed.from(profile)
        assertEquals(ChartRange(0f, 10f), seed[ChartMetric.PRESSURE])
        assertEquals(ChartRange(0f, 32f), seed[ChartMetric.WEIGHT])
        assertEquals(ChartRange(0f, 50.5f), seed[ChartMetric.WATER])
        assertEquals(ChartRange(87f, 97f), seed[ChartMetric.TEMPERATURE])
        assertFalse(seed.containsKey(ChartMetric.FLOW))
    }

    @Test fun flowCurvesDoNotInventAPressurePeak() {
        val flow = StartParameters(true, true, 2, 7, 92, 70, true, 0, 20, 38, 20, 0, 160, 5, 400, 140, 0)
        assertFalse(ChartAxisSeed.ranges(peakPressureTenthsBar = null, targetWeightHundredthsGram = null,
            temperatureC = null).containsKey(ChartMetric.PRESSURE))
        assertTrue(ChartAxisSeed.from(CurveProfile("flow", "flow", "manual", 0, flow)).containsKey(
            ChartMetric.TEMPERATURE))
    }
}
