package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Two real sessions/controllers/queues; only their GATT drivers are fake. */
class QueuedPreflightTareActivityTest {
    private class F {
        val coffee=CoffeeSessionFixture()
        val parameters=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
        val tare=StandaloneTare { coffee.now }
        var samples=0L
        var controller:ExtractionController?=null
        val scale=DeviceSession(DeviceRole.BOOKOO,object:GattDriver {
            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                calls+=Triple(generation,token,operation);return true
            }
            override fun close(generation:Long)=Unit
        },{coffee.now},weightFrame={sample,at->
            tare.sample(++samples,sample.weightHundredthsGram)
            controller?.weight(WeightReading(sample.weightHundredthsGram,at))
        })
        val scaleControl=ScaleSessionControl(scale,tare,{samples})
        val beforeCoffee:Int
        val beforeScale:Int
        val blocker:Triple<Long,Long,GattOperation>
        val originalContext:CoffeeStartContext
        init {
            scale.connect("bookoo")
            completeScale()
            completeScale(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.bookooWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false))))
            completeScale()
            repeat(4){coffee.now+=501;scale.tick();completeScale()}
            notifyWeight(false)
            assertEquals(DeviceState.READY,scale.state)
            idle(coffee.now)
            originalContext=requireNotNull(coffee.session.captureStartContext(parameters))
            controller=ExtractionController(CoffeeSessionControl(coffee.session),scaleControl,{coffee.now})
            notifyWeight(false)
            val queue=DeviceSession::class.java.getDeclaredField("queue").apply { isAccessible=true }.get(scale) as GattQueue
            queue.enqueue(GattOperation.Discover,5000) { }
            blocker=calls.last();beforeScale=calls.size;beforeCoffee=coffee.calls.size
            assertTrue(controller!!.start(parameters,3400,0))
            assertEquals(StandaloneTare.State.WRITING,tare.state)
            assertEquals(beforeScale,calls.size);assertEquals(beforeCoffee,coffee.calls.size)
        }
        fun completeScale(result:OperationResult=OperationResult.Success()) {
            val c=calls.last();scale.onComplete(c.first,c.second,result)
        }
        fun idle(at:Long) {
            coffee.now=at
            val bytes=coffee.hex("400023F02F1C770B00000000000000190321AF")
            coffee.session.onNotification(coffee.session.generation,KnownGatt.coffeeNotify,bytes)
            controller?.machineFrame((HoyiCodec.decode(bytes) as DecodeResult.Valid).value,at)
        }
        fun activity(valve:Boolean) {
            coffee.now+=100
            // Exact decoded notifications go through both the coffee Session and Controller.
            val bytes=coffee.hex(if(valve) "800808000100000D23EB24515C" else "800808000100000D23EB24215C")
            val frame=(HoyiCodec.decode(bytes) as DecodeResult.Valid).value as ExtractionTelemetry
            assertEquals(valve,frame.valveOpen)
            coffee.session.onNotification(coffee.session.generation,KnownGatt.coffeeNotify,bytes)
            controller!!.machineFrame(frame,coffee.now)
            idle(coffee.now+1)
            assertTrue(coffee.session.startConditionsValid(parameters,originalContext))
        }
        fun notifyWeight(zero:Boolean) {
            val bytes=coffee.hex(if(zero) "030B000000012B0000002D00024600C803010081" else "030B000000012D007A3A2D03424600C803010084")
            scale.onNotification(scale.generation,KnownGatt.bookooNotify,bytes)
        }
        fun release()=scale.onComplete(blocker.first,blocker.second,OperationResult.Success())
        fun assertTare() {
            assertEquals(beforeScale+1,calls.size)
            val op=calls.last().third as GattOperation.Write
            assertEquals(KnownGatt.bookooWrite,op.endpoint);assertTrue(op.withResponse)
            assertArrayEquals(BookooCodec.tare().frame.toByteArray(),op.bytes)
        }
        fun assertNoRetry() {
            val count=calls.size;val coffeeCount=coffee.calls.size
            release();coffee.now++;notifyWeight(true);controller!!.tick();scale.tick()
            assertEquals(count,calls.size);assertEquals(coffeeCount,coffee.calls.size)
            scale.disconnect();coffee.session.disconnect()
        }
    }
    @Test fun valveActivityWhileTareQueuedPreventsZeroingEvenAfterFreshIdle() {
        val f=F();f.activity(true);f.release()
        assertEquals(f.beforeScale,f.calls.size);assertEquals(f.beforeCoffee,f.coffee.calls.size)
        assertEquals(StandaloneTare.State.FAILED,f.tare.state)
        assertEquals(ExtractionState.IDLE,f.controller!!.state)
        assertTrue(f.scaleControl.startAllowed);f.assertNoRetry()
    }
    private fun allowed(closedValve:Boolean) {
        val f=F();if(closedValve)f.activity(false)
        f.release();f.assertTare();assertEquals(f.beforeCoffee,f.coffee.calls.size)
        f.completeScale();assertEquals(StandaloneTare.State.WAITING_ZERO,f.tare.state)
        assertEquals(ExtractionState.STARTING,f.controller!!.state)
        f.coffee.now++;f.notifyWeight(true)
        assertEquals(StandaloneTare.State.CONFIRMED,f.tare.state)
        assertEquals(f.beforeCoffee+1,f.coffee.calls.size)
        val start=f.coffee.calls.last().third as GattOperation.Write
        assertEquals(KnownGatt.coffeeWrite,start.endpoint);assertTrue(start.withResponse)
        assertArrayEquals(CoffeeCommands.start(f.parameters).frame.toByteArray(),start.bytes)
        f.coffee.complete();assertEquals(ExtractionState.RUNNING,f.controller!!.state)
        f.assertNoRetry()
    }
    @Test fun cleanQueuedTareKeepsBytesAndRequiresNewDecodedZero()=allowed(false)
    @Test fun closedValvePreheatDoesNotBlockQueuedTare()=allowed(true)
    @Test fun activityAfterTareWasSentCannotRetroactivelyProveUnsentOrStart() {
        val f=F();f.release();f.assertTare()
        f.activity(true);f.completeScale()
        assertEquals(StandaloneTare.State.WAITING_ZERO,f.tare.state)
        f.coffee.now++;f.notifyWeight(true)
        assertEquals(StandaloneTare.State.CONFIRMED,f.tare.state)
        assertEquals(f.beforeCoffee,f.coffee.calls.size)
        assertEquals(ExtractionState.IDLE,f.controller!!.state)
        f.assertNoRetry()
    }
}
