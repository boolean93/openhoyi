package io.openhoyi.mobile

import io.openhoyi.session.StandaloneTare
import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class StandaloneTareTest {
    @Test fun zeroAtDeadlineCannotConfirmBeforeTimerRuns() {
        var now = 0L
        val tare = StandaloneTare { now }
        val token = requireNotNull(tare.begin())
        tare.written(token, OperationResult.Success(), 0)
        now = 5000L
        tare.sample(1, 0)
        assertEquals(StandaloneTare.State.UNKNOWN, tare.state)
    }

    @Test fun extremeNegativeWeightCannotConfirmZero() {
        val tare = StandaloneTare { 0L }
        val token = requireNotNull(tare.begin())
        tare.written(token, OperationResult.Success(), 0)
        tare.sample(1, Int.MIN_VALUE)
        assertEquals(StandaloneTare.State.WAITING_ZERO, tare.state)
    }
    @Test fun cancelledRetryCannotEraseEarlierUnknownOutcome() {
        val tare = StandaloneTare { 0L }
        val first = requireNotNull(tare.begin())
        tare.written(first, OperationResult.Unknown("lost"), 0)
        val second = requireNotNull(tare.begin())
        tare.written(second, OperationResult.Cancelled("not sent"), 0)
        assertEquals(StandaloneTare.State.UNKNOWN, tare.state)
        val third = requireNotNull(tare.begin())
        tare.written(third, OperationResult.Success(), 0)
        tare.sample(1, 0)
        assertEquals(StandaloneTare.State.CONFIRMED, tare.state)
        assertFalse(tare.unresolved)
    }
    @Test fun failedRetryCannotEraseEarlierUnknownOutcome() {
        val tare = StandaloneTare { 0L }
        val first = requireNotNull(tare.begin())
        tare.written(first, OperationResult.Unknown("lost"), 0)
        val second = requireNotNull(tare.begin())
        tare.written(second, OperationResult.Failed("not sent"), 0)
        assertEquals(StandaloneTare.State.UNKNOWN, tare.state)
    }

    @Test fun requiresFreshPostWriteZeroBeforeConfirmation() {
        val tare = StandaloneTare { 0L }
        val token = requireNotNull(tare.begin())
        assertNull(tare.begin())
        tare.sample(1, 0)
        tare.written(token, OperationResult.Success(), 1)
        assertEquals(StandaloneTare.State.WAITING_ZERO, tare.state)
        tare.sample(1, 0)
        assertEquals(StandaloneTare.State.WAITING_ZERO, tare.state)
        tare.sample(2, 80)
        assertEquals(StandaloneTare.State.WAITING_ZERO, tare.state)
        tare.sample(3, -50)
        assertEquals(StandaloneTare.State.CONFIRMED, tare.state)
    }

    @Test fun staleCallbacksAndTimeoutCannotCompleteNewRequest() {
        val tare = StandaloneTare { 0L }
        val first = requireNotNull(tare.begin())
        tare.written(first, OperationResult.Unknown("lost"), 0)
        assertEquals(StandaloneTare.State.UNKNOWN, tare.state)
        val second = requireNotNull(tare.begin())
        tare.written(first, OperationResult.Success(), 0)
        tare.timeout(first)
        assertEquals(StandaloneTare.State.WRITING, tare.state)
        tare.written(second, OperationResult.Success(), 0)
        tare.timeout(second)
        assertEquals(StandaloneTare.State.UNKNOWN, tare.state)
    }

    @Test fun disconnectCannotBeReportedAsConfirmed() {
        val tare = StandaloneTare { 0L }
        val token = requireNotNull(tare.begin())
        tare.written(token, OperationResult.Success(), 0)
        tare.disconnected()
        tare.sample(1, 0)
        assertEquals(StandaloneTare.State.UNKNOWN, tare.state)
    }
}
