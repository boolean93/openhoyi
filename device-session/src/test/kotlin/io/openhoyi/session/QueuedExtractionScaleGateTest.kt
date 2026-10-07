package io.openhoyi.session

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.StartParameters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Actual controller, coffee adapter, Session and queue; only the BLE driver and scale are fake. */
internal class QueuedExtractionFixture(val target:Int=3400) {
    val f=CoffeeSessionFixture()
    val profile=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
    class Scale:ScaleControl {
        override var ready=true
        override var startAllowed=true
        var tares=0
        override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit) {
            if(beforeDispatch()){tares++;done(OperationResult.Success())} else done(OperationResult.Failed("tare guard"))
        }
    }
    val scale=Scale()
    val controller=ExtractionController(CoffeeSessionControl(f.session),scale,{f.now})
    val blocker:Triple<Long,Long,GattOperation>
    val before:Int
    fun idle(at:Long) {
        f.now=at
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,f.hex("400023F02F1C770B00000000000000190321AF"))
    }
    init {
        idle(0)
        val queue=DeviceSession::class.java.getDeclaredField("queue").apply { isAccessible=true }.get(f.session) as GattQueue
        queue.enqueue(GattOperation.Discover,5000) { }
        blocker=f.calls.last();before=f.calls.size
        controller.weight(WeightReading(0,0))
        assertTrue(controller.start(profile,target,0))
        if(target>0) {
            assertEquals(1,scale.tares);assertTrue(controller.preparingScale)
            idle(100);controller.weight(WeightReading(0,100))
        } else assertEquals(0,scale.tares)
        assertFalse(controller.preparingScale);assertEquals(ExtractionState.STARTING,controller.state)
        assertEquals(before,f.calls.size)
    }
    fun release(allowed:Boolean) {
        f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
        if(allowed) {
            assertEquals(before+1,f.calls.size)
            val op=f.calls.last().third as GattOperation.Write
            assertEquals(KnownGatt.coffeeWrite,op.endpoint);assertTrue(op.withResponse)
            assertArrayEquals(CoffeeCommands.start(profile).frame.toByteArray(),op.bytes)
            f.complete();assertEquals(ExtractionState.RUNNING,controller.state)
        } else {
            assertEquals(before,f.calls.size);assertEquals(ExtractionState.IDLE,controller.state)
        }
        val calls=f.calls.size
        f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
        assertEquals(calls,f.calls.size)
        f.session.disconnect()
    }
}
class QueuedExtractionScaleGateTest {
    @Test fun scaleDisconnectAfterTareBeforeMachineDispatchCannotStart() {
        val q=QueuedExtractionFixture();q.scale.ready=false;q.controller.scaleDisconnected();q.release(false)
    }
    @Test fun restoredScaleWithoutNewWeightCannotStartOldQueuedRequest() {
        val q=QueuedExtractionFixture();q.scale.ready=false;q.controller.scaleDisconnected();q.scale.ready=true;q.release(false)
    }
    @Test fun staleWeightBeforeDispatchBlocksDespiteFreshCoffeeContext() {
        val q=QueuedExtractionFixture();q.idle(1601);q.release(false)
    }
    @Test fun unresolvedTareAfterQueuePreventsStart() {
        val q=QueuedExtractionFixture();q.scale.startAllowed=false;q.release(false)
    }
    @Test fun liveScaleAtInclusiveWeightFreshnessBoundaryKeepsOriginalBytes() {
        val q=QueuedExtractionFixture();q.idle(1600);q.release(true)
    }
    @Test fun newLiveWeightRenewsEligibilityWhileMachineRequestWaits() {
        val q=QueuedExtractionFixture();q.idle(1601);q.controller.weight(WeightReading(1200,1601));q.release(true)
    }
}
class QueuedFlowStartScaleGateTest {
    @Test fun flowStartKeepsOptionalScaleWhenDisconnected() {
        val q=QueuedExtractionFixture(0);q.scale.ready=false;q.controller.scaleDisconnected();q.release(true)
    }
    @Test fun flowStartDoesNotRequireFreshWeight() {
        val q=QueuedExtractionFixture(0);q.idle(1601);q.release(true)
    }
    @Test fun unresolvedTareStillBlocksFlowStartAsAtEntry() {
        val q=QueuedExtractionFixture(0);q.scale.startAllowed=false;q.release(false)
    }
}

@RunWith(Parameterized::class)
class QueuedStartUnexpectedActivityTest(private val target:Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="target={0}") fun targets()=listOf(arrayOf(0),arrayOf(3400))
    }
    @Test fun observedValveActivityBeforeDispatchCannotResumeAfterFreshIdle() {
        run {
            val q=QueuedExtractionFixture(target);q.f.now=200
            q.controller.machineFrame(ExtractionTelemetry(8,1,20,1,9200,20,64,0,ByteFrame(byteArrayOf())),200)
            q.idle(201)
            assertEquals(ExtractionState.STARTING,q.controller.state)
            assertNotNull(q.f.session.captureStartContext(q.profile)) // Isolate the caller: coffee protocol gates still permit.
            q.release(false)
        }
    }
    @Test fun closedValvePreheatTelemetryDoesNotCancelOriginalQueuedStart() {
        run {
            val q=QueuedExtractionFixture(target);q.f.now=200
            q.controller.machineFrame(ExtractionTelemetry(8,1,0,0,9200,0,32,0,ByteFrame(byteArrayOf())),200)
            q.idle(201);q.release(true)
        }
    }
}
