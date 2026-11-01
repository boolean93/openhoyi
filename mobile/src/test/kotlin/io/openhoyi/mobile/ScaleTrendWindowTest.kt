package io.openhoyi.mobile

import io.openhoyi.protocol.*
import io.openhoyi.session.DeviceState
import org.junit.Assert.*
import org.junit.Test

class ScaleTrendWindowTest {
    private fun sample(at: Long, weight: Int = 1800, evidence: ScaleEvidence = ScaleEvidence.VERIFIED_TRANSPORT) =
        ScaleObservation(weight, at, ScaleCapabilities(), evidence, raw = ByteFrame(byteArrayOf()))
    @Test fun repeatedSnapshotsAndOutOfOrderNotificationsDoNotInventSamples() {
        val window = ScaleTrendWindow()
        window.observe(sample(1000), DeviceState.READY, 1000)
        window.observe(sample(1000), DeviceState.READY, 1250)
        window.observe(sample(900), DeviceState.READY, 1300)
        assertEquals(listOf(1000L), window.points(1300).map { it.atMs })
        window.observe(sample(1400, 1900), DeviceState.READY, 1400)
        assertEquals(listOf(1800, 1900), window.points(1400).map { it.hundredthsGram })
    }
    @Test fun disconnectStalenessAndEvidenceChangesBreakLines() {
        val window = ScaleTrendWindow()
        window.observe(sample(1000), DeviceState.READY, 1000)
        window.observe(null, DeviceState.DISCONNECTED, 1100)
        window.observe(sample(1200), DeviceState.READY, 1200)
        window.observe(sample(3000), DeviceState.READY, 3000)
        window.observe(sample(3100, evidence = ScaleEvidence.LIVE_READ_ONLY), DeviceState.READY, 3100)
        val points = window.points(3100)
        assertEquals(4, points.size)
        assertEquals(4, points.map { it.segment }.distinct().size)
        assertEquals(ScaleEvidence.LIVE_READ_ONLY, points.last().evidence)
    }
    @Test fun unsupportedFutureAndStaleValuesCannotBecomePointsButNegativeWeightCan() {
        val window = ScaleTrendWindow()
        window.observe(sample(2000), DeviceState.READY, 1000)
        window.observe(sample(0), DeviceState.READY, 2000)
        window.observe(sample(2000, Int.MAX_VALUE), DeviceState.READY, 2000)
        window.observe(sample(2000, -125), DeviceState.READY, 2000)
        assertEquals(listOf(-125), window.points(2000).map { it.hundredthsGram })
    }
    @Test fun windowExpiresWithoutNewNotificationsAndClockRollbackClearsIt() {
        val window = ScaleTrendWindow()
        window.observe(sample(1000), DeviceState.READY, 1000)
        assertEquals(1, window.points(11000).size)
        assertTrue(window.points(11001).isEmpty())
        window.observe(sample(12000), DeviceState.READY, 12000)
        assertTrue(window.points(10).isEmpty())
        window.observe(sample(11), DeviceState.READY, 11)
        assertEquals(listOf(11L), window.points(11).map { it.atMs })
    }
    @Test fun memoryIsBoundedAndReturnedSnapshotsDoNotChange() {
        val window = ScaleTrendWindow(maxPoints = 3)
        window.observe(sample(0), DeviceState.READY, 0)
        val before = window.points(0)
        for (at in 1L..4L) window.observe(sample(at), DeviceState.READY, at)
        assertEquals(listOf(2L, 3L, 4L), window.points(4).map { it.atMs })
        assertEquals(listOf(0L), before.map { it.atMs })
    }
}
