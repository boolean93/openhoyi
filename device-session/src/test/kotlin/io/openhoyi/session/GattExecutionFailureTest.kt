package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange
import org.junit.Assert.*
import org.junit.Test

class GattExecutionFailureTest {
    private val write = GattOperation.Write(KnownGatt.coffeeWrite, byteArrayOf(4, 93, 89))
    private class Driver : GattDriver {
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        var closes = 0
        var throwOnWrite = true
        var throwOnClose = false
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            calls += Triple(generation, token, operation)
            if (operation is GattOperation.Write && throwOnWrite) throw IllegalStateException("Injected after submission boundary")
            return true
        }
        override fun close(generation: Long) {
            closes++
            if (throwOnClose) throw IllegalStateException("Injected close failure")
        }
    }
    private fun verifyUnknown(closeThrows: Boolean) {
        val driver = Driver().apply { throwOnClose = closeThrows }
        val invalidations = mutableListOf<String>()
        val queue = GattQueue(driver, { 0 }) { invalidations += it }
        queue.open()
        val generation = queue.generation
        val results = mutableListOf<Pair<Int, OperationResult>>()
        queue.enqueue(GattOperation.Connect("fixture"), 1000) { results += 0 to it }
        queue.enqueue(write, 1000) { results += 1 to it }
        queue.enqueue(GattOperation.Discover, 1000) { results += 2 to it }
        val first = driver.calls.first()
        queue.complete(first.first, first.second, OperationResult.Success())
        assertEquals(listOf(0, 1, 2), results.map { it.first })
        assertTrue(results[0].second is OperationResult.Success)
        assertEquals(OperationResult.Unknown("transport execution exception"), results[1].second)
        assertEquals(OperationResult.Cancelled("transport execution exception"), results[2].second)
        assertEquals(2, driver.calls.size)
        assertEquals(1, driver.closes)
        assertEquals(listOf("transport execution exception"), invalidations)
        assertFalse(queue.active)
        assertFalse(queue.inFlight)
        val failed = driver.calls.last()
        queue.complete(failed.first, failed.second, OperationResult.Success())
        queue.tick()
        assertEquals(3, results.size)
        assertEquals(2, driver.calls.size)
        queue.enqueue(write, 1000) { results += 3 to it }
        assertEquals(OperationResult.Cancelled("not connected"), results.last().second)
        assertEquals(2, driver.calls.size)
        driver.throwOnWrite = false
        queue.open()
        assertTrue(queue.generation > generation)
        queue.enqueue(write, 1000) { results += 4 to it }
        assertEquals(3, driver.calls.size)
        queue.complete(generation, failed.second, OperationResult.Success())
        assertEquals(4, results.size)
        val fresh = driver.calls.last()
        queue.complete(fresh.first, fresh.second, OperationResult.Success())
        assertEquals(5, results.size)
        assertTrue(results.last().second is OperationResult.Success)
    }
    @Test fun thrownExecutionKeepsUnknownAndCancelsUnsentWork() = verifyUnknown(false)
    @Test fun closeFailureDoesNotLoseUnknownOrPendingCallbacks() = verifyUnknown(true)
    @Test fun explicitFalseAndGuardExceptionKeepDefiniteRejection() {
        var calls = 0
        var invalidations = 0
        val queue = GattQueue(object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean { calls++; return false }
            override fun close(generation: Long) = error("Rejection must not close transport")
        }, { 0 }) { invalidations++ }
        queue.open()
        val results = mutableListOf<OperationResult>()
        queue.enqueue(write, 1000) { results += it }
        queue.enqueue(write, 1000, beforeDispatch = { error("Injected guard failure before execution") }) { results += it }
        assertEquals(listOf(OperationResult.Failed("transport rejected operation"),
            OperationResult.Failed("pre-dispatch guard rejected operation")), results)
        assertEquals(1, calls)
        assertEquals(0, invalidations)
        assertTrue(queue.active)
        assertFalse(queue.inFlight)
    }
    @Test fun synchronousCompletionThenThrowDoesNotCompleteOperationTwice() {
        lateinit var queue: GattQueue
        var closes = 0
        var invalidations = 0
        val results = mutableListOf<OperationResult>()
        queue = GattQueue(object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                queue.complete(generation, token, OperationResult.Success())
                throw IllegalStateException("Injected after synchronous callback")
            }
            override fun close(generation: Long) { closes++ }
        }, { 0 }) { invalidations++ }
        queue.open()
        queue.enqueue(write, 1000) { results += it }
        assertEquals(listOf(OperationResult.Success()), results)
        assertFalse(queue.active)
        assertEquals(1, closes)
        assertEquals(1, invalidations)
    }
    @Test fun oldExecutionExceptionCannotInvalidateReentrantNewGeneration() {
        lateinit var queue: GattQueue
        var oldGeneration = 0L
        var closes = 0
        var invalidations = 0
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        val results = mutableListOf<Pair<Int, OperationResult>>()
        queue = GattQueue(object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += Triple(generation, token, operation)
                if (generation == oldGeneration) {
                    queue.complete(generation, token, OperationResult.Success())
                    throw IllegalStateException("Old execution threw after reconnect callback")
                }
                return true
            }
            override fun close(generation: Long) { closes++ }
        }, { 0 }) { invalidations++ }
        queue.open()
        oldGeneration = queue.generation
        queue.enqueue(write, 1000) {
            results += 0 to it
            queue.open() // Explicit new owner, inside the completed operation's observer.
            queue.enqueue(GattOperation.Discover, 1000) { next -> results += 1 to next }
        }
        assertTrue(queue.generation > oldGeneration)
        assertEquals(listOf(0), results.map { it.first })
        assertTrue(results.single().second is OperationResult.Success)
        assertEquals(2, calls.size)
        assertEquals(1, closes) // Only the explicit reconnect closed the old owner.
        assertEquals(0, invalidations)
        assertTrue(queue.active)
        assertTrue(queue.inFlight)
        val fresh = calls.last()
        queue.complete(fresh.first, fresh.second, OperationResult.Success())
        assertEquals(listOf(0, 1), results.map { it.first })
        assertTrue(results.last().second is OperationResult.Success)
        assertTrue(queue.active)
    }
    @Test fun unknownCallbackReconnectIsNotInvalidatedByOldExecution() {
        val driver = Driver()
        lateinit var queue: GattQueue
        var invalidations = 0
        queue = GattQueue(driver, { 0 }) {
            invalidations++
            queue.disconnect("host invalidated")
        }
        queue.open()
        val oldGeneration = queue.generation
        val results = mutableListOf<Pair<Int, OperationResult>>()
        queue.enqueue(GattOperation.Connect("fixture"), 1000) { results += 0 to it }
        queue.enqueue(write, 1000) {
            results += 1 to it
            assertTrue(it is OperationResult.Unknown)
            queue.open() // Explicit recovery after the old operation became unknown.
            queue.enqueue(GattOperation.Discover, 1000) { fresh -> results += 3 to fresh }
        }
        queue.enqueue(GattOperation.Discover, 1000) { results += 2 to it }
        val first = driver.calls.first()
        queue.complete(first.first, first.second, OperationResult.Success())
        assertEquals(listOf(0, 1, 2), results.map { it.first })
        assertTrue(results[2].second is OperationResult.Cancelled)
        assertTrue(queue.generation > oldGeneration)
        assertTrue(queue.active)
        assertTrue(queue.inFlight)
        assertEquals(1, driver.closes)
        assertEquals(0, invalidations)
        assertEquals(3, driver.calls.size)
        val fresh = driver.calls.last()
        queue.complete(fresh.first, fresh.second, OperationResult.Success())
        assertEquals(listOf(0, 1, 2, 3), results.map { it.first })
        assertTrue(results.last().second is OperationResult.Success)
    }
    @Test fun actualSessionWriteRetainsUnknownTracker() {
        val fixture = CoffeeSessionFixture()
        val change = MachineSettingChange.BrewTemperature(93)
        val tracker = SettingsWriteTracker()
        val token = requireNotNull(tracker.begin(change))
        fixture.executeFailure = IllegalStateException("Injected driver execution failure")
        val before = fixture.calls.size
        val results = mutableListOf<OperationResult>()
        fixture.session.writeSetting(change) {
            results += it
            assertTrue(tracker.written(token, it, 1))
        }
        assertEquals(1, results.size)
        assertTrue(results.single() is OperationResult.Unknown)
        assertEquals(SettingsWriteTracker.State.UNKNOWN, tracker.state)
        assertEquals(DeviceState.FAILED, fixture.session.state)
        assertNull(fixture.session.address)
        assertEquals(before + 1, fixture.calls.size)
        assertEquals(1, fixture.closes)
        fixture.session.writeSetting(change) { assertTrue(it is OperationResult.Failed) }
        assertEquals(before + 1, fixture.calls.size)
        assertNull(tracker.begin(change))
    }
}
