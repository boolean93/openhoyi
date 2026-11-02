package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class CustomPressureStartPermitTest {
    private val p=StartParameters(false,false,2,7,92,55,false,0,30,90,0,0,150,0,400,0,0)
    private fun fresh(f:CoffeeSessionFixture) {
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,f.hex("400023F02F1C770B00000000000000190321AF"))
    }
    @Test fun defaultSessionStillRejectsUnknownPressureFrame() {
        val f=CoffeeSessionFixture();fresh(f)
        assertNull(f.session.captureStartContext(p))
        var result:OperationResult?=null;val size=f.calls.size
        f.session.startExtraction(p){result=it}
        assertTrue(result is OperationResult.Failed);assertEquals(size,f.calls.size)
    }
    @Test fun explicitlyAuthorizedBoundedPressureRecipeDispatchesExactFrame() {
        val f=CoffeeSessionFixture {it==p};fresh(f)
        assertNotNull(f.session.captureStartContext(p))
        var result:OperationResult?=null;val size=f.calls.size
        f.session.startExtraction(p){result=it}
        assertEquals(size+1,f.calls.size)
        val write=f.calls.last().third as GattOperation.Write
        assertEquals(KnownGatt.coffeeWrite,write.endpoint)
        assertArrayEquals(CoffeeCommands.start(p).frame.toByteArray(),write.bytes)
        f.complete();assertTrue(result is OperationResult.Success)
    }
    private fun queued(exception:Boolean) {
        var allowed=true
        val f=CoffeeSessionFixture {if(!allowed && exception)error("permit unavailable") else allowed}
        fresh(f);val context=f.session.captureStartContext(p)
        assertNotNull(context)
        val queue=DeviceSession::class.java.getDeclaredField("queue").apply {isAccessible=true}.get(f.session) as GattQueue
        queue.enqueue(GattOperation.Discover,5000){}
        val barrier=f.calls.last();val size=f.calls.size
        var result:OperationResult?=null
        f.session.startExtraction(p,context!!){result=it}
        allowed=false
        f.session.onComplete(barrier.first,barrier.second,OperationResult.Success())
        assertEquals(size,f.calls.size);assertTrue(result is OperationResult.Failed)
        f.session.onComplete(barrier.first,barrier.second,OperationResult.Success());f.session.tick()
        assertEquals(size,f.calls.size)
    }
    @Test fun revokedRecipeAtActualQueueDispatchSendsNothing()=queued(false)
    @Test fun permitExceptionAtActualQueueDispatchFailsClosed()=queued(true)
    private fun queuedFault(kind:String) {
        val f=CoffeeSessionFixture {it==p};fresh(f)
        val context=requireNotNull(f.session.captureStartContext(p))
        val queue=DeviceSession::class.java.getDeclaredField("queue").apply {isAccessible=true}.get(f.session) as GattQueue
        queue.enqueue(GattOperation.Discover,5000){}
        val barrier=f.calls.last()
        var result:OperationResult?=null
        f.session.startExtraction(p,context){result=it}
        val idle=f.hex("400023F02F1C770B00000000000000190321AF")
        when(kind) {
            "alarm" -> f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,idle.apply {this[13]=1})
            "sleep" -> f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,idle.apply {this[8]=1})
            "settings" -> f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,f.hex("830113FD5D007D0F350019006E"))
            "stale" -> f.now=1501
            "reconnect" -> {
                f.session.disconnect()
                f.session.connect("replacement",CoffeeAuthentication(java.time.LocalDateTime.of(2026,10,11,0,0),"123456"))
            }
            else -> error("unknown fault")
        }
        f.session.onComplete(barrier.first,barrier.second,OperationResult.Success())
        assertNotNull(result);assertFalse(result is OperationResult.Success)
        fun starts()=f.calls.count {val write=it.third as? GattOperation.Write;write!=null && write.bytes.size==20 && write.bytes[0]==2.toByte()}
        assertEquals(0,starts())
        f.session.onComplete(barrier.first,barrier.second,OperationResult.Success());f.session.tick()
        assertEquals(0,starts())
    }
    @Test fun queuedPressureStartIsCancelledByAlarm()=queuedFault("alarm")
    @Test fun queuedPressureStartIsCancelledBySleep()=queuedFault("sleep")
    @Test fun queuedPressureStartIsCancelledByChangedSettings()=queuedFault("settings")
    @Test fun queuedPressureStartIsCancelledByStaleTelemetry()=queuedFault("stale")
    @Test fun queuedPressureStartCannotFollowAReconnection()=queuedFault("reconnect")
    @Test fun hostCannotAuthorizeUnvalidatedModesOrLossyWaterThroughPressurePermit() {
        val f=CoffeeSessionFixture {true};fresh(f)
        for(bad in listOf(p.copy(pressureLogic=true),p.copy(variableFlowLogic=true),p.copy(target1=121),
            p.copy(firstFlowTenths=151),p.copy(target4=1),p.copy(maximumWaterMl=54),
            p.copy(maximumWaterMl=801),p.copy(preinfusionSeconds=1),p.copy(firstDurationSeconds=1),
            p.copy(firstSegmentFlowMode=true)))assertNull(f.session.captureStartContext(bad))
    }
    @Test fun authorizedRecipeStillRequiresFreshIdleAndVerifiedFirmware() {
        val f=CoffeeSessionFixture {true};fresh(f)
        assertNotNull(f.session.captureStartContext(p))
        f.now=1501;assertNull(f.session.captureStartContext(p))
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,f.hex("830114FD5C007D0F350019006E"))
        fresh(f);assertNull(f.session.captureStartContext(p))
    }
}
