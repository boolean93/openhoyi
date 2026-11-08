package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ChartAxisScaleTest {
    @Test fun niceStepUsesOnlyOneTwoTwoPointFiveFiveAndTenMantissas() {
        assertEquals(0.5f, ChartAxisScale.niceStep(0.37f), 0f)
        assertEquals(0.25f, ChartAxisScale.niceStep(0.21f), 0f)
        assertEquals(1f, ChartAxisScale.niceStep(0.6f), 0f)
        assertEquals(2f, ChartAxisScale.niceStep(1.74f), 0f)
        assertEquals(2.5f, ChartAxisScale.niceStep(2.2f), 0f)
        assertEquals(5f, ChartAxisScale.niceStep(4.2f), 0f)
        assertEquals(10f, ChartAxisScale.niceStep(6.2f), 0f)
        assertEquals(100f, ChartAxisScale.niceStep(80f), 0f)
        assertTrue(ChartAxisScale.niceStep(1e-6f) > 0f)
    }

    @Test fun pressureExampleExpandsToNiceWholeTicks() {
        val axis = ChartAxisScale.yAxis(0f, 8.7f)
        assertEquals(ChartRange(0f, 10f), axis.range)
        assertEquals(2f, axis.step, 0f)
        assertEquals(listOf(0f, 2f, 4f, 6f, 8f, 10f), ChartAxisScale.majorTicks(axis.range, axis.step))
    }

    @Test fun temperatureExampleKeepsObservedIntervalWithoutAnchoringZero() {
        // A 91..94 cup is not forced to zero; every observed value gets a tick.
        val axis = ChartAxisScale.yAxis(91f, 94f)
        assertEquals(ChartRange(91f, 94f), axis.range)
        assertEquals(1f, axis.step, 0f)
        assertEquals(listOf(91f, 92f, 93f, 94f), ChartAxisScale.majorTicks(axis.range, axis.step))
    }

    @Test fun temperatureWithFiveDegreePaddingStartsWithWholeTicks() {
        val axis = ChartAxisScale.yAxis(90f, 95f)
        assertEquals(ChartRange(90f, 95f), axis.range)
        assertEquals(6, ChartAxisScale.majorTicks(axis.range, axis.step).size)
    }

    @Test fun rangeAlwaysContainsObservedValuesAndBothEndsAreMajorTicks() {
        listOf(0f to 0.01f, -3f to 2f, 88.2f to 93.4f, 0f to 800f, -12f to -5f).forEach { (low, high) ->
            val axis = ChartAxisScale.yAxis(low, high)
            assertTrue("$low..$high low", axis.range.low <= low)
            assertTrue("$high high", axis.range.high >= high)
            val ticks = ChartAxisScale.majorTicks(axis.range, axis.step)
            assertEquals(axis.range.low, ticks.first(), 0f)
            assertEquals(axis.range.high, ticks.last(), 0f)
            assertTrue("division count", ticks.size in 3..7)
        }
    }

    @Test fun minorTicksSitAtHalfStepsBetweenMajors() {
        val range = ChartRange(0f, 10f)
        assertEquals(listOf(1f, 3f, 5f, 7f, 9f), ChartAxisScale.minorTicks(range, 2f))
        assertTrue(ChartAxisScale.minorTicks(ChartRange(0f, 1f), 0.5f).all {
            it == 0.25f || it == 0.75f
        })
    }

    @Test fun zeroWidthSamplesGetARealFiniteAxis() {
        val flat = ChartAxisScale.yAxis(9f, 9f)
        assertTrue(flat.range.low < flat.range.high)
        assertTrue(flat.range.low <= 9f && flat.range.high >= 9f)
        val negative = ChartAxisScale.yAxis(-2f, -2f)
        assertTrue(negative.range.low <= -2f && negative.range.high >= -2f)
    }

    @Test fun timeAxisUsesHumanReadableStepsAndRoundsHistoryEndUp() {
        val history = ChartAxisScale.timeAxis(24_600, roundUp = true)
        assertEquals(25_000L, history.endMs)
        assertEquals(5_000L, history.stepMs)
        assertEquals(listOf(0L, 5_000L, 10_000L, 15_000L, 20_000L, 25_000L), history.majorTicks)
        assertTrue(history.minorTicks.contains(2_500L) && history.minorTicks.contains(22_500L))
    }

    @Test fun realtimeTimeAxisKeepsItsContinuousEnd() {
        val live = ChartAxisScale.timeAxis(30_100, roundUp = false)
        assertEquals(30_100L, live.endMs)
        assertEquals(10_000L, live.stepMs)
        assertEquals(listOf(0L, 10_000L, 20_000L, 30_000L, 30_100L), live.majorTicks)
    }

    @Test fun longTimeAxisFallsBackToWholeMinuteSteps() {
        val long = ChartAxisScale.timeAxis(600_000, roundUp = true)
        assertEquals(120_000L, long.stepMs)
        assertEquals(600_000L, long.endMs)
        assertEquals(listOf(0L, 120_000L, 240_000L, 360_000L, 480_000L, 600_000L), long.majorTicks)
    }

    @Test fun invalidInputsAreRejectedRatherThanGuessed() {
        listOf<() -> Unit>({ ChartAxisScale.niceStep(0f) }, { ChartAxisScale.niceStep(-1f) },
            { ChartAxisScale.niceStep(Float.NaN) }, { ChartAxisScale.yAxis(Float.NaN, 1f) },
            { ChartAxisScale.yAxis(0f, Float.POSITIVE_INFINITY) },
            { ChartAxisScale.timeAxis(0) }).forEach { invalid ->
            try { invalid(); fail("Expected IllegalArgumentException") } catch (_: IllegalArgumentException) {
                // Display-only axis arithmetic rejects invalid input instead of drawing nonsense.
            }
        }
    }
}
