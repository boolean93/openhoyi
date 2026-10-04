package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

/** Host serial contract only; does not detect re-numbered BLE replay or continuous thermal readiness. */
class BrewPreparationOrderingTest {
    private fun waiting() = BrewPreparation().also {
        val token = requireNotNull(it.begin("curve", 92))
        assertTrue(it.written(token, OperationResult.Success(), 10))
    }
    @Test fun olderHotSampleCannotOverrideNewerColdSample() {
        val p = waiting()
        assertFalse(p.observe(12, 9000))
        assertFalse(p.observe(11, 9200))
        assertEquals(BrewPreparation.State.WAITING_TEMP, p.state)
        assertFalse(p.matches("curve", 92))
        assertTrue(p.observe(13, 9200))
        assertTrue(p.matches("curve", 92))
    }
    @Test fun repeatedSerialCannotReplaceAnAlreadyObservedTemperature() {
        val p = waiting()
        assertFalse(p.observe(12, 9000))
        assertFalse(p.observe(12, 9200))
        assertEquals(BrewPreparation.State.WAITING_TEMP, p.state)
        assertTrue(p.observe(13, 9200))
    }
    @Test fun consumedOperationDoesNotBlockFreshBaselineInNewStream() {
        val p = waiting()
        assertTrue(p.observe(50, 9200))
        p.consumed()
        val next = requireNotNull(p.begin("new-curve", 93))
        assertTrue(p.written(next, OperationResult.Success(), 5))
        assertTrue(p.observe(6, 9300))
        assertTrue(p.matches("new-curve", 93))
        assertFalse(p.matches("curve", 92))
    }
}
