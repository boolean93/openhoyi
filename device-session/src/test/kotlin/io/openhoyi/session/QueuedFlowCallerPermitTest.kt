package io.openhoyi.session

import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.StartParameters
import org.junit.Assert.*
import org.junit.Test

class QueuedFlowCallerPermitTest {
    private val parameters=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
    private class NoScale:ScaleControl {
        override val ready=false
        override val startAllowed=true
        override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit)=error("flow preflight must not tare")
    }
    private fun queued(revoke:Boolean,throws:Boolean=false) {
        val f=CoffeeSessionFixture()
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,
            f.hex("400023F02F1C770B00000000000000190321AF"))
        val controller=ExtractionController(CoffeeSessionControl(f.session),NoScale(),{f.now})
        val queue=DeviceSession::class.java.getDeclaredField("queue").apply { isAccessible=true }.get(f.session) as GattQueue
        queue.enqueue(GattOperation.Discover,5000) { }
        val barrier=f.calls.last();val count=f.calls.size
        var allowed=true
        assertTrue(controller.start(parameters,0,0) { if(!allowed && throws)error("lost caller") else allowed })
        assertEquals(ExtractionState.STARTING,controller.state);assertEquals(count,f.calls.size)
        if(revoke)allowed=false
        f.session.onComplete(barrier.first,barrier.second,OperationResult.Success())
        if(revoke) {
            assertEquals(count,f.calls.size);assertEquals(ExtractionState.IDLE,controller.state)
        }else {
            assertEquals(count+1,f.calls.size)
            val write=f.calls.last().third as GattOperation.Write
            assertArrayEquals(CoffeeCommands.start(parameters).frame.toByteArray(),write.bytes)
            assertEquals(KnownGatt.coffeeWrite,write.endpoint);assertTrue(write.withResponse)
            f.complete();assertEquals(ExtractionState.RUNNING,controller.state)
        }
        val finalCount=f.calls.size
        f.session.onComplete(barrier.first,barrier.second,OperationResult.Success());f.session.tick();controller.tick()
        assertEquals(finalCount,f.calls.size);f.session.disconnect()
    }
    @Test fun flowWithoutScaleRetainsExactWireFrame()=queued(false)
    @Test fun flowCallerRevokedAtQueueDispatchSendsNothing()=queued(true)
    @Test fun flowCallerExceptionAtQueueDispatchFailsClosed()=queued(true,true)
    private fun initial(throws:Boolean) {
        val f=CoffeeSessionFixture();val count=f.calls.size
        val controller=ExtractionController(CoffeeSessionControl(f.session),NoScale(),{f.now})
        assertFalse(controller.start(parameters,0,0) { if(throws)error("caller unavailable") else false })
        assertEquals(ExtractionState.IDLE,controller.state);assertEquals(count,f.calls.size);f.session.disconnect()
    }
    @Test fun initiallyRevokedCallerDoesNotBeginRequest()=initial(false)
    @Test fun initialCallerExceptionDoesNotEscapeOrBeginRequest()=initial(true)
}
