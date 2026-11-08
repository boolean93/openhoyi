package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class FourMetricChartProjectionTest {
    private fun point(ms: Long, pressure: Int = 90, water: Int = 123,
        temperature: Int = 9350, weight: Int? = 456, brewing: Boolean? = true) =
        ShotPoint(ms, pressure, 999, water, temperature, weight, 999, brewing)

    private fun FourMetricChartData.series(metric: ChartMetric) = series.single { it.metric == metric }

    @Test fun liveUsesScaleDerivativeForCurrentAndReferenceWithoutChangingHistory() {
        val points = listOf(point(0, weight = 0), point(500, weight = 100), point(1000, weight = 200))
        val live = FourMetricChartProjection.build(points, points, liveFlow = true)
        assertEquals(liveMetrics, live.series.map { it.metric })
        val flow = live.series(ChartMetric.FLOW)
        assertEquals(listOf(ChartValue(500, 2f), ChartValue(1000, 2f)), flow.current.single())
        assertEquals(flow.current, flow.reference)
        assertEquals(ChartRange(0f, 2f), flow.range)
        val history = FourMetricChartProjection.build(points, points)
        assertEquals(historicalMetrics, history.series.map { it.metric })
        assertEquals(listOf(0f, 1f, 2f), history.series(ChartMetric.WEIGHT).current.single().map { it.value })
        assertEquals(listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.FLOW, ChartMetric.TEMPERATURE),
            ChartInspection.values(points.last(), true, 2f).keys.toList())
    }

    @Test fun liveMissingScaleKeepsEmptyFlowAxisAndDerivativeGaps() {
        val empty = FourMetricChartProjection.build(listOf(point(0, weight = null)), emptyList(), liveFlow = true)
        assertEquals(liveMetrics, empty.series.map { it.metric })
        assertTrue(empty.series(ChartMetric.FLOW).current.isEmpty())
        assertEquals(ChartRange(0f, 1f), empty.series(ChartMetric.FLOW).range)
        val points = listOf(point(0, weight = 0), point(500, weight = 100),
            point(1000, weight = null), point(1500, weight = 200), point(2000, weight = 300),
            point(2500, weight = 100), point(3000, weight = 200),
            point(3500, weight = 300, brewing = false), point(4000, weight = 400), point(4500, weight = 500))
        val live = FourMetricChartProjection.build(points, emptyList(), liveFlow = true)
        assertEquals(listOf(listOf(500L), listOf(2000L), listOf(3000L), listOf(4500L)),
            live.series(ChartMetric.FLOW).current.map { segment -> segment.map { it.elapsedMs } })
    }

    @Test fun liveFlowRangeExpandsAndDoesNotShrinkWithinShot() {
        val high = FourMetricChartProjection.build(listOf(point(0, weight = 0), point(1000, weight = 620)),
            emptyList(), liveFlow = true)
        assertEquals(ChartRange(0f, 8f), high.series(ChartMetric.FLOW).range)
        val next = FourMetricChartProjection.build(emptyList(), emptyList(),
            high.series.associate { it.metric to it.range }, liveFlow = true)
        assertEquals(high.series(ChartMetric.FLOW).range, next.series(ChartMetric.FLOW).range)
    }

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
        assertEquals(50000L, data.timeEndMs)
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
        assertEquals(listOf(ChartRange(0f, 10f), ChartRange(0f, 40f), ChartRange(88f, 98f)),
            data.series.map { it.range })
    }

    @Test fun referenceWeightAddsAxisWithoutAddingCurrentWeight() {
        val data = FourMetricChartProjection.build(emptyList(), listOf(point(41000, weight = -100)))
        assertNull(data.observedThroughMs)
        assertEquals(50000L, data.timeEndMs)
        assertTrue(data.series.all { it.current.isEmpty() })
        assertEquals(listOf(listOf(ChartValue(41000, -1f))), data.series(ChartMetric.WEIGHT).reference)
    }

    @Test fun defaultWeightRangeKeepsTheMinimumSpanAnchoredAtZero() {
        assertEquals(ChartRange(0f, 10f), FourMetricChartProjection.build(listOf(point(0)),
            emptyList()).series(ChartMetric.WEIGHT).range)
    }

    @Test fun peaksAndNegativePressureExpandRangesWithoutClipping() {
        val data = FourMetricChartProjection.build(listOf(point(0, pressure = 180, water = 1000,
            weight = -100, temperature = 11000), point(1000, pressure = -5)), emptyList())
        assertEquals(listOf(ChartRange(-5f, 20f), ChartRange(0f, 100f), ChartRange(-2f, 10f),
            ChartRange(90f, 110f)), data.series.map { it.range })
        assertEquals(listOf(18f, -0.5f), data.series(ChartMetric.PRESSURE).current.single().map { it.value })
    }

    @Test fun referenceAlsoExpandsRangesAndPreviousSessionRangesNeverShrink() {
        val expanded = FourMetricChartProjection.build(emptyList(), listOf(point(0, pressure = 181,
            water = 1001, weight = -101, temperature = 11001)))
        val previous = expanded.series.associate { it.metric to it.range }
        val snapshot = previous.toMap()
        val later = FourMetricChartProjection.build(listOf(point(0)), emptyList(), previous)
        // The same-session ranges never shrink: every later bound contains the earlier bound.
        expanded.series.zip(later.series).forEach { (before, after) ->
            assertTrue(after.range.low <= before.range.low)
            assertTrue(after.range.high >= before.range.high)
            after.current.flatten().forEach { value ->
                assertTrue(value.value in after.range.low..after.range.high)
            }
        }
        assertEquals(previous, snapshot)
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

    @Test fun liveStartsFromSmallPerMetricRangesBeforeAnySample() {
        val live = FourMetricChartProjection.build(emptyList(), emptyList(), liveFlow = true)
        assertEquals(ChartRange(0f, 2f), live.series(ChartMetric.PRESSURE).range)
        assertEquals(ChartRange(0f, 10f), live.series(ChartMetric.WATER).range)
        assertEquals(ChartRange(0f, 1f), live.series(ChartMetric.FLOW).range)
        assertEquals(ChartRange(90f, 95f), live.series(ChartMetric.TEMPERATURE).range)
    }

    @Test fun livePressureAxisExpandsInObservedDrivenStepsWithProgress() {
        var previous = FourMetricChartProjection.build(emptyList(), emptyList(), liveFlow = true)
            .series.associate { it.metric to it.range }
        var lastHigh = previous.getValue(ChartMetric.PRESSURE).high
        val highs = mutableListOf(lastHigh)
        for (pressureTenths in listOf(30, 50, 60, 90)) {
            val data = FourMetricChartProjection.build(listOf(point(0, pressure = 0),
                point(1000, pressure = pressureTenths)), emptyList(), previous, liveFlow = true)
            val range = data.series(ChartMetric.PRESSURE).range
            assertTrue("observed ${pressureTenths / 10f} bar must be inside $range",
                range.low <= 0f && range.high >= pressureTenths / 10f)
            assertTrue("axis must not shrink: $lastHigh -> ${range.high}",
                range.high >= lastHigh)
            lastHigh = range.high
            highs += range.high
            previous = data.series.associate { it.metric to it.range }
        }
        assertTrue("pressure axis must move with progress, got $highs",
            highs.distinct().size >= 3)
        assertEquals(highs.sorted(), highs)
    }

    @Test fun liveReferenceDoesNotPreSizeAxesOrTime() {
        val current = listOf(point(0, pressure = 30), point(5_000, pressure = 30))
        val reference = listOf(point(0, pressure = 90), point(40_000, pressure = 90))
        val live = FourMetricChartProjection.build(current, reference, liveFlow = true)
        // The 40 s / 9 bar reference must not pre-size a 5 s / 3 bar current cup.
        assertEquals(5_000L, live.timeEndMs)
        val pressure = live.series(ChartMetric.PRESSURE)
        assertTrue("reference pre-sized the live pressure axis: ${pressure.range}",
            pressure.range.high < 9f)
        assertTrue(pressure.reference.flatten().any { it.value == 9f })
    }

    @Test fun idleLivePreviewStillUsesTheReferenceCup() {
        val reference = listOf(point(0, pressure = 90), point(40_000, pressure = 90))
        val idle = FourMetricChartProjection.build(emptyList(), reference, liveFlow = true)
        assertEquals(40_000L, idle.timeEndMs)
        assertTrue(idle.series(ChartMetric.PRESSURE).range.high >= 9f)
        assertTrue(idle.series(ChartMetric.PRESSURE).reference.isNotEmpty())
    }

    @Test fun historyKeepsReferenceInTheWholeCupAxes() {
        val current = listOf(point(0, pressure = 30), point(5_000, pressure = 30))
        val reference = listOf(point(0, pressure = 90), point(40_000, pressure = 90))
        val history = FourMetricChartProjection.build(current, reference)
        assertTrue("history axis must include the reference duration", history.timeEndMs >= 40_000L)
        assertTrue("history axis must include the reference pressure",
            history.series(ChartMetric.PRESSURE).range.high >= 9f)
    }

    @Test fun livePressureRangeNeverShrinksWithinTheSameShot() {
        val peak = FourMetricChartProjection.build(listOf(point(0, pressure = 0),
            point(1000, pressure = 160)), emptyList(), liveFlow = true)
        val previous = peak.series.associate { it.metric to it.range }
        val later = FourMetricChartProjection.build(listOf(point(0, pressure = 50)), emptyList(),
            previous, liveFlow = true)
        assertEquals(peak.series(ChartMetric.PRESSURE).range,
            later.series(ChartMetric.PRESSURE).range)
    }

    @Test fun nearlyFlatTemperatureUsesTheFiveDegreeMinimumSpan() {
        val data = FourMetricChartProjection.build(listOf(point(0, temperature = 9100),
            point(1000, temperature = 9400)), emptyList())
        assertEquals(ChartRange(90f, 95f), data.series(ChartMetric.TEMPERATURE).range)
        assertEquals(1f, data.series(ChartMetric.TEMPERATURE).step, 0f)
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
