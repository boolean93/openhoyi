package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

/** Host sample ordering only; does not identify old BLE frames given a new serial. */
class CupResetBoundaryOrderingTest {
    private fun boundary(settings: Boolean, disconnect: Boolean) {
        val t = CupResetTracker()
        val token = requireNotNull(t.begin(25))
        t.written(token, OperationResult.Success(), 10, 10)
        if (settings) assertFalse(t.observeSettings(12, 25)) else assertFalse(t.observeIdle(12, 25))
        if (disconnect) t.disconnected(10, 10) else assertTrue(t.timeout(token, 10, 10))
        if (settings) {
            assertFalse(t.observeSettings(11, 0))
            assertFalse(t.observeIdle(11, 0))
            assertEquals(CupResetTracker.State.UNKNOWN, t.state)
            assertFalse(t.observeSettings(12, 0))
            assertTrue(t.observeSettings(13, 0))
        } else {
            assertFalse(t.observeIdle(11, 0))
            assertFalse(t.observeSettings(11, 0))
            assertEquals(CupResetTracker.State.UNKNOWN, t.state)
            assertFalse(t.observeIdle(12, 0))
            assertTrue(t.observeIdle(13, 0))
        }
        assertEquals(CupResetTracker.State.CONFIRMED, t.state)
    }
    @Test fun settingsTimeoutDoesNotRegressConsumedSerial() = boundary(true, false)
    @Test fun settingsDisconnectDoesNotRegressConsumedSerial() = boundary(true, true)
    @Test fun idleTimeoutDoesNotRegressConsumedSerial() = boundary(false, false)
    @Test fun idleDisconnectDoesNotRegressConsumedSerial() = boundary(false, true)
    @Test fun boundaryDiscardsPreBoundaryPartialEvidence() {
        val t = CupResetTracker()
        val token = requireNotNull(t.begin(25))
        t.written(token, OperationResult.Success(), 10, 10)
        assertFalse(t.observeSettings(12, 0))
        t.timeout(token, 12, 10)
        assertFalse(t.observeIdle(11, 0))
        assertEquals(CupResetTracker.State.UNKNOWN, t.state)
        assertTrue(t.observeSettings(13, 0))
    }
    @Test fun newOperationDoesNotInheritPreviousSerialsOrCounts() {
        val t = CupResetTracker()
        val first = requireNotNull(t.begin(25))
        t.written(first, OperationResult.Success(), 10, 10)
        assertFalse(t.observeSettings(50, 0))
        assertTrue(t.observeIdle(50, 0))
        val next = requireNotNull(t.begin(2))
        t.disconnected(5, 5)
        assertFalse(t.written(next, OperationResult.Success(), 5, 5))
        assertFalse(t.observeSettings(6, 0))
        assertTrue(t.observeIdle(6, 0))
    }
}
