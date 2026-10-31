package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ExtractionOverlayProjectionTest {
    private fun sample(ms: Long, pressure: Int, weight: Int?) = ShotPoint(ms, pressure, 999, 0, 9300, weight)
    @Test fun independentUnitsShareOnlyTheTimeline() {
        val data = ExtractionOverlayProjection.build(listOf(sample(0, 90, 0), sample(1000, 90, 200)), emptyList())
        assertEquals(9f, data.pressure.last()!!, .001f)
        assertEquals(2f, data.flow.last()!!, .001f)
        assertEquals(12f, data.pressureMaximum, .001f)
        assertEquals(3f, data.flowMaximum, .001f)
        assertEquals(30f, data.timeSeconds, .001f)
        assertEquals(2, data.pressure.size)
        assertEquals(2, data.flow.size)
    }
    @Test fun referenceExtendsTimeButCannotCreateFutureLiveData() {
        val data = ExtractionOverlayProjection.build(listOf(sample(0, 80, 0)), listOf(sample(41000, 160, null)))
        assertEquals(50f, data.timeSeconds, .001f)
        assertEquals(16f, data.pressureMaximum, .001f)
        assertEquals(1, data.pressure.size)
        assertNull(data.flow.single())
    }
    @Test fun gapsPreheatAndWeightResetRemainUnknown() {
        val data = ExtractionOverlayProjection.build(listOf(sample(0, 0, 0).copy(brewing=false),
            sample(1000, 90, 200), sample(2000, 90, 400), sample(4000, 80, 600), sample(5000, 80, 0)), emptyList())
        assertNull(data.pressure.first())
        assertEquals(listOf(null, null, 2f, null, null), data.flow)
    }
}
