package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Real scale session/decoder/tare + controller. Fake BLE and coffee, not hardware acceptance. */
class ProtocolTargetWeightTest {
    private val adapters = listOf(FelicitaScaleProtocolAdapter, DiFluidScaleProtocolAdapter, DiFluidTiScaleProtocolAdapter)
    private class Coffee : CoffeeControl {
        override var ready = true
        var starts = 0; var stops = 0
        override fun prepareStart(parameters: StartParameters) = ready
        override fun startConditionsValid(parameters: StartParameters) = ready
        override fun start(parameters: StartParameters, beforeDispatch: () -> Boolean, done: (OperationResult) -> Unit) {
            if (beforeDispatch()) { starts++; done(OperationResult.Success()) } else done(OperationResult.Cancelled("guard"))
        }
        override fun stop(done: (OperationResult) -> Unit) { stops++; done(OperationResult.Success()) }
    }
    private class Fixture(val adapter: ScaleProtocolAdapter, val ordered: Boolean = false) {
        var now = 100L; var sampleSerial = 0L
        val calls = mutableListOf<Triple<Long,Long,GattOperation>>()
        val coffee = Coffee()
        val tare = StandaloneTare { now }
        val session: DeviceSession = DeviceSession(DeviceRole.BOOKOO, object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += Triple(generation,token,operation); return true
            }
            override fun close(generation: Long) = Unit
        }, { now }, stateChanged = {
            // Same ordering as NativeDeviceHub: revoke zero evidence and controller input on loss of READY.
            if (it != DeviceState.READY) { tare.disconnected(); controller.scaleDisconnected() }
        }, scaleAdapter = adapter, scaleObservation = {
            tare.sample(++sampleSerial, it.hundredthsGram)
            controller.weight(it)
        })
        val controller: ExtractionController = ExtractionController(coffee, ScaleSessionControl(session,tare,{sampleSerial}), {now}, {if(ordered) sampleSerial else null})
        val parameters = StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
        fun complete(result: OperationResult = OperationResult.Success()) {
            val (g,t,_) = calls.last(); session.onComplete(g,t,result)
        }
        fun boot() {
            session.connect("fixture"); complete()
            val n = requireNotNull(adapter.notifyEndpoint); val w = requireNotNull(adapter.writeEndpoint)
            val endpoints = if(n==w) listOf(CharacteristicInfo(n,true,false,true,false)) else
                listOf(CharacteristicInfo(w,true,false,false,false),CharacteristicInfo(n,false,false,true,false))
            complete(OperationResult.Success(endpoints)); complete()
            val connected = now
            for (command in adapter.initializationCommands) { now=connected+command.delayMs;session.tick();complete() }
        }
        fun notify(bytes: ByteArray) = session.onNotification(session.generation,requireNotNull(adapter.notifyEndpoint),bytes)
        fun reading(value: Int, unit: Int = 0, unitless: Boolean = false): ByteArray {
            if(adapter===FelicitaScaleProtocolAdapter)
                return byteArrayOf(1,2,43) + "%06d".format(value).toByteArray() +
                    (if(unit==0) " g" else "oz").toByteArray() + byteArrayOf(83,79,48,34,137.toByte(),13,10)
            val tenths = value/10
            val payload = ByteArray(if(unitless) 12 else 13).apply {
                for (i in 0..3) this[i]=(tenths ushr (24-i*8)).toByte()
                if(!unitless) this[12]=unit.toByte()
            }
            val body = byteArrayOf(0xDF.toByte(),0xDF.toByte(),3,0,payload.size.toByte())+payload
            return body+byteArrayOf((body.sumOf {it.toInt() and 255} and 255).toByte())
        }
        fun begin() {
            boot(); notify(reading(1800)); assertEquals(DeviceState.READY,session.state)
            assertTrue(controller.start(parameters,3400,0)); assertEquals(0,coffee.starts)
        }
        fun running() {
            begin(); complete(); now++; notify(reading(0))
            assertEquals(1,coffee.starts); assertEquals(ExtractionState.RUNNING,controller.state)
        }
    }
    @Test fun diagnosticDffdBoundaryRestartDoesNotInvalidateTareOrReady() {
        fun hex(s:String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        for(adapter in adapters.drop(1)) {
            val f=Fixture(adapter,ordered=true);f.begin();f.complete()
            f.now+=34;f.notify(hex("dfdf03020103c7dfdf03020101c5dfdf03000dff"))
            assertEquals(DeviceState.READY,f.session.state)
            assertEquals(StandaloneTare.State.WAITING_ZERO,f.tare.state)
            f.now+=222;f.notify(hex("dfdf03000d00000000000000000015c2e6008b"))
            assertEquals(DeviceState.READY,f.session.state)
            assertEquals(StandaloneTare.State.CONFIRMED,f.tare.state)
            assertEquals(1,f.coffee.starts)
        }
    }
    @Test fun boundaryRestartStillRejectsWrongUnitAndCannotReviveExpiredStart() {
        fun hex(s:String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        val fragment=hex("dfdf03020103c7dfdf03020101c5dfdf03000dff")
        for(adapter in adapters.drop(1)) {
            val wrong=Fixture(adapter,ordered=true);wrong.begin();wrong.complete()
            wrong.now++;wrong.notify(fragment);wrong.now++;wrong.notify(wrong.reading(0,unit=1))
            assertEquals(DeviceState.SYNCHRONIZING,wrong.session.state)
            assertEquals(StandaloneTare.State.UNKNOWN,wrong.tare.state)
            assertEquals(0,wrong.coffee.starts)
            val late=Fixture(adapter,ordered=true);late.begin();late.now+=4000;late.complete()
            late.notify(fragment);late.now+=1000
            late.notify(hex("dfdf03000d00000000000000000015c2e6008b"))
            assertEquals(StandaloneTare.State.CONFIRMED,late.tare.state)
            assertEquals(0,late.coffee.starts)
            assertEquals(ExtractionState.IDLE,late.controller.state)
        }
    }
    @Test fun postCallbackZeroInSameMillisecondUsesHostOrder() {
        for(adapter in adapters) {
            val f=Fixture(adapter,ordered=true);f.begin();f.complete()
            f.notify(f.reading(0)) // New callback, but clock has not ticked.
            assertEquals(StandaloneTare.State.CONFIRMED,f.tare.state)
            assertEquals(1,f.coffee.starts)
        }
    }
    @Test fun orderedRunningStillRejectsSameTimestampWeight() {
        val f=Fixture(FelicitaScaleProtocolAdapter,ordered=true);f.begin();f.complete();f.notify(f.reading(0))
        assertEquals(1,f.coffee.starts)
        f.now+=7001;f.notify(f.reading(1000))
        f.notify(f.reading(3500)) // New sequence, same timestamp: not a preflight relaxation.
        assertEquals(0,f.coffee.stops)
        f.now++;f.notify(f.reading(3500));assertEquals(1,f.coffee.stops)
    }
    @Test fun sequenceDoesNotReplayPreCallbackOrRepeatedOrOlderZero() {
        val f=Fixture(FelicitaScaleProtocolAdapter,ordered=true);f.begin()
        f.notify(f.reading(0));f.complete()
        f.controller.weight(WeightReading(0,f.now)) // Same serial: already received before successful write.
        assertEquals(0,f.coffee.starts)
        f.sampleSerial--
        f.controller.weight(WeightReading(0,f.now))
        assertEquals(0,f.coffee.starts)
        f.sampleSerial+=2
        f.controller.weight(WeightReading(0,f.now-1)) // New serial never permits an older timestamp.
        assertEquals(0,f.coffee.starts)
        f.now++;f.notify(f.reading(0));assertEquals(1,f.coffee.starts)
    }
    @Test fun lateZeroReleasesTareButNeverRevivesExpiredStart() {
        val f=Fixture(FelicitaScaleProtocolAdapter,ordered=true);f.begin()
        f.now+=4000;f.complete() // Standalone confirmation deadline is later than launch intent.
        f.now+=1000;f.controller.tick()
        assertEquals(ExtractionState.IDLE,f.controller.state)
        assertEquals(StandaloneTare.State.WAITING_ZERO,f.tare.state)
        f.notify(f.reading(0))
        assertEquals(StandaloneTare.State.CONFIRMED,f.tare.state)
        assertEquals(0,f.coffee.starts)
        assertEquals(StopReason.TARE_UNCONFIRMED,f.controller.stopReason)
    }
    @Test fun deadlineZeroWithoutTickNeverStartsEvenWithNewSequence() {
        val f=Fixture(FelicitaScaleProtocolAdapter,ordered=true);f.begin()
        f.now+=4000;f.complete();f.now+=1000
        f.notify(f.reading(0))
        assertEquals(0,f.coffee.starts)
        assertEquals(ExtractionState.IDLE,f.controller.state)
    }
    @Test fun unorderedCallerKeepsStrictTimestampEvidence() {
        val f=Fixture(FelicitaScaleProtocolAdapter);f.begin();f.complete();f.notify(f.reading(0))
        assertEquals(0,f.coffee.starts)
        f.now++;f.notify(f.reading(0));assertEquals(1,f.coffee.starts)
    }
    @Test fun supportedProtocolsTareConfirmNewZeroStartAndStopAtTarget() {
        for(adapter in adapters) {
            val f=Fixture(adapter); f.begin()
            assertFalse(adapter.transportVerified); assertFalse(adapter.capabilities.validatedWeightControl)
            val tareWrite=f.calls.last().third as GattOperation.Write
            assertArrayEquals(adapter.tareCommand!!.frame.toByteArray(),tareWrite.bytes)
            f.now++; f.notify(f.reading(0)); assertEquals(0,f.coffee.starts) // A zero before write completion is not proof.
            f.complete(); f.now++; f.notify(f.reading(0)); assertEquals(1,f.coffee.starts)
            val writes=f.calls.size
            f.now+=1500; f.controller.tick() // No extra automatic tare.
            assertEquals(writes,f.calls.size)
            assertEquals(StandaloneTare.State.CONFIRMED,f.tare.state)
            f.now+=5601; f.notify(f.reading(3500))
            assertEquals(1,f.coffee.stops); assertEquals(StopReason.TARGET_WEIGHT,f.controller.stopReason)
            f.controller.tick(); assertEquals(1,f.coffee.stops)
        }
    }
    @Test fun failedUnknownOrUnconfirmedTareNeverStartsOrRetries() {
        for(adapter in adapters) {
            for(result in listOf(OperationResult.Failed("rejected"),OperationResult.Unknown("unknown"))) {
                val f=Fixture(adapter);f.begin();val count=f.calls.size;f.complete(result)
                f.now++;f.notify(f.reading(0));f.now+=5000;f.controller.tick()
                assertEquals(0,f.coffee.starts);assertEquals(count,f.calls.size)
            }
            val f=Fixture(adapter);f.begin();f.complete();f.now++;f.notify(f.reading(1800))
            f.now+=5000;f.controller.tick();assertEquals(0,f.coffee.starts)
            assertEquals(StopReason.TARE_UNCONFIRMED,f.controller.stopReason)
        }
    }
    @Test fun invalidUnitOrBadFrameRevokesReadyAndProtectsBeforeAndDuringShot() {
        for(adapter in adapters) for(active in listOf(false,true)) {
            val f=Fixture(adapter)
            if(active) f.running() else {f.begin();f.complete()}
            f.now++;f.notify(f.reading(0,unit=1))
            assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
            assertEquals(if(active) 1 else 0,f.coffee.stops)
            assertEquals(if(active) ExtractionState.STOP_REQUESTED else ExtractionState.IDLE,f.controller.state)
            assertEquals(if(active) 1 else 0,f.coffee.starts)
        }
        for(adapter in adapters) {
            val f=Fixture(adapter);f.running();f.now++
            f.notify(f.reading(0).copyOf().apply {this[lastIndex]=0})
            assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertEquals(1,f.coffee.stops)
        }
    }
    @Test fun lossOrExpiredReadingsStopWithoutDuplicateWrites() {
        for(adapter in adapters) {
            val f=Fixture(adapter);f.running();f.session.disconnect()
            assertEquals(1,f.coffee.stops);f.controller.tick();assertEquals(1,f.coffee.stops)
            val stale=Fixture(adapter);stale.running();stale.now+=1501;stale.controller.tick()
            assertEquals(1,stale.coffee.stops)
        }
    }
    @Test fun difluidUnitlessWeightNeedsThisConnectionsGramConfirmation() {
        for(adapter in adapters.drop(1)) {
            val f=Fixture(adapter);f.boot();f.notify(f.reading(0,unitless=true))
            assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
            assertFalse(f.controller.start(f.parameters,3400,0));assertEquals(0,f.coffee.starts)
            f.notify(DiFluidCodec.setGrams().frame.toByteArray()+f.reading(1800,unitless=true))
            assertEquals(DeviceState.READY,f.session.state)
            assertTrue(f.controller.start(f.parameters,3400,0));f.complete();f.now++
            f.notify(f.reading(0,unitless=true));assertEquals(1,f.coffee.starts)
            f.now++;f.notify(f.reading(0,unit=1));assertEquals(1,f.coffee.stops)
        }
    }
}
