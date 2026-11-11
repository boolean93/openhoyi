package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic transport regression, not hardware acceptance. */
class DiFluidCompatibilityTest {
    private class Driver : GattDriver {
        val operations = mutableListOf<Triple<Long, Long, GattOperation>>()
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            operations += Triple(generation, token, operation)
            return true
        }
        override fun close(generation: Long) = Unit
    }
    private class Fixture(val adapter: ScaleProtocolAdapter) {
        var now = 100L
        val driver = Driver()
        val diagnostics = mutableListOf<String>()
        val observations = mutableListOf<ScaleObservation>()
        val session = DeviceSession(DeviceRole.BOOKOO, driver, { now },
            diagnostic = { diagnostics += it }, scaleAdapter = adapter,
            scaleObservation = { observations += it })
        fun complete(result: OperationResult = OperationResult.Success()) {
            val (g,t,_) = driver.operations.last()
            session.onComplete(g,t,result)
        }
        fun discover(characteristics: List<CharacteristicInfo>) {
            session.connect("synthetic-difluid")
            complete()
            complete(OperationResult.Success(characteristics))
        }
    }
    @Test fun sharedAndContraryServiceReachInitializationWithoutFallback() {
        val f=Fixture(DiFluidScaleProtocolAdapter)
        val endpoint=DiFluidTiScaleProtocolAdapter.notifyEndpoint
        f.discover(listOf(CharacteristicInfo(endpoint,true,false,true,false)))
        assertEquals(DeviceState.SUBSCRIBING,f.session.state)
        f.complete()
        f.now+=500;f.session.tick()
        assertEquals(endpoint,(f.driver.operations.last().third as GattOperation.Write).endpoint)
        val count=f.driver.operations.size
        f.complete(OperationResult.Failed("rejected"))
        assertEquals(DeviceState.FAILED,f.session.state)
        f.now+=5000;f.session.tick()
        assertEquals(count,f.driver.operations.size)
    }
    @Test fun sharedContraryServiceRoutesWeightAndExplicitTare() {
        val adapter=DiFluidScaleProtocolAdapter
        val endpoint=DiFluidTiScaleProtocolAdapter.notifyEndpoint
        val f=Fixture(adapter)
        f.discover(listOf(CharacteristicInfo(endpoint,true,false,true,false)))
        f.complete()
        val start=f.now
        for(command in adapter.initializationCommands) {
            f.now=start+command.delayMs;f.session.tick();f.complete()
        }
        val writes=f.driver.operations.mapNotNull {it.third as? GattOperation.Write}
        assertEquals(adapter.initializationCommands.size,writes.size)
        assertTrue(writes.all {it.endpoint==endpoint})
        assertTrue(writes.none {it.bytes.contentEquals(adapter.tareCommand.frame.toByteArray())})
        val sample="DFDF03000D000002F800000000000A27B000A9".chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        f.session.onNotification(f.session.generation,adapter.notifyEndpoint,sample)
        assertTrue(f.observations.isEmpty())
        f.session.onNotification(f.session.generation,endpoint,sample)
        assertEquals(DeviceState.READY,f.session.state)
        var result:OperationResult?=null
        f.session.tare({true}) {result=it}
        val tare=f.driver.operations.last().third as GattOperation.Write
        assertEquals(endpoint,tare.endpoint)
        assertArrayEquals(adapter.tareCommand.frame.toByteArray(),tare.bytes)
        f.complete()
        assertTrue(result is OperationResult.Success)
    }
    @Test fun reconnectResolvesFreshLayoutAndIgnoresOldGeneration() {
        val f=Fixture(DiFluidScaleProtocolAdapter)
        f.discover(listOf(CharacteristicInfo(DiFluidTiScaleProtocolAdapter.notifyEndpoint,true,false,true,false)))
        val old=f.driver.operations.last()
        f.session.disconnect()
        f.discover(listOf(CharacteristicInfo(DiFluidScaleProtocolAdapter.notifyEndpoint,false,false,true,false),
            CharacteristicInfo(DiFluidScaleProtocolAdapter.writeEndpoint,true,false,false,false)))
        f.session.onComplete(old.first,old.second,OperationResult.Success())
        f.session.onNotification(old.first,DiFluidTiScaleProtocolAdapter.notifyEndpoint,byteArrayOf())
        assertEquals(DeviceState.SUBSCRIBING,f.session.state)
        f.complete();f.now+=500;f.session.tick()
        assertEquals(DiFluidScaleProtocolAdapter.writeEndpoint,(f.driver.operations.last().third as GattOperation.Write).endpoint)
        val count=f.driver.operations.size
        f.complete(OperationResult.Unknown("timeout"))
        f.now+=5000;f.session.tick()
        assertEquals(DeviceState.FAILED,f.session.state)
        assertEquals(count,f.driver.operations.size)
    }
    @Test fun twoValidServicesAreAmbiguous() {
        val f=Fixture(DiFluidScaleProtocolAdapter)
        f.discover(listOf(DiFluidScaleProtocolAdapter,DiFluidTiScaleProtocolAdapter).map {
            CharacteristicInfo(it.notifyEndpoint,true,false,true,false)
        })
        assertEquals(DeviceState.FAILED,f.session.state)
        assertTrue(f.driver.operations.none {it.third is GattOperation.Subscribe || it.third is GattOperation.Write})
    }
    @Test fun duplicateIdentityRejectedBeforeProperties() {
        val f=Fixture(DiFluidScaleProtocolAdapter)
        val endpoint=DiFluidScaleProtocolAdapter.notifyEndpoint
        f.discover(listOf(CharacteristicInfo(endpoint,true,false,true,false),
            CharacteristicInfo(endpoint,false,false,false,false)))
        assertEquals(DeviceState.FAILED,f.session.state)
    }
    @Test fun splitProfileStillReachesReadyWithOfficialGramFrame() {
        for (adapter in listOf(DiFluidScaleProtocolAdapter, DiFluidTiScaleProtocolAdapter)) {
            val f = Fixture(adapter)
            f.discover(listOf(
                CharacteristicInfo(adapter.writeEndpoint, true, false, false, false),
                CharacteristicInfo(adapter.notifyEndpoint, false, false, true, false)))
            assertEquals(DeviceState.SUBSCRIBING, f.session.state)
            f.complete()
            val start = f.now
            for (cmd in adapter.initializationCommands) {
                f.now = start + cmd.delayMs; f.session.tick(); f.complete()
            }
            assertEquals(DeviceState.SYNCHRONIZING, f.session.state)
            val sample = "DFDF03000D000002F800000000000A27B000A9".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            f.session.onNotification(f.session.generation, adapter.notifyEndpoint, sample)
            assertEquals(DeviceState.READY, f.session.state)
            assertEquals(7600, f.observations.single().hundredthsGram)
            assertFalse(f.observations.single().eligibleForControl)
            println("Confirmed ${adapter.id}: expected AA02/AA01 profile + official frame -> READY, 76g, no weight-control eligibility")
        }
    }
    @Test fun unitlessSensorFrameIsRawRatherThanGuessed() {
        val sample = "DFDF03000C000002F800000000000A27B0A8".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result = DiFluidCodec.decode(sample)
        assertTrue(result is DecodeResult.Valid && result.value is DiFluidUnitlessWeight)
    }
    @Test fun unitlessSessionRevokesRecoversAndResetsOnReconnect() {
        val adapter=DiFluidScaleProtocolAdapter
        val f=Fixture(adapter)
        val endpoint=adapter.notifyEndpoint
        fun initialize() {
            f.discover(listOf(CharacteristicInfo(endpoint,true,false,true,false)))
            f.complete()
            val start=f.now
            for(command in adapter.initializationCommands) {
                f.now=start+command.delayMs;f.session.tick();f.complete()
            }
        }
        fun bytes(hex:String)=hex.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        val raw=bytes("DFDF03000C000002F800000000000A27B0A8")
        val unit=DiFluidCodec.setGrams().frame.toByteArray()
        val full=bytes("DFDF03000D000002F800000000000A27B000A9")
        fun notify(b:ByteArray)=f.session.onNotification(f.session.generation,endpoint,b)
        initialize();notify(full)
        assertEquals(DeviceState.READY,f.session.state)
        notify(full.copyOf().apply {this[lastIndex]=0})
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        notify(unit+raw)
        assertEquals(DeviceState.READY,f.session.state)
        val nonGram=full.copyOf().apply {
            this[17]=1
            this[lastIndex]=(dropLast(1).sumOf {it.toInt() and 255} and 255).toByte()
        }
        notify(nonGram)
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        notify(unit+raw)
        assertEquals(DeviceState.READY,f.session.state)
        f.session.disconnect();initialize();notify(raw)
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        notify(unit+raw)
        assertEquals(DeviceState.READY,f.session.state)
    }
    @Test fun unitlessRequiresConnectionUnitAndOrderedRevocation() {
        fun frame(function:Int,command:Int,payload:ByteArray):ByteArray {
            val body=byteArrayOf(0xDF.toByte(),0xDF.toByte(),function.toByte(),command.toByte(),payload.size.toByte())+payload
            return body+byteArrayOf((body.sumOf {it.toInt() and 255} and 255).toByte())
        }
        val raw=frame(3,0,ByteArray(12).apply {this[3]=10})
        val negative=frame(3,0,ByteArray(12).apply {for(i in 0..3)this[i]=0xFF.toByte()})
        val unit=frame(1,4,byteArrayOf(0))
        val ounces=frame(1,4,byteArrayOf(1))
        val full=frame(3,0,ByteArray(13).apply {this[3]=10})
        val adapter=DiFluidScaleProtocolAdapter
        val events=mutableListOf<ScaleProtocolEvent>()
        val fresh=adapter.newDecoder {events+=it}
        assertFalse(fresh(full+raw,0) is DecodeResult.Valid)
        assertTrue(events.contains(ScaleProtocolEvent.ReadingInvalidated))
        val decode=adapter.newDecoder {}
        assertFalse(decode(raw,1) is DecodeResult.Valid)
        assertTrue(decode(unit+raw,2) is DecodeResult.Valid)
        val neg=decode(negative,3) as DecodeResult.Valid
        assertEquals(-10,neg.value.hundredthsGram)
        assertFalse(decode(raw+ounces,4) is DecodeResult.Valid)
        assertFalse(decode(unit+raw+raw.copyOf().apply {this[lastIndex]=0},5) is DecodeResult.Valid)
        assertFalse(decode(raw,6) is DecodeResult.Valid)
        assertTrue(decode(full,7) is DecodeResult.Valid)
        assertFalse(decode(raw,8) is DecodeResult.Valid)
        assertTrue(decode(unit+raw,9) is DecodeResult.Valid)
        assertFalse(adapter.newDecoder {}(raw,10) is DecodeResult.Valid)
        assertFalse(decode(frame(3,2,byteArrayOf(1)),11) is DecodeResult.Valid)
        assertFalse(decode(frame(3,0,ByteArray(11)),12) is DecodeResult.Valid)
        assertFalse(decode(raw,13) is DecodeResult.Valid)
    }

}
