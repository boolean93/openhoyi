package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ChartInspectionTest {
    private fun point(ms: Long, weight: Int? = 456, brewing: Boolean? = true) =
        ShotPoint(ms, 91, 999, 123, 9356, weight, 999, brewing)

    @Test fun nearestUsesActualSampleAndNeverInterpolatesAcrossGap() {
        val samples = listOf(point(1000), point(9000))
        assertEquals(0, ChartInspection.nearestIndex(samples, 5000))
        assertEquals(1, ChartInspection.nearestIndex(samples, 5001))
        assertEquals(0, ChartInspection.nearestIndex(samples, 0))
        assertEquals(1, ChartInspection.nearestIndex(samples, Long.MAX_VALUE))
        assertEquals(null, ChartInspection.nearestIndex(emptyList(), 1000))
    }
    @Test fun snapshotPreservesMissingValuesAndPhysicalUnits() {
        val values = ChartInspection.values(point(1000))
        assertEquals(9.1f, values[ChartMetric.PRESSURE]!!, .001f)
        assertEquals(12.3f, values[ChartMetric.WATER]!!, .001f)
        assertEquals(4.56f, values[ChartMetric.WEIGHT]!!, .001f)
        assertEquals(93.56f, values[ChartMetric.TEMPERATURE]!!, .001f)
        val missing = ChartInspection.values(point(1000, null, false))
        assertNull(missing[ChartMetric.PRESSURE])
        assertNull(missing[ChartMetric.WEIGHT])
        assertEquals(12.3f, missing[ChartMetric.WATER]!!, .001f)
    }
}
