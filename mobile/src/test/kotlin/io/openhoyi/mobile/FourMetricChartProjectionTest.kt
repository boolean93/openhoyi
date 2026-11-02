package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class FourMetricChartProjectionTest {
    private fun point(ms: Long, pressure: Int = 90, water: Int = 123,
        temperature: Int = 9350, weight: Int? = 456, brewing: Boolean? = true) =
        ShotPoint(ms, pressure, 999, water, temperature, weight, 999, brewing)

    private fun FourMetricChartData.series(metric: ChartMetric) = series.single { it.metric == metric }

    @Test fun exactPhysicalValuesNeverUseFlow() {
        val data = FourMetricChartProjection.build(listOf(point(1000)), emptyList())
        assertEquals(listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.WEIGHT,
            ChartMetric.TEMPERATURE), data.series.map { it.metric })
        assertEquals(listOf(9f, 12.3f, 4.56f, 93.5f),
            data.series.map { it.current.single().single().value })
        assertEquals(listOf("bar", "ml", "g", "°C"), data.series.map { it.metric.unit })
        assertTrue(data.series.all { it.reference.isEmpty() })
    }

    @Test fun absentScaleOmitsWeightRatherThanInventingZero() {
        val data = FourMetricChartProjection.build(listOf(point(0, weight = null),
            point(1000, weight = null)), emptyList())
        assertEquals(listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.TEMPERATURE),
            data.series.map { it.metric })
        assertTrue(data.series.all { it.current.single().size == 2 })
    }

    @Test fun missingWeightSplitsSegmentsAndPreservesRawNegativeValues() {
        val data = FourMetricChartProjection.build(listOf(point(0, weight = 100),
            point(1000, weight = null), point(2000, weight = 200),
            point(3000, weight = -100)), emptyList())
        val weight = data.series(ChartMetric.WEIGHT)
        assertEquals(listOf(listOf(ChartValue(0, 1f)),
            listOf(ChartValue(2000, 2f), ChartValue(3000, -1f))), weight.current)
        assertTrue(weight.range.low <= -1f)
    }

    @Test fun gapBoundaryAllows1500MsButBreaksAt1501MsForEveryMetric() {
        val data = FourMetricChartProjection.build(listOf(point(0), point(1500), point(3001)), emptyList())
        data.series.forEach { series ->
            assertEquals(series.metric.name, listOf(2, 1), series.current.map { it.size })
            assertEquals(listOf(0L, 1500L, 3001L), series.current.flatten().map { it.elapsedMs })
        }
    }

    @Test fun nonBrewingPressureIsAbsentButOtherObservedValuesRemain() {
        val data = FourMetricChartProjection.build(listOf(point(0, brewing = false)), emptyList())
        assertTrue(data.series(ChartMetric.PRESSURE).current.isEmpty())
        assertEquals(listOf(12.3f, 4.56f, 93.5f),
            data.series.filter { it.metric != ChartMetric.PRESSURE }.map { it.current.single().single().value })
    }

    @Test fun nonBrewingPointBreaksPressureAndLegacyUnknownStillPlots() {
        val data = FourMetricChartProjection.build(listOf(point(0), point(1000, brewing = false),
            point(2000, brewing = null)), emptyList())
        assertEquals(listOf(listOf(ChartValue(0, 9f)), listOf(ChartValue(2000, 9f))),
            data.series(ChartMetric.PRESSURE).current)
    }

    @Test fun waterResetSplitsOnlyWaterAndWeightDropIsNotATare() {
        val samples = listOf(point(0, water = 100, weight = 400),
            point(1000, water = 200, weight = 300), point(2000, water = 50, weight = 200),
            point(3000, water = 60, weight = 100))
        val data = FourMetricChartProjection.build(samples, emptyList())
        assertEquals(listOf(2, 2), data.series(ChartMetric.WATER).current.map { it.size })
        assertEquals(listOf(10f, 20f, 5f, 6f), data.series(ChartMetric.WATER).current.flatten().map { it.value })
        assertEquals(listOf(2000L), data.waterResetsMs)
        assertTrue(data.referenceWaterResetsMs.isEmpty())
        assertEquals(4, data.series(ChartMetric.PRESSURE).current.single().size)
        assertEquals(listOf(4f, 3f, 2f, 1f), data.series(ChartMetric.WEIGHT).current.single().map { it.value })
    }

    @Test fun referenceKeepsIndependentSegmentsAndResetTimes() {
        val reference = listOf(point(0, water = 200), point(1000, water = 100),
            point(2501, water = 110, weight = null), point(3501, water = 120))
        val data = FourMetricChartProjection.build(listOf(point(0)), reference)
        assertEquals(listOf(1, 1, 2), data.series(ChartMetric.WATER).reference.map { it.size })
        assertEquals(listOf(2, 2), data.series(ChartMetric.PRESSURE).reference.map { it.size })
        assertEquals(listOf(2, 1), data.series(ChartMetric.WEIGHT).reference.map { it.size })
        assertEquals(listOf(1000L), data.referenceWaterResetsMs)
        assertTrue(data.waterResetsMs.isEmpty())
    }

    @Test fun longReferenceExtendsAxisWithoutExtendingCurrentObservation() {
        val current = listOf(point(0), point(1000))
        val reference = listOf(point(0), point(1000), point(41000))
        val data = FourMetricChartProjection.build(current, reference)
        assertEquals(1000L, data.observedThroughMs)
        assertEquals(41000L, data.timeEndMs)
        data.series.forEach {
            assertEquals(listOf(0L, 1000L), it.current.flatten().map { value -> value.elapsedMs })
            assertEquals(listOf(0L, 1000L, 41000L), it.reference.flatten().map { value -> value.elapsedMs })
        }
    }

    @Test fun emptyInputsHaveDefaultsButNeverFabricateSamples() {
        val data = FourMetricChartProjection.build(emptyList(), emptyList())
        assertNull(data.observedThroughMs)
        assertEquals(1000L, data.timeEndMs)
        assertEquals(listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.TEMPERATURE),
            data.series.map { it.metric })
        assertTrue(data.series.all { it.current.isEmpty() && it.reference.isEmpty() })
        assertTrue(data.waterResetsMs.isEmpty())
        assertTrue(data.referenceWaterResetsMs.isEmpty())
        assertEquals(listOf(ChartRange(0f, 12f), ChartRange(0f, 60f), ChartRange(80f, 100f)),
            data.series.map { it.range })
    }

    @Test fun referenceWeightAddsAxisWithoutAddingCurrentWeight() {
        val data = FourMetricChartProjection.build(emptyList(), listOf(point(41000, weight = -100)))
        assertNull(data.observedThroughMs)
        assertEquals(41000L, data.timeEndMs)
        assertTrue(data.series.all { it.current.isEmpty() })
        assertEquals(listOf(listOf(ChartValue(41000, -1f))), data.series(ChartMetric.WEIGHT).reference)
    }

    @Test fun defaultWeightRangeIsZeroToFifty() {
        assertEquals(ChartRange(0f, 50f), FourMetricChartProjection.build(listOf(point(0)),
            emptyList()).series(ChartMetric.WEIGHT).range)
    }

    @Test fun peaksAndNegativePressureExpandRangesWithoutClipping() {
        val data = FourMetricChartProjection.build(listOf(point(0, pressure = 180, water = 1000,
            weight = -100, temperature = 11000), point(1000, pressure = -5)), emptyList())
        assertEquals(listOf(ChartRange(-1f, 18f), ChartRange(0f, 100f), ChartRange(-10f, 50f),
            ChartRange(80f, 110f)), data.series.map { it.range })
        assertEquals(listOf(18f, -0.5f), data.series(ChartMetric.PRESSURE).current.single().map { it.value })
    }

    @Test fun referenceAlsoExpandsRangesAndPreviousSessionRangesNeverShrink() {
        val expanded = FourMetricChartProjection.build(emptyList(), listOf(point(0, pressure = 181,
            water = 1001, weight = -101, temperature = 11001)))
        val previous = expanded.series.associate { it.metric to it.range }
        val snapshot = previous.toMap()
        val later = FourMetricChartProjection.build(listOf(point(0)), emptyList(), previous)
        assertEquals(listOf(ChartRange(0f, 19f), ChartRange(0f, 110f), ChartRange(-10f, 50f),
            ChartRange(80f, 115f)), later.series.map { it.range })
        assertEquals(previous, snapshot)
        assertEquals(expanded.series.map { it.range }, later.series.map { it.range })
    }

    @Test fun largeNegativeWaterCannotRoundLowerBoundInsideObservedMinimum() {
        val water = FourMetricChartProjection.build(listOf(point(0, water = -1688510903),
            point(1000, water = 1342177000)), emptyList()).series(ChartMetric.WATER)
        val minimum = water.current.flatten().minOf { it.value }
        assertTrue("Lower bound ${water.range.low} clips observed minimum $minimum",
            water.range.low <= minimum)
    }

    @Test fun largePositiveWaterCannotRoundUpperBoundInsideObservedMaximum() {
        val water = FourMetricChartProjection.build(listOf(point(0, water = -1688510903),
            point(1000, water = 1342177000)), emptyList()).series(ChartMetric.WATER)
        val maximum = water.current.flatten().maxOf { it.value }
        assertTrue("Upper bound ${water.range.high} clips observed maximum $maximum",
            water.range.high >= maximum)
    }

    @Test fun negativeDuplicateAndReversedTimesAreRejectedForEachInputWithoutMutation() {
        val invalidInputs = listOf(listOf(point(-1)), listOf(point(0), point(0)),
            listOf(point(1000), point(0)))
        invalidInputs.forEach { invalid ->
            for (invalidIsCurrent in listOf(true, false)) {
                val current = (if (invalidIsCurrent) invalid else listOf(point(0))).toMutableList()
                val reference = (if (invalidIsCurrent) listOf(point(0)) else invalid).toMutableList()
                val currentBefore = current.map { it.copy() }
                val referenceBefore = reference.map { it.copy() }
                expectInvalid { FourMetricChartProjection.build(current, reference) }
                assertEquals(currentBefore, current)
                assertEquals(referenceBefore, reference)
            }
        }
    }

    @Test fun successfulProjectionLeavesInputSamplesUntouched() {
        val current = mutableListOf(point(0, water = 200), point(1000, water = 100))
        val reference = mutableListOf(point(0, weight = -100), point(2000, weight = null))
        val currentBefore = current.map { it.copy() }
        val referenceBefore = reference.map { it.copy() }
        FourMetricChartProjection.build(current, reference)
        assertEquals(currentBefore, current)
        assertEquals(referenceBefore, reference)
    }

    @Test fun invalidPreviousRangesAreRejected() {
        listOf(ChartRange(Float.NaN, 12f), ChartRange(0f, Float.POSITIVE_INFINITY),
            ChartRange(Float.NEGATIVE_INFINITY, 12f), ChartRange(12f, 12f), ChartRange(13f, 12f))
            .forEach { range ->
                expectInvalid { FourMetricChartProjection.build(emptyList(), emptyList(),
                    mapOf(ChartMetric.PRESSURE to range)) }
            }
    }

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Invalid sampling/ranges must be rejected, not silently reordered or repaired.
        }
    }
}
