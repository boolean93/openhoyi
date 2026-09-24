package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import org.junit.Assert.*
import org.junit.Test

class ShotSeriesTest {
    private val frame = ExtractionTelemetry(1, 2, 93, 120, 9200, 24, 0x40, 0, ByteFrame(byteArrayOf()))

    @Test fun recordsMachineTelemetryAndOnlyFreshScaleWeight() {
        val series = ShotSeries()
        series.begin("shot-1", 1_000)
        series.machine(frame, 1_100, 2710, 1_050, 230)
        series.machine(frame, 1_300, 2720, 1_300, -20)
        series.machine(frame, 3_000, 2800, 1_300, 410)
        assertEquals(listOf(100L, 300L, 2000L), series.points.map { it.elapsedMs })
        assertEquals(listOf(2710, 2720, null), series.points.map { it.weightHundredthsGram })
        assertEquals(listOf(230, -20, null), series.points.map { it.scaleFlowHundredths })
        assertEquals(93, series.points.first().pressureTenthsBar)
        assertEquals(24, series.points.first().machineFlowTenthsMlPerSecond)
        assertEquals(120, series.points.first().waterTenthsMl)
        assertEquals("shot-1", series.finish()?.first)
        assertEquals(3, series.points.size)
        series.begin("shot-3", 4_000)
        assertTrue(series.points.isEmpty())
    }

    @Test fun rejectsStaleOrReorderedNotificationsAndBoundsLongShots() {
        val series = ShotSeries()
        series.begin("shot-2", 10_000)
        series.machine(frame, 9_999, null, null)
        repeat(4_100) { series.machine(frame, 10_000L + it * 100, null, null) }
        assertTrue(series.points.size <= 2_000)
        assertEquals(0L, series.points.first().elapsedMs)
        assertEquals(409_900L, series.points.last().elapsedMs)
        val before = series.points.size
        series.machine(frame, 10_100, null, null)
        assertEquals(before, series.points.size)
    }

    @Test fun checkpointsFirstPointThenAtBoundedIntervalsAndKeepsPartialHistory() {
        val series = ShotSeries()
        series.begin("shot-partial", 1_000)
        assertNull(series.checkpoint(1_000))
        series.machine(frame, 1_100, null, null)
        assertEquals(1, series.checkpoint(1_100)?.second?.size)
        series.machine(frame, 5_900, null, null)
        assertNull(series.checkpoint(5_900))
        series.machine(frame, 6_100, null, null)
        assertEquals(3, series.checkpoint(6_100)?.second?.size)
        assertEquals(3, series.checkpoint(6_100, force = true)?.second?.size)
        assertEquals("shot-partial", series.finish()?.first)
        assertNull(series.checkpoint(7_000, force = true))
    }
}
