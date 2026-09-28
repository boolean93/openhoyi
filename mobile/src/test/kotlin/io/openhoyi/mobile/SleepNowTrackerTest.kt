package io.openhoyi.mobile

import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class SleepNowTrackerTest {
    @Test fun confirmsOnlyWithFreshAsleepTelemetryAfterWrite() {
        val tracker = SleepNowTracker()
        val token = requireNotNull(tracker.begin())
        assertNull(tracker.begin())
        assertFalse(tracker.observe(1, 1))
        assertTrue(tracker.written(token, OperationResult.Success(), 1))
        assertFalse(tracker.observe(1, 1))
        assertFalse(tracker.observe(2, 0))
        assertFalse(tracker.observe(3, 2))
        assertTrue(tracker.observe(4, 1))
        assertEquals(SleepNowTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun timeoutDisconnectAndStaleCallbackNeverClaimAsleep() {
        val tracker = SleepNowTracker()
        val first = requireNotNull(tracker.begin())
        assertTrue(tracker.written(first, OperationResult.Unknown("link"), 0))
        assertEquals(SleepNowTracker.State.UNKNOWN, tracker.state)
        assertNull(tracker.begin())
        assertFalse(tracker.written(first, OperationResult.Success(), 0))
        assertFalse(tracker.observe(0, 0))
        assertFalse(tracker.observe(1, 2))
        assertEquals(SleepNowTracker.State.UNKNOWN, tracker.state)
        assertFalse(tracker.observe(2, 0))
        assertEquals(SleepNowTracker.State.RECONCILED, tracker.state)
        val second = requireNotNull(tracker.begin())
        assertTrue(tracker.written(second, OperationResult.Success(), 0))
        assertTrue(tracker.timeout(second, 2))
        assertNull(tracker.begin())
        assertFalse(tracker.observe(2, 1))
        assertTrue(tracker.observe(3, 1))
        assertEquals(SleepNowTracker.State.CONFIRMED, tracker.state)
        val third = requireNotNull(tracker.begin())
        assertTrue(tracker.written(third, OperationResult.Success(), 3))
        tracker.disconnected(3)
        assertNull(tracker.begin())
        assertEquals(SleepNowTracker.State.UNKNOWN, tracker.state)
    }
}
