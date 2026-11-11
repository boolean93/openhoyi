package io.openhoyi.session

import io.openhoyi.protocol.ScaleObservation
import org.junit.Assert.*
import org.junit.Test

/** The Android owner has the same close boundary, with its ticker stopped first. */
class ScaleTransportCloseUncertainTest {
    private val selection = ScaleSelection("AA:BB:CC:DD:EE:01", BookooScaleProtocolAdapter.id)
    private class Driver : GattDriver {
        var closes = 0
        var executes = 0
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            executes++
            return true
        }
        override fun close(generation: Long) { closes++; throw IllegalStateException("physical close failed") }
    }
    @Test fun queueSettlesEveryWaiterOnceAndNeverReopensAfterCloseFailure() {
        val driver = Driver(); val queue = GattQueue(driver, { 0L })
        val old = queue.open()
        val results = mutableListOf<OperationResult>()
        repeat(3) { queue.enqueue(GattOperation.Connect("scale"), 1000) { results += it } }
        queue.disconnect("disconnect")
        assertEquals(1,driver.closes)
        assertEquals(1,driver.executes)
        assertEquals(1,results.count {it is OperationResult.Unknown})
        assertEquals(2,results.count {it is OperationResult.Cancelled})
        assertNotNull(queue.transportCloseFailure)
        queue.disconnect("again")
        assertThrows(TransportCloseUncertainException::class.java) { queue.open() }
        queue.complete(old,1,OperationResult.Success())
        assertEquals(3,results.size)
        assertEquals(1,driver.closes)
    }
    @Test fun openCannotAdvanceGenerationWhenClosingPreviousGenerationFails() {
        val driver=Driver();val queue=GattQueue(driver,{0L})
        val original=queue.open()
        assertThrows(TransportCloseUncertainException::class.java) {queue.open()}
        assertEquals(original,queue.generation)
        assertFalse(queue.active)
        assertEquals(1,driver.closes)
        assertEquals(0,driver.executes)
    }
    @Test fun callbackReentrantOpenIsDeniedButEveryDetachedWaiterStillSettles() {
        val driver=Driver();lateinit var queue:GattQueue
        val results=mutableListOf<Pair<Int,OperationResult>>()
        var denied=0
        queue=GattQueue(driver,{0L})
        queue.open();val original=queue.generation
        queue.enqueue(GattOperation.Connect("scale"),1000) { result ->
            results+=0 to result
            try {queue.open()} catch (_:TransportCloseUncertainException) {denied++}
        }
        queue.enqueue(GattOperation.Discover,1000) {results+=1 to it}
        queue.enqueue(GattOperation.Discover,1000) {results+=2 to it}
        queue.disconnect("disconnect")
        assertEquals(1,denied)
        assertEquals(listOf(0,1,2),results.map {it.first})
        assertTrue(results[0].second is OperationResult.Unknown)
        assertTrue(results[1].second is OperationResult.Cancelled)
        assertTrue(results[2].second is OperationResult.Cancelled)
        assertEquals(original,queue.generation)
        assertEquals(1,driver.closes)
    }
    private class Owner(val session: DeviceSession) : AutoCloseable {
        override fun close() {
            session.disconnect()
            session.transportCloseFailure?.let { throw IllegalStateException("transport close uncertain",it) }
        }
    }
    @Test fun sessionAndSingleSlotRejectReplacementAfterDisconnectOrTimeoutCloseFailure() {
        for (timeout in listOf(false,true)) {
            val driver=Driver();var now=0L;var callbacks=0
            val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},scaleObservation={ _:ScaleObservation->callbacks++ })
            val slot=SingleScaleConnectionSlot<Owner>()
            val lease=slot.replace(selection) {Owner(session)}
            session.connect(selection.address)
            val generation=session.generation
            if(timeout) { now=22_000;session.tick() } else session.disconnect()
            assertNotNull(session.transportCloseFailure)
            assertEquals(1,driver.closes)
            assertEquals(0,callbacks)
            session.onNotification(generation,KnownGatt.bookooNotify,ByteArray(20))
            assertEquals(0,callbacks)
            val executed=driver.executes
            session.connect(selection.address)
            assertEquals(DeviceState.FAILED,session.state)
            assertEquals(executed,driver.executes)
            var factories=0
            assertThrows(IllegalStateException::class.java) {
                slot.replace(selection.copy(address="AA:BB:CC:DD:EE:02")) {factories++;Owner(session)}
            }
            assertTrue(slot.blocked);assertNull(slot.current)
            assertFalse(slot.owns(lease.ticket));assertEquals(0,factories)
        }
    }
}
