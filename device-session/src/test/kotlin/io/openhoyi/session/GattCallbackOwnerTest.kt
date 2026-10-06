package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class GattCallbackOwnerTest {
    private class Fixture {
        var now = 0L
        var closes = 0
        val calls = mutableListOf<Pair<Long, Long>>()
        val invalidations = mutableListOf<String>()
        val results = mutableListOf<Pair<Int, OperationResult>>()
        val queue: GattQueue = GattQueue(object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += generation to token; return true
            }
            override fun close(generation: Long) { closes++ }
        }, { now }) { reason ->
            invalidations += reason
            queueDisconnectFromHost()
        }
        private fun queueDisconnectFromHost() = queue.disconnect("host invalidated")
        fun enqueue(index: Int, observer: (OperationResult) -> Unit = {}) {
            queue.enqueue(GattOperation.Discover, 1000) {
                results += index to it
                observer(it)
            }
        }
        fun complete() {
            val call = calls.last()
            queue.complete(call.first, call.second, OperationResult.Success())
        }
        fun reconnect() { queue.open(); enqueue(10) }
        fun assertNewOwnerSurvives(oldGeneration: Long, oldIndices: List<Int>) {
            assertTrue(queue.generation > oldGeneration)
            assertTrue(queue.active)
            assertTrue(queue.inFlight)
            assertEquals(oldIndices, results.map { it.first })
            assertTrue(invalidations.isEmpty())
            assertEquals(1, closes)
            assertEquals(2, calls.size)
            val old = calls.first()
            queue.complete(old.first, old.second, OperationResult.Success())
            assertEquals(oldIndices, results.map { it.first })
            complete()
            assertEquals(oldIndices + 10, results.map { it.first })
            assertTrue(results.last().second is OperationResult.Success)
            assertTrue(queue.active)
        }
    }
    @Test fun successfulObserverReconnectThenThrowDoesNotCloseNewOwner() {
        val f = Fixture(); f.queue.open(); val generation = f.queue.generation
        f.enqueue(0) { f.reconnect(); throw IllegalArgumentException("old observer failure") }
        f.complete()
        assertTrue(f.results.first().second is OperationResult.Success)
        f.assertNewOwnerSurvives(generation, listOf(0))
    }
    @Test fun timeoutObserverReconnectDoesNotInvalidateNewOwner() {
        val f = Fixture(); f.queue.open(); val generation = f.queue.generation
        f.enqueue(0) { assertTrue(it is OperationResult.Unknown); f.reconnect() }
        f.enqueue(1)
        f.now = 1000; f.queue.tick()
        assertEquals(OperationResult.Unknown("operation timeout"), f.results[0].second)
        assertEquals(OperationResult.Cancelled("operation timeout"), f.results[1].second)
        f.assertNewOwnerSurvives(generation, listOf(0, 1))
    }
    @Test fun detachedOldWaiterFailureDoesNotInvalidateReconnectedOwner() {
        val f = Fixture(); f.queue.open(); val generation = f.queue.generation
        f.enqueue(0) { assertTrue(it is OperationResult.Unknown); f.reconnect() }
        f.enqueue(1) { assertTrue(it is OperationResult.Cancelled); throw IllegalArgumentException("detached old waiter") }
        f.queue.disconnect("explicit disconnect")
        f.assertNewOwnerSurvives(generation, listOf(0, 1))
    }
    @Test fun reconnectDuringCallbackFailureCleanupPreservesNewOwnerAndAllWaiters() {
        val f = Fixture(); f.queue.open(); val generation = f.queue.generation
        f.enqueue(0) { throw IllegalArgumentException("current observer failed") }
        f.enqueue(1) { assertTrue(it is OperationResult.Cancelled); f.reconnect() }
        f.enqueue(2)
        f.complete()
        assertEquals(OperationResult.Cancelled("operation callback failure"), f.results[1].second)
        assertEquals(OperationResult.Cancelled("operation callback failure"), f.results[2].second)
        f.assertNewOwnerSurvives(generation, listOf(0, 1, 2))
    }
    @Test fun sameOwnerCallbackFailureAndTimeoutStillInvalidateWithoutRetry() {
        for (timeout in listOf(false, true)) {
            val f = Fixture(); f.queue.open()
            f.enqueue(0) { if (!timeout) throw IllegalArgumentException("same owner observer failure") }
            f.enqueue(1)
            if (timeout) { f.now = 1000; f.queue.tick() } else f.complete()
            val reason = if (timeout) "operation timeout" else "operation callback failure"
            assertEquals(listOf(reason), f.invalidations)
            assertEquals(1, f.closes)
            assertFalse(f.queue.active)
            assertFalse(f.queue.inFlight)
            assertEquals(listOf(0, 1), f.results.map { it.first })
            assertEquals(OperationResult.Cancelled(reason), f.results[1].second)
            assertEquals(1, f.calls.size)
            f.queue.tick()
            assertEquals(1, f.calls.size)
        }
    }
    @Test fun cleanupReportingPreservesFirstErrorAndSuppressesLaterHostFailure() {
        val first = IllegalStateException("first invalidation failure")
        val second = IllegalStateException("later invalidation failure")
        var invalidations = 0
        var token = 0L
        lateinit var queue: GattQueue
        queue = GattQueue(object : GattDriver {
            override fun execute(generation: Long, value: Long, operation: GattOperation): Boolean { token = value; return true }
            override fun close(generation: Long) = Unit
        }, { 0 }) {
            invalidations++
            throw if (invalidations == 1) first else second
        }
        queue.open()
        queue.enqueue(GattOperation.Discover, 1000) { throw IllegalArgumentException("running observer") }
        queue.enqueue(GattOperation.Discover, 1000) { throw IllegalArgumentException("waiting observer") }
        val failure = runCatching { queue.complete(queue.generation, token, OperationResult.Success()) }.exceptionOrNull()
        assertSame(first, failure)
        assertEquals(listOf(second), failure!!.suppressed.toList())
        assertEquals(2, invalidations)
        assertFalse(queue.active)
        assertFalse(queue.inFlight)
    }
}
