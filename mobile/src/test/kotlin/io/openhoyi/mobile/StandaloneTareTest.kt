package io.openhoyi.mobile

import io.openhoyi.session.OperationResult
import org.junit.Assert.*
import org.junit.Test

class StandaloneTareTest {
    @Test fun requiresFreshPostWriteZeroBeforeConfirmation() {
        val tare = StandaloneTare()
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
        val tare = StandaloneTare()
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
        val tare = StandaloneTare()
        val token = requireNotNull(tare.begin())
        tare.written(token, OperationResult.Success(), 0)
        tare.disconnected()
        tare.sample(1, 0)
        assertEquals(StandaloneTare.State.UNKNOWN, tare.state)
    }
}
