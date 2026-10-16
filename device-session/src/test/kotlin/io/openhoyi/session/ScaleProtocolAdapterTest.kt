package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Test
import org.junit.Assert.*

class ScaleProtocolAdapterTest {
    private fun hex(s:String)=s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun sample():ByteArray = ByteArray(20).apply {
        this[0]=3;this[1]=11;this[6]=43;this[7]=0;this[8]=7;this[9]=8;this[10]=45;this[12]=10
        this[19]=take(19).fold(0) { a,b -> a xor (b.toInt() and 255) }.toByte()
    }
    @Test fun bookooAdapterPreservesCapturedCommandsAndSigns() {
        val adapter=BookooScaleProtocolAdapter
        assertEquals(listOf("030A02000308","030A0300141E","030A0700000E","030A08010000"),adapter.initializationCommands.map { it.frame.hex() })
        assertEquals("030A01000008",adapter.tareCommand!!.frame.hex())
        val observation=(adapter.decode(sample(),123L) as DecodeResult.Valid).value
        assertEquals(1800,observation.hundredthsGram);assertEquals(-10,observation.deviceFlowHundredths)
        assertEquals(123L,observation.receivedAtMs);assertTrue(observation.eligibleForControl)
        assertNull(observation.batteryPercent);assertFalse(adapter.capabilities.battery)
    }
    @Test fun offlineCandidatesCannotExposeGattOrControl() {
        for (adapter in ScaleProtocolRegistry.offlineCandidates) {
            assertNull(adapter.writeEndpoint);assertNull(adapter.notifyEndpoint);assertNull(adapter.tareCommand)
            assertTrue(adapter.initializationCommands.isEmpty());assertFalse(adapter.capabilities.validatedWeightControl)
            assertFalse(adapter.permitsWrite(GattOperation.Write(KnownGatt.bookooWrite,BookooCodec.tare().frame.toByteArray(),true)))
            val bytes=when(adapter.id) {
                "felicita"->hex("0708")
                "acaia"->hex("EFDD0C0005080700000200")
                else->hex("DFDF030000000000B4")
            }
            val observation=(adapter.decode(bytes,10) as DecodeResult.Valid).value
            assertFalse(observation.eligibleForControl)
        }
        assertEquals(listOf(BookooScaleProtocolAdapter),ScaleProtocolRegistry.connectable)
    }
    @Test fun adapterDoesNotExpandWriteWhitelist() {
        val adapter=BookooScaleProtocolAdapter
        assertTrue(adapter.permitsWrite(GattOperation.Write(KnownGatt.bookooWrite,BookooCodec.tare().frame.toByteArray(),false)))
        assertFalse(adapter.permitsWrite(GattOperation.Write(KnownGatt.coffeeWrite,BookooCodec.tare().frame.toByteArray(),true)))
        assertFalse(adapter.permitsWrite(GattOperation.Write(KnownGatt.bookooWrite,hex("030A09000000"),true)))
        assertTrue(adapter.decode(sample().apply { this[19]=0 },0) is DecodeResult.Invalid)
        assertTrue(adapter.decode(hex("0102"),0) is DecodeResult.Unknown)
    }
    private class Driver:GattDriver {
        val operations=mutableListOf<Triple<Long,Long,GattOperation>>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {operations+=Triple(generation,token,operation);return true}
        override fun close(generation:Long) {}
    }
    @Test fun sessionEmitsGenericAndLegacySampleOnlyAfterFourInitFrames() {
        var now=0L;val driver=Driver();val observations=mutableListOf<ScaleObservation>();val legacy=mutableListOf<BookooSample>()
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},weightFrame={s,_->legacy+=s},scaleObservation={observations+=it})
        fun complete(result:OperationResult=OperationResult.Success()) {val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)}
        session.connect("bookoo");complete()
        complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.bookooWrite,true,false,false,false),CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false))))
        complete()
        session.onNotification(session.generation,KnownGatt.bookooNotify,sample());assertTrue(observations.isEmpty())
        repeat(4) {now+=500;session.tick();complete()}
        assertEquals(DeviceState.SYNCHRONIZING,session.state)
        session.onNotification(session.generation,KnownGatt.bookooNotify,sample())
        assertEquals(DeviceState.READY,session.state);assertEquals(1,legacy.size);assertEquals(1,observations.size)
        assertEquals(legacy.single().weightHundredthsGram,observations.single().hundredthsGram)
        val old=session.generation;session.disconnect();session.onNotification(old,KnownGatt.bookooNotify,sample());assertEquals(1,observations.size)
        session.connect("bookoo");assertEquals(DeviceState.CONNECTING,session.state)
    }
    private class Coffee:CoffeeControl {
        override val ready=true
        var starts=0
        override fun prepareStart(parameters:StartParameters)=true
        override fun startConditionsValid(parameters:StartParameters)=true
        override fun start(parameters:StartParameters,beforeDispatch:()->Boolean,done:(OperationResult)->Unit) {starts++;done(OperationResult.Success())}
        override fun stop(done:(OperationResult)->Unit) {done(OperationResult.Success())}
    }
    private class ReadOnlyScale:ScaleControl {
        override val ready=true
        override val startAllowed=true
        override val capabilities=ScaleCapabilities()
        var tares=0
        override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit) {tares++;done(OperationResult.Success())}
    }
    @Test fun readinessAloneCannotAuthorizeWeightTargetWithUnverifiedCapabilities() {
        val coffee=Coffee();val scale=ReadOnlyScale();val controller=ExtractionController(coffee,scale,{1000})
        controller.weight(WeightReading(0,1000))
        val parameters=StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0)
        assertFalse(controller.start(parameters,3400,0));assertEquals(0,scale.tares);assertEquals(0,coffee.starts)
    }
    @Test fun offlineObservationCannotPopulateExtractionPreflight() {
        val coffee=Coffee();val scale=object:ScaleControl {
            override val ready=true;override val startAllowed=true
            override fun tare(beforeDispatch:()->Boolean,done:(OperationResult)->Unit) {fail("offline must not tare")}
        }
        val controller=ExtractionController(coffee,scale,{1000})
        controller.weight((OfflineScaleProtocolAdapter(LegacyScaleFamily.FELICITA).decode(hex("0000"),1000) as DecodeResult.Valid).value)
        assertFalse(controller.start(StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0),3400,0))
    }
    @Test fun offlineAdapterCannotEvenOpenSession() {
        val driver=Driver()
        try {
            DeviceSession(DeviceRole.BOOKOO,driver,{0},scaleAdapter=OfflineScaleProtocolAdapter(LegacyScaleFamily.ACAIA))
            fail("offline candidate cannot connect")
        } catch(_:IllegalArgumentException) {assertTrue(driver.operations.isEmpty())}
    }

    @Test fun adapterOwnsInitTimingWithoutChangingExtractionBusiness() {
        var now=0L;val driver=Driver()
        val adapter=object:ScaleProtocolAdapter by BookooScaleProtocolAdapter {
            override val initializationCommands=BookooCodec.initializationCommands().map {TimedCommand(it.delayMs*2,it.frame)}
        }
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},scaleAdapter=adapter)
        fun complete(result:OperationResult=OperationResult.Success()) {val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)}
        session.connect("scale");complete()
        complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.bookooWrite,true,false,false,false),CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false))))
        complete();val before=driver.operations.size
        now=500;session.tick();assertEquals(before,driver.operations.size)
        now=1000;session.tick();assertEquals(before+1,driver.operations.size);complete()
        now=1500;session.tick();assertEquals(before+1,driver.operations.size)
        now=2000;session.tick();assertEquals(before+2,driver.operations.size)
    }

}
