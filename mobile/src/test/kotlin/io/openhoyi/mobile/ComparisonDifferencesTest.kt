package io.openhoyi.mobile

import io.openhoyi.mobile.ComparisonDifferences.Metric
import io.openhoyi.mobile.ComparisonDifferences.Reading
import io.openhoyi.mobile.ComparisonDifferences.Unavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComparisonDifferencesTest {
    private fun point(
        time: Long,
        pressure: Int = 85,
        water: Int = 20,
        temp: Int = 9300,
        brewing: Boolean? = true,
        net: Int? = 500,
        source: BeverageWeightSource = BeverageWeightSource.CONFIRMED,
        segment: Int = 0,
    ) = ShotPoint(time, pressure, 0, water, temp, 20000, brewing = brewing, beverageHundredthsGram = net,
        beverageSource = source.takeIf { net != null }, beverageSegmentId = segment.takeIf { net != null })

    @Test fun usesObservedBrewingPeakAndMedianAndLastRecordedWaterAndNetWeight() {
        val result = ComparisonDifferences.summarize("ENDED", listOf(
            point(0, 990, 900, 12000, brewing = false),
            point(300, 80, 10, 9200, net = 0),
            point(600, 92, 30, 9600),
            point(900, 700, 40, 6500, brewing = false, net = 3225),
        ))
        assertEquals(Reading.Value(92), result[Metric.PRESSURE])
        assertEquals(Reading.Value(940), result[Metric.TEMPERATURE])
        assertEquals(Reading.Value(40), result[Metric.WATER])
        assertEquals(Reading.Value(3225), result[Metric.NET_WEIGHT])
    }

    @Test fun comparesAtDisplayPrecisionAndReversesDirectionWithCupOrder() {
        fun temperature(temp: Int) = ComparisonDifferences.summarize("ENDED",
            listOf(point(0, temp = temp), point(100, temp = temp))).getValue(Metric.TEMPERATURE)
        val a = temperature(9301)
        val b = temperature(9304)
        val c = temperature(9312)
        assertEquals(0L, ComparisonDifferences.difference(a, b)?.displayUnits)
        assertEquals(ComparisonDifferences.Difference(false, 1), ComparisonDifferences.difference(a, c))
        assertEquals(ComparisonDifferences.Difference(true, 1), ComparisonDifferences.difference(c, a))
    }

    @Test fun neverUsesLegacyAlignmentOrUnconfirmedEndAsEvidence() {
        val known = listOf(point(0), point(100))
        for (status in listOf("UNKNOWN", "NOT_STARTED", "RUNNING")) {
            assertEquals(setOf(Reading.Missing(Unavailable.NOT_ENDED)),
                ComparisonDifferences.summarize(status, known).values.toSet())
        }
        assertEquals(setOf(Reading.Missing(Unavailable.NO_SAMPLES)),
            ComparisonDifferences.summarize("ENDED", emptyList()).values.toSet())
        assertEquals(setOf(Reading.Missing(Unavailable.NO_BREWING)),
            ComparisonDifferences.summarize("ENDED", known.map { it.copy(brewing = null) }).values.toSet())
        assertEquals(Reading.Missing(Unavailable.NO_BREWING),
            ComparisonDifferences.summarize("ENDED", listOf(point(0), point(90, brewing = false)))[Metric.PRESSURE])
    }

    @Test fun keepsIndependentMetricsAndDoesNotSubstituteStaleOrRawNetWeight() {
        val beginning = listOf(point(0, water = 50), point(100, water = 80))
        fun result(endpoint: ShotPoint) = ComparisonDifferences.summarize("ENDED", beginning + endpoint)
        val missing = result(point(200, water = 0, brewing = false, net = null))
        assertEquals(Reading.Missing(Unavailable.WATER_RESET), missing[Metric.WATER])
        assertEquals(Reading.Missing(Unavailable.NO_NET_WEIGHT), missing[Metric.NET_WEIGHT])
        assertEquals(Reading.Value(85), missing[Metric.PRESSURE])
        assertNull(ComparisonDifferences.difference(missing.getValue(Metric.NET_WEIGHT), Reading.Value(0)))
        assertEquals(Reading.Missing(Unavailable.NET_SEGMENTS),
            result(point(200, water = 90, segment = 1))[Metric.NET_WEIGHT])
        val onlyLaterSegment = listOf(point(0, net = null), point(100, segment = 1))
        assertEquals(Reading.Missing(Unavailable.NET_SEGMENTS),
            ComparisonDifferences.summarize("ENDED", onlyLaterSegment)[Metric.NET_WEIGHT])
        val estimated = listOf(point(0, source = BeverageWeightSource.ESTIMATED), point(100, source = BeverageWeightSource.ESTIMATED))
        assertEquals(Reading.Missing(Unavailable.ESTIMATED),
            ComparisonDifferences.summarize("ENDED", estimated)[Metric.NET_WEIGHT])
    }
}
