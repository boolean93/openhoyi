package io.openhoyi.mobile

import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class CupResetTrackerTest {
    @Test fun onlyNewZeroCountAfterSuccessfulWriteConfirmsReset() {
        val tracker = CupResetTracker()
        val token = requireNotNull(tracker.begin(25))
        assertNull(tracker.begin(25))
        assertFalse(tracker.observe(10, 0))
        assertTrue(tracker.written(token, OperationResult.Success(), 10))
        assertFalse(tracker.observe(10, 0))
        assertFalse(tracker.observe(11, 25))
        assertTrue(tracker.observe(12, 0))
        assertEquals(CupResetTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun disconnectTimeoutAndStaleCallbacksCannotClaimReset() {
        val tracker = CupResetTracker()
        val first = requireNotNull(tracker.begin(8))
        assertTrue(tracker.written(first, OperationResult.Unknown("link"), 1))
        assertEquals(CupResetTracker.State.UNKNOWN, tracker.state)
        val second = requireNotNull(tracker.begin(8))
        assertFalse(tracker.written(first, OperationResult.Success(), 2))
        assertTrue(tracker.written(second, OperationResult.Success(), 2))
        assertTrue(tracker.timeout(second))
        assertFalse(tracker.observe(3, 0))
        val third = requireNotNull(tracker.begin(8))
        tracker.disconnected()
        assertFalse(tracker.written(third, OperationResult.Success(), 3))
        assertEquals(CupResetTracker.State.UNKNOWN, tracker.state)
    }
}
