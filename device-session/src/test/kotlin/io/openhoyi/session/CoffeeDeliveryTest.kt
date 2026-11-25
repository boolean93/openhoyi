package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class CoffeeDeliveryTest {
    private class Driver : GattDriver {
        val calls = mutableListOf<Pair<Long, Long>>()
        var accepted = true
        var throwAfterHandoff = false
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            calls += generation to token
            if (throwAfterHandoff) error("possibly submitted")
            return accepted
        }
        override fun close(generation: Long) = Unit
    }

    private class Fixture(enabled: Boolean = true) {
        val driver = Driver()
        val queue = GattQueue(driver, { 0L }).apply { if (enabled) enableCoffeeDelivery(); open() }
        val results = mutableListOf<OperationResult>()
        fun enqueue(kind: CoffeeDeliveryKind, urgent: Boolean = false, permit: () -> Boolean = { true }) =
            queue.enqueueTagged(GattOperation.Write(KnownGatt.coffeeWrite, byteArrayOf(1), true),
                5000, urgent, permit, kind) { results += it }
        fun phase(kind: CoffeeDeliveryKind) = queue.coffeeDeliveries().single { it.kind == kind }.phase
        fun ack(call: Pair<Long, Long> = driver.calls.last()) =
            queue.complete(call.first, call.second, OperationResult.Success())
    }

    @Test fun queueRecordsTransportOnlyAndUrgentStopDoesNotPreemptInFlight() = with(Fixture()) {
        enqueue(CoffeeDeliveryKind.START)
        enqueue(CoffeeDeliveryKind.BOILER)
        assertEquals(CoffeeDeliveryPhase.DRIVER_HANDOFF, phase(CoffeeDeliveryKind.START))
        assertEquals(CoffeeDeliveryPhase.QUEUED, phase(CoffeeDeliveryKind.BOILER))
        queue.cancelPending { it is GattOperation.Write }
        enqueue(CoffeeDeliveryKind.STOP, urgent = true)
        assertEquals(CoffeeDeliveryPhase.NOT_SENT, phase(CoffeeDeliveryKind.BOILER))
        assertEquals(CoffeeDeliveryPhase.QUEUED, phase(CoffeeDeliveryKind.STOP))
        assertEquals(1, driver.calls.size)
        ack()
        assertEquals(CoffeeDeliveryPhase.TRANSPORT_CALLBACK_OK, phase(CoffeeDeliveryKind.START))
        assertEquals(CoffeeDeliveryPhase.DRIVER_HANDOFF, phase(CoffeeDeliveryKind.STOP))
        ack()
        assertEquals(CoffeeDeliveryPhase.TRANSPORT_CALLBACK_OK, phase(CoffeeDeliveryKind.STOP))
        assertEquals(2, driver.calls.size)
    }

    @Test fun disconnectIsUnknownOnlyAfterHandoffAndNeverReplaysAcrossGeneration() = with(Fixture()) {
        enqueue(CoffeeDeliveryKind.SCHEDULE_FIRST)
        val old = driver.calls.single()
        enqueue(CoffeeDeliveryKind.SCHEDULE_SECOND)
        queue.disconnect("link lost")
        assertEquals(CoffeeDeliveryPhase.OUTCOME_UNKNOWN, phase(CoffeeDeliveryKind.SCHEDULE_FIRST))
        assertEquals(CoffeeDeliveryPhase.NOT_SENT, phase(CoffeeDeliveryKind.SCHEDULE_SECOND))
        queue.open()
        assertTrue(queue.coffeeDeliveries().isEmpty())
        enqueue(CoffeeDeliveryKind.SLEEP)
        ack(old)
        assertEquals(CoffeeDeliveryPhase.DRIVER_HANDOFF, phase(CoffeeDeliveryKind.SLEEP))
        assertEquals(2, driver.calls.size)
    }

    @Test fun deniedGuardIsNotSentButDriverExceptionIsUnknown() = with(Fixture()) {
        enqueue(CoffeeDeliveryKind.SETTING, permit = { false })
        assertEquals(CoffeeDeliveryPhase.NOT_SENT, phase(CoffeeDeliveryKind.SETTING))
        assertTrue(driver.calls.isEmpty())
        driver.throwAfterHandoff = true
        enqueue(CoffeeDeliveryKind.CUP_RESET)
        assertEquals(CoffeeDeliveryPhase.OUTCOME_UNKNOWN, phase(CoffeeDeliveryKind.CUP_RESET))
    }

    @Test fun explicitDriverRefusalIsNotSentWhereasDisconnectedAvailsNoReceipt() = with(Fixture()) {
        driver.accepted = false
        enqueue(CoffeeDeliveryKind.STORED_RECIPE)
        assertEquals(CoffeeDeliveryPhase.NOT_SENT, phase(CoffeeDeliveryKind.STORED_RECIPE))
        queue.disconnect("link lost")
        enqueue(CoffeeDeliveryKind.PREHEAT)
        assertFalse(queue.coffeeDeliveries().any { it.kind == CoffeeDeliveryKind.PREHEAT })
    }

    @Test fun observationHookCannotDispatchOnADetachedOwnerOrBreakAnOrdinaryWrite() = with(Fixture()) {
        val write = GattOperation.Write(KnownGatt.coffeeWrite,byteArrayOf(1),true)
        queue.enqueueTagged(write,5000,false,{true},CoffeeDeliveryKind.START,
            onHandoff={queue.disconnect("pre-driver observer detached owner")}) { results += it }
        assertTrue(driver.calls.isEmpty())
        assertEquals(CoffeeDeliveryPhase.NOT_SENT,phase(CoffeeDeliveryKind.START))
        queue.open()
        queue.enqueueTagged(write,5000,false,{true},CoffeeDeliveryKind.START,
            onHandoff={error("observation failed")}) { results += it }
        assertEquals(1,driver.calls.size)
        ack()
        assertTrue(results.last() is OperationResult.Success)
    }

    @Test fun guardCanDisconnectBeforeDriverHandoffWithoutClaimingUnknownDelivery() = with(Fixture()) {
        enqueue(CoffeeDeliveryKind.SETTING, permit = {
            queue.disconnect("reentrant before dispatch")
            true
        })
        assertEquals(CoffeeDeliveryPhase.NOT_SENT, phase(CoffeeDeliveryKind.SETTING))
        assertTrue(driver.calls.isEmpty())
        assertTrue(results.single() is OperationResult.Unknown) // Legacy result stays unchanged.
    }

    @Test fun defaultOffPreservesHistoricalCallbacksWithoutPublishingProjection() = with(Fixture(false)) {
        enqueue(CoffeeDeliveryKind.SETTINGS_REFRESH)
        ack()
        assertTrue(queue.coffeeDeliveries().isEmpty())
        assertTrue(results.single() is OperationResult.Success)
    }
}
