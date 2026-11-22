package io.openhoyi.session

import io.openhoyi.protocol.StartParameters
import org.junit.Assert.*
import org.junit.Test

class ShotStartEvidenceTest {
    private val parameters = StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)

    // The decoded field is slot-or-phase; this observed phase need not equal requested slot 7.
    private fun activity(f: CoffeeSessionFixture, receivedAt: Long, elapsed: Int, status: Int = 0x40,
                         generation: Long = f.session.generation, endpoint: Endpoint = KnownGatt.coffeeNotify,
                         capturedAt:Long = receivedAt) {
        f.now = receivedAt
        val frame = f.hex("80080900000000052421325103")
        frame[3] = (elapsed shr 8).toByte()
        frame[4] = elapsed.toByte()
        frame[11] = status.toByte()
        f.session.onNotification(generation,endpoint,frame,capturedAt)
    }

    private fun readyIdle(f: CoffeeSessionFixture, at:Long) {
        f.now=at
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,f.hex("400023F02F1C770B00000000000000190321AF"))
    }
    private fun session(): CoffeeSessionFixture = CoffeeSessionFixture(trackCoffeeDelivery=true).also { readyIdle(it,0) }

    @Test fun earlyRealIngressIsIndependentOfTheTransportCallback() {
        val f = session()
        val owner = requireNotNull(f.session.captureStartContext(parameters))
        var result: OperationResult? = null
        f.session.startExtraction(parameters,owner) { result = it }
        assertEquals(ShotStartObservation.WAITING_FOR_MACHINE, f.session.shotStartObservation(owner))
        activity(f,21,0,capturedAt=0) // Delivered after handoff, captured at the cut, thus not evidence.
        activity(f,28,1,capturedAt=0)
        assertEquals(ShotStartObservation.WAITING_FOR_MACHINE, f.session.shotStartObservation(owner))
        activity(f,100,0)
        activity(f,200,0)
        assertEquals(ShotStartObservation.WAITING_FOR_MACHINE, f.session.shotStartObservation(owner))
        activity(f,1100,1)
        assertNull(result)
        assertEquals(ShotStartObservation.MACHINE_EXTRACTION_OBSERVED, f.session.shotStartObservation(owner))
        f.complete()
        assertTrue(result is OperationResult.Success)
        readyIdle(f,1200)
        val nextOwner = requireNotNull(f.session.captureStartContext(parameters))
        assertNull(f.session.shotStartObservation(nextOwner))
        assertEquals(ShotStartObservation.MACHINE_EXTRACTION_OBSERVED, f.session.shotStartObservation(owner))
    }

    @Test fun ackAloneOldCupOldOwnerAndPreheatDoNotConfirmAndNoRetryAtTimeout() {
        val f = session()
        val owner = requireNotNull(f.session.captureStartContext(parameters))
        f.session.startExtraction(parameters,owner) { }
        f.complete()
        val count = f.calls.size
        activity(f,100,8)
        activity(f,1100,9)
        activity(f,1200,0,generation=f.session.generation-1)
        activity(f,1300,0,endpoint=KnownGatt.coffeeWrite)
        activity(f,1400,0)
        activity(f,1900,1,status=0x60)
        activity(f,2500,2)
        f.idle(2600)
        activity(f,3500,3)
        assertEquals(ShotStartObservation.WAITING_FOR_MACHINE, f.session.shotStartObservation(owner))
        activity(f,8000,0)
        activity(f,9000,9) // Both fit the absolute window; elapsed progression does not.
        assertEquals(ShotStartObservation.WAITING_FOR_MACHINE, f.session.shotStartObservation(owner))
        f.now=10000
        activity(f,10001,8,capturedAt=8500) // Delay/expired observation does not revive receipt.
        activity(f,10002,9,capturedAt=9500)
        assertEquals(ShotStartObservation.NOT_CONFIRMED,f.session.shotStartObservation(owner))
        assertEquals(count,f.calls.size)
        f.session.disconnect()
        assertEquals(ShotStartObservation.NOT_CONFIRMED,f.session.shotStartObservation(owner))
    }

    @Test fun pendingWriteNotYetHandedOffDoesNotAcceptMachineActivity() {
        val f = session()
        val owner = requireNotNull(f.session.captureStartContext(parameters))
        f.session.setBrewWait(0,{true}) { }
        var result: OperationResult? = null
        f.session.startExtraction(parameters,owner) { result = it }
        assertNull(result) // The start is queued, not rejected by setup.
        val count = f.calls.size
        activity(f,100,0)
        activity(f,1100,1)
        assertNull(f.session.shotStartObservation(owner))
        f.complete()
        assertTrue(result is OperationResult.Failed)
        assertEquals(count,f.calls.size)
    }
}
