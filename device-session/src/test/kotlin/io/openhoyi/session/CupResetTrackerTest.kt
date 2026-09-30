package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class CupResetTrackerTest {
    @Test fun onlyNewZeroCountAfterSuccessfulWriteConfirmsReset() {
        val tracker = CupResetTracker()
        val token = requireNotNull(tracker.begin(25))
        assertNull(tracker.begin(25))
        assertFalse(tracker.observeSettings(10, 0))
        assertTrue(tracker.written(token, OperationResult.Success(), 10, 20))
        assertFalse(tracker.observeSettings(10, 0))
        assertFalse(tracker.observeIdle(21, 0))
        assertFalse(tracker.observeSettings(11, 25))
        assertTrue(tracker.observeSettings(12, 0))
        assertEquals(CupResetTracker.State.CONFIRMED, tracker.state)
    }

    @Test fun disconnectTimeoutAndStaleCallbacksCannotClaimReset() {
        val tracker = CupResetTracker()
        val first = requireNotNull(tracker.begin(8))
        assertTrue(tracker.written(first, OperationResult.Unknown("link"), 1, 1))
        assertEquals(CupResetTracker.State.UNKNOWN, tracker.state)
        assertNull(tracker.begin(8))
        assertFalse(tracker.written(first, OperationResult.Success(), 2, 2))
        assertFalse(tracker.observeSettings(2, 8))
        assertFalse(tracker.observeIdle(1, 8))
        assertEquals(CupResetTracker.State.UNKNOWN, tracker.state)
        assertFalse(tracker.observeIdle(2, 8))
        assertEquals(CupResetTracker.State.RECONCILED, tracker.state)
        val second = requireNotNull(tracker.begin(8))
        assertTrue(tracker.written(second, OperationResult.Success(), 2, 2))
        assertTrue(tracker.timeout(second, 3, 3))
        assertNull(tracker.begin(8))
        assertFalse(tracker.observeSettings(4, 0))
        assertTrue(tracker.observeIdle(4, 0))
        assertEquals(CupResetTracker.State.CONFIRMED, tracker.state)
        val third = requireNotNull(tracker.begin(8))
        tracker.disconnected(4, 4)
        assertFalse(tracker.written(third, OperationResult.Success(), 4, 4))
        assertEquals(CupResetTracker.State.UNKNOWN, tracker.state)
    }
}
