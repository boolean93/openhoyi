package io.openhoyi.session

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import org.junit.Assert.*
import org.junit.Test

class PassiveShotDetectorTest {
    private val raw = ByteFrame(byteArrayOf())
    private val minimumSeconds = PassiveShotDetector.MIN_MANUAL_CUP_SECONDS
    private fun extraction(seconds: Int, water: Int, valve: Boolean = true) =
        ExtractionTelemetry(6, seconds, 70, water, 9300, 20, if (valve) 64 else 0, 0, raw)
    private val idle = IdleTelemetry(9300, 12000, 0, 8, 0, 0, 42, 0, raw)

    private fun events(detector: PassiveShotDetector, frame: io.openhoyi.protocol.HoyiMessage,
        atMs: Long, appShot: Boolean = false) = detector.observe(frame, atMs, appShot)

    @Test fun startsOnlyAfterTwoConsistentValveFramesAndEndsAfterFreshIdle() {
        val detector = PassiveShotDetector()
        assertTrue(events(detector, extraction(0, 1), 1000).isEmpty())
        assertFalse(detector.active)
        assertTrue(events(detector, extraction(1, 4), 1800).single() is PassiveShotDetector.Event.Started)
        assertTrue(detector.active)
        // Frames before commit are buffered for replay, never surfaced as live points.
        assertTrue(events(detector, extraction(2, 6), 2200).isEmpty())
        assertFalse(detector.isCommitted)
        assertTrue(events(detector, idle, 4900).isEmpty())
        assertEquals(PassiveShotDetector.Event.Ended, events(detector, idle, 5101).single())
        assertFalse(detector.active)
    }

    @Test fun ignoresAppShotAndNoiseAndDoesNotInferEndFromSilence() {
        val detector = PassiveShotDetector()
        assertTrue(events(detector, extraction(0, 1), 1000, appShot = true).isEmpty())
        assertTrue(events(detector, extraction(1, 2), 2000, appShot = true).isEmpty())
        assertTrue(events(detector, extraction(0, 1), 3000).isEmpty())
        assertTrue(events(detector, extraction(1, 2), 5000).isEmpty())
        assertTrue(events(detector, extraction(1, 0), 5200).isEmpty())
        assertTrue(events(detector, ExtractionTelemetry(6, 2, 70, 4, 9300, 20,
            64 or 32, 0, raw), 5300).isEmpty())
        assertFalse(detector.active)
        assertTrue(events(detector, idle, 50_000).isEmpty())
    }

    @Test fun lateValveFrameCannotMoveLastActiveTimeBackwardsOrEndEarly() {
        val detector=PassiveShotDetector()
        events(detector, extraction(0,1),1000)
        events(detector, extraction(1,2),1500)
        events(detector, extraction(2,6),4000)
        events(detector, extraction(1,2),1500)
        assertTrue(events(detector, idle,5000).isEmpty())
        assertTrue(detector.active)
        assertTrue(events(detector, idle,6800).isEmpty())
        assertEquals(PassiveShotDetector.Event.Ended, events(detector, idle,6801).single())
    }

    @Test fun outOfOrderCandidateAndDuplicatePointDoNotReplaceFreshEvidence() {
        val detector=PassiveShotDetector()
        assertTrue(events(detector, extraction(0,1),2000).isEmpty())
        assertTrue(events(detector, extraction(2,4),1000).isEmpty())
        assertTrue(events(detector, extraction(1,2),2500).single() is PassiveShotDetector.Event.Started)
        assertTrue(events(detector, extraction(1,2),2500).isEmpty())
        assertTrue(events(detector, idle,2400).isEmpty())
        assertTrue(detector.active)
        assertTrue(events(detector, extraction(2,4),3000).isEmpty())
        assertFalse(detector.isCommitted)
    }

    @Test fun disconnectMarksObservedShotUnknownRatherThanEnded() {
        val detector = PassiveShotDetector()
        events(detector, extraction(0, 1), 1000)
        events(detector, extraction(1, 2), 1500)
        assertEquals(PassiveShotDetector.Event.Interrupted, detector.disconnected())
        assertFalse(detector.active)
        assertNull(detector.disconnected())
    }

    @Test fun shortPassiveWaterIsStartedButNeverCommitted() {
        val detector = PassiveShotDetector()
        assertTrue(events(detector, extraction(0, 1), 1000).isEmpty())
        val started = events(detector, extraction(1, 4), 1800)
        assertTrue(started.single() is PassiveShotDetector.Event.Started)
        for (seconds in 2..5) {
            val atMs = 1800L + (seconds - 1) * 1000
            assertTrue("no commit at ${seconds}s", events(detector,
                extraction(seconds, seconds * 2), atMs).none { it is PassiveShotDetector.Event.Committed })
            assertFalse(detector.isCommitted)
        }
        assertTrue(events(detector, idle, 7000).isEmpty())
        assertEquals(PassiveShotDetector.Event.Ended, events(detector, idle, 8700).single())
        assertFalse(detector.active)
        assertFalse(detector.isCommitted)
    }

    @Test fun longPassiveWaterCommitsExactlyOnceAndReplaysBufferedFrames() {
        val detector = PassiveShotDetector()
        events(detector, extraction(0, 1), 1000)
        assertTrue(events(detector, extraction(1, 4), 1800).single() is PassiveShotDetector.Event.Started)
        var commits = 0
        for (seconds in 2..minimumSeconds) {
            val atMs = 1800L + (seconds - 1) * 1000
            for (event in events(detector, extraction(seconds, seconds * 4), atMs)) {
                if (event is PassiveShotDetector.Event.Committed) {
                    commits++
                    assertEquals(0, event.first.frame.elapsedSeconds)
                    assertEquals(listOf(1, 4), listOf(event.second.frame.elapsedSeconds,
                        event.second.frame.totalWaterTenthsMl))
                    assertEquals((2..minimumSeconds - 1).toList(),
                        event.points.dropLast(1).map { it.frame.elapsedSeconds })
                    assertEquals(minimumSeconds, event.points.last().frame.elapsedSeconds)
                    assertTrue(detector.isCommitted)
                }
            }
        }
        assertEquals(1, commits)
        assertTrue(events(detector, extraction(minimumSeconds + 1, 96), 1800L + minimumSeconds * 1000)
            .single() is PassiveShotDetector.Event.Point)
        assertEquals(PassiveShotDetector.Event.Ended,
            events(detector, idle, 1800L + minimumSeconds * 1000 + 2900).single())
        assertFalse(detector.isCommitted)
    }

    @Test fun staleValveTailFromThePreviousAppCupNeverCommitsFromAbsoluteElapsed() {
        val detector = PassiveShotDetector()
        assertTrue(events(detector, extraction(25, 300), 1000).isEmpty())
        val observed = events(detector, extraction(26, 302), 1500)
        assertEquals(1, observed.size)
        assertTrue(observed.single() is PassiveShotDetector.Event.Started)
        assertFalse(detector.isCommitted)
        assertTrue(events(detector, extraction(27, 304), 2000).isEmpty())
        assertEquals(PassiveShotDetector.Event.Ended, events(detector, idle, 5000).single())
        assertFalse(detector.isCommitted)
    }

    @Test fun closingValveFrameCannotCommitTheCupOnItsOwn() {
        val detector = PassiveShotDetector()
        assertTrue(events(detector, extraction(0, 1), 1000).isEmpty())
        assertTrue(events(detector, extraction(1, 4), 1800).single() is PassiveShotDetector.Event.Started)
        // The increment is already past the threshold, but the valve is closed: still not a cup.
        assertTrue(events(detector, extraction(minimumSeconds + 3, 20, valve = false), 4200).isEmpty())
        assertFalse(detector.isCommitted)
        assertTrue(detector.active)
        // The next open-valve frame past the threshold commits and replays the closing frame.
        val committed = events(detector, extraction(minimumSeconds + 4, 22), 5200).single()
            as PassiveShotDetector.Event.Committed
        assertEquals(listOf(minimumSeconds + 3, minimumSeconds + 4),
            committed.points.map { it.frame.elapsedSeconds })
        assertTrue(detector.isCommitted)
    }

    @Test fun frozenElapsedCounterFallsBackToObservedDuration() {
        val detector = PassiveShotDetector()
        assertTrue(events(detector, extraction(0, 1), 1000).isEmpty())
        assertTrue(events(detector, extraction(0, 4), 1500).single() is PassiveShotDetector.Event.Started)
        for (step in 1..5) {
            assertTrue("no early commit at ${step}s",
                events(detector, extraction(0, 0), 1500L + step * 1000).isEmpty())
        }
        assertTrue(events(detector, extraction(0, 0), 1000L + minimumSeconds * 1000 - 1).isEmpty())
        val committed = events(detector, extraction(0, 0), 1000L + minimumSeconds * 1000)
        assertTrue(committed.single() is PassiveShotDetector.Event.Committed)
        assertTrue(detector.isCommitted)
    }

    @Test fun reorderedFrameCannotCommitAndShortDisconnectNeverCommits() {
        val detector = PassiveShotDetector()
        events(detector, extraction(0, 1), 1000)
        events(detector, extraction(1, 4), 1800)
        // Out-of-order timestamps are ignored, even when the payload already crosses the threshold.
        assertTrue(events(detector, extraction(minimumSeconds, 40), 1700).isEmpty())
        assertFalse(detector.isCommitted)
        assertEquals(PassiveShotDetector.Event.Interrupted, detector.disconnected())
        assertFalse(detector.isCommitted)
        assertFalse(detector.active)
        // A later, unrelated short observation must not inherit the discarded one.
        events(detector, extraction(0, 1), 20_000)
        assertTrue(events(detector, extraction(1, 4), 21_000).single() is PassiveShotDetector.Event.Started)
        assertTrue(events(detector, idle, 24_500).single() is PassiveShotDetector.Event.Ended)
        assertFalse(detector.isCommitted)
    }
}
