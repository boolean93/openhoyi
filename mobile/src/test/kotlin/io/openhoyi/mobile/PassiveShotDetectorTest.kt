package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import org.junit.Assert.*
import org.junit.Test

class PassiveShotDetectorTest {
    private val raw = ByteFrame(byteArrayOf())
    private fun extraction(seconds: Int, water: Int, valve: Boolean = true) =
        ExtractionTelemetry(6, seconds, 70, water, 9300, 20, if (valve) 64 else 0, 0, raw)
    private val idle = IdleTelemetry(9300, 12000, 0, 8, 0, 0, 42, 0, raw)

    @Test fun startsOnlyAfterTwoConsistentValveFramesAndEndsAfterFreshIdle() {
        val detector = PassiveShotDetector()
        assertNull(detector.observe(extraction(0, 1), 1000, false))
        assertFalse(detector.active)
        assertTrue(detector.observe(extraction(1, 4), 1800, false) is PassiveShotDetector.Event.Started)
        assertTrue(detector.active)
        assertTrue(detector.observe(extraction(2, 6), 2200, false) is PassiveShotDetector.Event.Point)
        assertNull(detector.observe(idle, 4900, false))
        assertEquals(PassiveShotDetector.Event.Ended, detector.observe(idle, 5101, false))
        assertFalse(detector.active)
    }

    @Test fun ignoresAppShotAndNoiseAndDoesNotInferEndFromSilence() {
        val detector = PassiveShotDetector()
        assertNull(detector.observe(extraction(0, 1), 1000, true))
        assertNull(detector.observe(extraction(1, 2), 2000, true))
        assertNull(detector.observe(extraction(0, 1), 3000, false))
        assertNull(detector.observe(extraction(1, 2), 5000, false))
        assertNull(detector.observe(extraction(1, 0), 5200, false))
        assertNull(detector.observe(ExtractionTelemetry(6, 2, 70, 4, 9300, 20,
            64 or 32, 0, raw), 5300, false))
        assertFalse(detector.active)
        assertNull(detector.observe(idle, 50_000, false))
    }

    @Test fun disconnectMarksObservedShotUnknownRatherThanEnded() {
        val detector = PassiveShotDetector()
        detector.observe(extraction(0, 1), 1000, false)
        detector.observe(extraction(1, 2), 1500, false)
        assertEquals(PassiveShotDetector.Event.Interrupted, detector.disconnected())
        assertFalse(detector.active)
        assertNull(detector.disconnected())
    }
}
