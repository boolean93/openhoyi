package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

/** Fake transport only: every detached waiter must settle, even if host failure reporting throws. */
class GattQueueCleanupTest {
    private class Driver : GattDriver {
        var closes = 0
        var executes = 0
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            executes++
            return true
        }
        override fun close(generation: Long) { closes++ }
    }
    private fun verify(failingIndices: Set<Int>, distinctErrors: Boolean = false) {
        val driver = Driver()
        val hostError = IllegalStateException("Intentional host invalidation failure")
        var invalidations = 0
        val reportedErrors = mutableListOf<Exception>()
        val queue = GattQueue(driver, { 0L }) {
            invalidations++
            val error = if (distinctErrors) IllegalStateException("Host invalidation $invalidations") else hostError
            reportedErrors += error
            throw error
        }
        queue.open()
        val generation = queue.generation
        val results = mutableListOf<Pair<Int, OperationResult>>()
        repeat(4) { index ->
            queue.enqueue(GattOperation.Connect("fixture-device"), 1000) { result ->
                results += index to result
                if (index in failingIndices) throw IllegalArgumentException("Intentional operation observer failure")
            }
        }
        val failure = runCatching { queue.disconnect("fixture disconnect") }.exceptionOrNull()
        assertEquals("Every detached operation must settle once", listOf(0, 1, 2, 3), results.map { it.first })
        assertEquals(OperationResult.Unknown("fixture disconnect"), results[0].second)
        results.drop(1).forEach { assertEquals(OperationResult.Cancelled("fixture disconnect"), it.second) }
        if (failingIndices.isEmpty()) assertNull(failure) else assertSame(reportedErrors.first(), failure)
        if (distinctErrors) assertEquals(reportedErrors.drop(1), failure!!.suppressed.toList())
        else if (failure != null) assertTrue(failure.suppressed.isEmpty())
        assertEquals(failingIndices.size, invalidations)
        assertEquals(1, driver.closes)
        assertEquals(1, driver.executes)
        assertFalse(queue.active)
        assertFalse(queue.inFlight)
        assertEquals(generation, queue.generation)
        queue.disconnect("repeat")
        assertEquals(4, results.size)
        assertEquals(1, driver.closes)
    }
    @Test fun ordinaryDisconnectKeepsUnknownAndCancelledResults() = verify(emptySet())
    @Test fun runningObserverAndInvalidationFailureDoNotSkipWaiters() = verify(setOf(0))
    @Test fun waitingObserverAndInvalidationFailureDoNotSkipLaterWaiters() = verify(setOf(1))
    @Test fun distinctInvalidationErrorsAreRetainedAfterAllWaitersSettle() = verify(setOf(0, 1, 2), distinctErrors = true)
    @Test fun repeatedSameInvalidationErrorStillSettlesAllWaiters() = verify(setOf(0, 1, 2))
}
