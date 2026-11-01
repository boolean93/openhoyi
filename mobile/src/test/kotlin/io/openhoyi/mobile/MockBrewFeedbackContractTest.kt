package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class MockBrewFeedbackContractTest {
    @Test fun quarterSecondMockCupFeedsTheUnchangedWireSecondFeedbackContract() {
        val runtime = MockDeviceRuntime { "" }
        val tracker = BrewFeedbackTracker()
        runtime.start(0, 7)
        tracker.begin("quarter-second-mock", 8, 0)
        for (at in 0L..31_750L step 250L) {
            val frame = runtime.sample(at).coffee as ExtractionTelemetry
            assertEquals(8, frame.slotOrPhase)
            tracker.observe("quarter-second-mock", frame, at)
        }
        assertTrue(runtime.sample(32_000).coffee is IdleTelemetry)
        assertEquals(ExtractionState.ENDED_OBSERVED, runtime.shotState)
        val result = requireNotNull(tracker.finish("quarter-second-mock", observedEnd = true))
        assertEquals("quarter-second-mock", result.shotId)
        assertEquals(31, result.machineSeconds)
        assertEquals(0, result.preinfusionSeconds)
        assertEquals(64, result.earlyWaterTenthsMl) // Last second-10 observation is at10.75s.
        assertEquals(190, result.finalWaterTenthsMl)
        assertEquals((19.0 - 6.4) / 21, result.flowHeuristic, 0.0)
        assertEquals(BrewFeedbackClips.Level.LOW_FLOW, result.level)
        assertNull(tracker.finish("quarter-second-mock", observedEnd = true))
    }

    @Test fun fractionalEndOfWireSecondTenIsAcceptedButSecondElevenDoesNotReplaceIt() {
        val runtime = MockDeviceRuntime { "" }
        val tracker = BrewFeedbackTracker()
        runtime.start(0, 7)
        tracker.begin("wire-boundary-mock", 8, 0)
        val frames = listOf(10_000L, 10_750L, 10_999L, 11_000L).map { at ->
            (runtime.sample(at).coffee as ExtractionTelemetry).also {
                tracker.observe("wire-boundary-mock", it, at)
            }
        }
        assertEquals(listOf(10, 10, 10, 11), frames.map { it.elapsedSeconds })
        assertEquals(listOf(60, 64, 65, 66), frames.map { it.totalWaterTenthsMl })
        assertEquals(listOf(8, 8, 8, 8), frames.map { it.slotOrPhase })
        tracker.observe("wire-boundary-mock", runtime.sample(31_750).coffee as ExtractionTelemetry, 31_750)
        assertTrue(runtime.sample(32_000).coffee is IdleTelemetry)
        val result = requireNotNull(tracker.finish("wire-boundary-mock", observedEnd = true))
        assertEquals(65, result.earlyWaterTenthsMl)
        assertEquals(190, result.finalWaterTenthsMl)
        assertEquals(31, result.machineSeconds)
        assertEquals((19.0 - 6.5) / 21, result.flowHeuristic, 0.0)
        assertEquals(BrewFeedbackClips.Level.LOW_FLOW, result.level)
    }
}
