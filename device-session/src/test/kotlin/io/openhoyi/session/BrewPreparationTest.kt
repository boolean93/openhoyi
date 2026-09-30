package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class BrewPreparationTest {
    @Test fun readinessRequiresFreshCorrectedTemperatureAfterWrite() {
        val preparation = BrewPreparation()
        val token = requireNotNull(preparation.begin("curve-a", 92))
        assertNull(preparation.begin("curve-b", 93))
        assertFalse(preparation.observe(1, 9200))
        assertTrue(preparation.written(token, OperationResult.Success(), 1))
        assertFalse(preparation.observe(1, 9200))
        assertFalse(preparation.observe(2, 9099))
        assertTrue(preparation.observe(3, 9100))
        assertEquals(BrewPreparation.State.READY, preparation.state)
        assertTrue(preparation.matches("curve-a", 92))
        assertFalse(preparation.matches("curve-b", 92))
        preparation.consumed()
        assertEquals(BrewPreparation.State.IDLE, preparation.state)
    }

    @Test fun cancellationAndDisconnectKeepUncertainOutcomes() {
        val preparation = BrewPreparation()
        val first = requireNotNull(preparation.begin("curve-a", 92))
        val cancel = requireNotNull(preparation.beginCancel())
        assertFalse(preparation.written(first, OperationResult.Success(), 0))
        assertTrue(preparation.cancelled(cancel, OperationResult.Unknown("link")))
        assertEquals(BrewPreparation.State.UNKNOWN, preparation.state)
        val retry = requireNotNull(preparation.beginCancel())
        assertTrue(preparation.cancelled(retry, OperationResult.Success()))
        assertEquals(BrewPreparation.State.CANCEL_WRITTEN, preparation.state)
        assertNull(preparation.begin("curve-b", 90))
        val explicitRetry = requireNotNull(preparation.beginCancel())
        assertTrue(preparation.cancelled(explicitRetry, OperationResult.Success()))
        assertEquals(BrewPreparation.State.CANCEL_WRITTEN, preparation.state)
        preparation.consumed()
        val second = requireNotNull(preparation.begin("curve-b", 90))
        assertTrue(preparation.written(second, OperationResult.Success(), 0))
        preparation.disconnected()
        assertEquals(BrewPreparation.State.UNKNOWN, preparation.state)
    }

    @Test fun durableRecoveryDoesNotInventAProfileOrTemperature() {
        val preparation = BrewPreparation()
        assertTrue(preparation.restoreUnknown())
        assertNull(preparation.profileId)
        assertNull(preparation.targetC)
        assertFalse(preparation.restoreUnknown())
        val token = requireNotNull(preparation.beginCancel())
        assertTrue(preparation.cancelled(token, OperationResult.Success()))
        assertEquals(BrewPreparation.State.CANCEL_WRITTEN, preparation.state)
    }

    @Test fun timeoutWithoutSafeCancellationBecomesUnknown() {
        val preparation = BrewPreparation()
        val token = requireNotNull(preparation.begin("curve-a", 92))
        assertTrue(preparation.written(token, OperationResult.Success(), 0))
        assertTrue(preparation.timedOut(token))
        assertEquals(BrewPreparation.State.UNKNOWN, preparation.state)
        assertFalse(preparation.timedOut(token))
        assertNotNull(preparation.beginCancel())
    }

    @Test fun correctedTemperatureUsesSameOneDegreeRuleAsLegacy() {
        assertEquals(9150, BrewPreparation.correctedTemperature(9200, 5))
        assertTrue(BrewPreparation.isAtTarget(9100, 92))
        assertTrue(BrewPreparation.isAtTarget(9300, 92))
        assertFalse(BrewPreparation.isAtTarget(9099, 92))
        assertFalse(BrewPreparation.isAtTarget(9301, 92))
    }
}
