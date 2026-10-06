package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class GattOpenAtomicityTest {
    private class Fixture {
        var closes = 0
        val calls = mutableListOf<Pair<Long, Long>>()
        val results = mutableListOf<Pair<Int, OperationResult>>()
        val invalidations = mutableListOf<String>()
        var onClose: () -> Unit = {}
        var onInvalidated: () -> Unit = {}
        val queue: GattQueue = GattQueue(object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += generation to token; return true
            }
            override fun close(generation: Long) { closes++; onClose() }
        }, { 0 }) { reason -> invalidations += reason; onInvalidated() }
        fun enqueue(index: Int, callback: (OperationResult) -> Unit = {}) {
            queue.enqueue(GattOperation.Discover, 1000) { results += index to it; callback(it) }
        }
        fun assertFreshOwner(oldGeneration: Long, expectedIndices: List<Int>) {
            assertEquals(oldGeneration + 1, queue.generation)
            assertTrue(queue.active)
            assertFalse(queue.inFlight)
            assertEquals(expectedIndices, results.map { it.first })
            assertEquals(1, closes)
            assertEquals(1, calls.size)
            enqueue(10)
            val fresh = calls.last()
            assertEquals(queue.generation, fresh.first)
            val old = calls.first()
            queue.complete(old.first, old.second, OperationResult.Success())
            assertEquals(expectedIndices, results.map { it.first })
            queue.complete(fresh.first, fresh.second, OperationResult.Success())
            assertEquals(expectedIndices + 10, results.map { it.first })
            assertTrue(results.last().second is OperationResult.Success)
        }
    }
    @Test fun oldObserverCannotOpenAndSubmitDuringReplacement() {
        val f = Fixture(); f.queue.open(); val oldGeneration = f.queue.generation
        var nestedFailure: Throwable? = null
        f.enqueue(0) {
            nestedFailure = runCatching { f.queue.open(); f.enqueue(2) }.exceptionOrNull()
        }
        f.enqueue(1)
        val freshGeneration = f.queue.open()
        assertTrue(nestedFailure is IllegalStateException)
        assertEquals("reentrant queue open", nestedFailure!!.message)
        assertEquals(oldGeneration + 1, freshGeneration)
        assertTrue(f.results[0].second is OperationResult.Unknown)
        assertTrue(f.results[1].second is OperationResult.Cancelled)
        assertTrue(f.invalidations.isEmpty())
        f.assertFreshOwner(oldGeneration, listOf(0, 1))
    }
    @Test fun uncaughtNestedOpenStillSettlesAllOldWaitersBeforeGrantingNewOwner() {
        val f = Fixture(); f.queue.open(); val oldGeneration = f.queue.generation
        f.enqueue(0) { f.queue.open(); f.enqueue(3) }
        f.enqueue(1); f.enqueue(2)
        f.queue.open()
        assertEquals(listOf("operation callback failure"), f.invalidations)
        assertTrue(f.results[0].second is OperationResult.Unknown)
        assertTrue(f.results.drop(1).all { it.second is OperationResult.Cancelled })
        f.assertFreshOwner(oldGeneration, listOf(0, 1, 2))
    }
    @Test fun driverCloseCannotOpenACompetingOwnerDuringReplacement() {
        val f = Fixture(); f.queue.open(); val oldGeneration = f.queue.generation
        f.enqueue(0)
        var nestedFailure: Throwable? = null
        f.onClose = { nestedFailure = runCatching { f.queue.open(); f.enqueue(2) }.exceptionOrNull() }
        f.queue.open()
        assertTrue(nestedFailure is IllegalStateException)
        assertTrue(f.invalidations.isEmpty())
        f.assertFreshOwner(oldGeneration, listOf(0))
    }
    @Test fun reportingFailureAbortsReplacementAndReleasesGuardForExplicitRetry() {
        val f = Fixture(); f.queue.open(); val oldGeneration = f.queue.generation
        val reportError = IllegalStateException("intentional host reporting failure")
        f.onInvalidated = { throw reportError }
        f.enqueue(0) { throw IllegalArgumentException("intentional observer failure") }
        f.enqueue(1)
        assertSame(reportError, runCatching { f.queue.open() }.exceptionOrNull())
        assertEquals(oldGeneration, f.queue.generation)
        assertFalse(f.queue.active)
        assertFalse(f.queue.inFlight)
        assertEquals(listOf(0, 1), f.results.map { it.first })
        f.onInvalidated = {}
        f.queue.open()
        f.assertFreshOwner(oldGeneration, listOf(0, 1))
    }
    @Test fun ordinaryReplacementPreservesUnknownCancelledAndFreshCompletion() {
        val f = Fixture(); f.queue.open(); val oldGeneration = f.queue.generation
        f.enqueue(0); f.enqueue(1)
        f.queue.open()
        assertEquals(OperationResult.Unknown("replaced"), f.results[0].second)
        assertEquals(OperationResult.Cancelled("replaced"), f.results[1].second)
        assertTrue(f.invalidations.isEmpty())
        f.assertFreshOwner(oldGeneration, listOf(0, 1))
    }
}
