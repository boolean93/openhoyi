package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

/** Host serial ordering, not firmware zero acknowledgement or replay-renumbering detection. */
class StandaloneTareOrderingTest {
    private fun waiting() = StandaloneTare { 100L }.also {
        val token = requireNotNull(it.begin())
        assertTrue(it.written(token, OperationResult.Success(), 10))
    }
    @Test fun olderZeroCannotOverrideNewerNonzeroSample() {
        val t = waiting()
        t.sample(12, 2500)
        t.sample(11, 0)
        assertEquals(StandaloneTare.State.WAITING_ZERO, t.state)
        assertTrue(t.unresolved)
        t.sample(13, 0)
        assertEquals(StandaloneTare.State.CONFIRMED, t.state)
        assertFalse(t.unresolved)
    }
    @Test fun repeatedSerialCannotReplaceNonzeroWithZero() {
        val t = waiting()
        t.sample(12, 2500)
        t.sample(12, 0)
        assertEquals(StandaloneTare.State.WAITING_ZERO, t.state)
        t.sample(13, 50)
        assertEquals(StandaloneTare.State.CONFIRMED, t.state)
    }
    @Test fun explicitNewOperationEstablishesANewBaseline() {
        val t = waiting()
        t.sample(50, 0)
        assertEquals(StandaloneTare.State.CONFIRMED, t.state)
        val next = requireNotNull(t.begin())
        assertTrue(t.written(next, OperationResult.Success(), 5))
        t.sample(6, -50)
        assertEquals(StandaloneTare.State.CONFIRMED, t.state)
    }
    @Test fun zeroAfterDeadlineCannotResolveAnUnknownTare() {
        var now = 100L
        val t = StandaloneTare { now }
        val token = requireNotNull(t.begin())
        t.written(token, OperationResult.Success(), 10)
        now = 5100
        t.sample(11, 0)
        assertEquals(StandaloneTare.State.UNKNOWN, t.state)
        assertTrue(t.unresolved)
    }
}
