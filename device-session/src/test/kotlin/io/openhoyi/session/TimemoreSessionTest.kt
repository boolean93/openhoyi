package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class TimemoreSessionTest {
    // Notification CRC=0000 is explicitly reserved by the vendor; outbound CRC has codec vectors.
    private fun notify(command:Int,vararg payload:Int)=byteArrayOf(0xA5.toByte(),0x5A,1,command.toByte(),0,payload.size.toByte())+
        payload.map(Int::toByte).toByteArray()+byteArrayOf(0,0)
    private fun unit(value:Int=0)=notify(6,value)
    private fun weight(tenths:Int=0)=notify(1,tenths ushr 24,tenths ushr 16,tenths ushr 8,tenths,0,0,0,1,0)
    private fun ack(success:Boolean,command:Int=0x0D):ByteArray {
        val body=byteArrayOf(0xA5.toByte(),0x5A,3,command.toByte(),0,1,if(success)1 else 0)
        var crc=0xFFFF
        for(byte in body)for(bit in 0..7) {
            val feedback=(crc xor (byte.toInt() ushr bit)) and 1
            crc=(crc ushr 1) xor if(feedback==1)0xA001 else 0
        }
        return body+byteArrayOf((crc ushr 8).toByte(),crc.toByte())
    }
    private class Driver:GattDriver {
        val operations=mutableListOf<Triple<Long,Long,GattOperation>>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {operations+=Triple(generation,token,operation);return true}
        override fun close(generation:Long) {}
    }
    private inner class Fixture(val adapter:ScaleProtocolAdapter=TimemoreDotScaleProtocolAdapter) {
        var now=0L
        val driver=Driver()
        val samples=mutableListOf<ScaleObservation>()
        var observed:((ScaleObservation)->Unit)?=null
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},scaleObservation={samples+=it;observed?.invoke(it)},scaleAdapter=adapter)
        val endpoint get()=adapter.notifyEndpoint!!
        fun complete(result:OperationResult=OperationResult.Success()) {val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)}
        fun start(wnr:Boolean=true,notify:Boolean=true) {
            session.connect("scale");complete()
            val characteristics=mutableListOf(CharacteristicInfo(endpoint,false,false,notify,!notify))
            adapter.writeEndpoint?.let {characteristics+=CharacteristicInfo(it,true,wnr,false,false)}
            complete(OperationResult.Success(characteristics))
            if(session.state==DeviceState.FAILED)return
            complete() // subscription
            for(command in adapter.initializationCommands) {now+=command.delayMs;session.tick();complete()}
        }
        fun receive(bytes:ByteArray)=session.onNotification(session.generation,endpoint,bytes)
        fun ready() {start();receive(unit()+weight());assertEquals(DeviceState.READY,session.state)}
        fun writes()=driver.operations.mapNotNull {it.third as? GattOperation.Write}
    }
    @Test fun bothDocumentedModelsRequireReadbackAndExactGattProperties() {
        for(adapter in listOf(TimemoreDotScaleProtocolAdapter,TimemoreBasicLinkScaleProtocolAdapter)) {
            val f=Fixture(adapter);f.start()
            assertEquals(listOf(TimemoreCodec.setGrams().frame,TimemoreCodec.readUnit().frame),f.writes().map {ByteFrame(it.bytes)})
            assertTrue(f.writes().all {!it.withResponse})
            f.receive(weight(123));assertTrue(f.samples.isEmpty())
            f.receive(ack(true,6)+weight());assertTrue(f.samples.isEmpty()) // Write ACK is not unit readback.
            f.receive(unit(1)+weight());assertTrue(f.samples.isEmpty()) // oz cannot authorize grams.
            f.receive(unit()+weight(-123));assertEquals(-1230,f.samples.single().hundredthsGram)
            assertEquals(ScaleEvidence.DOCUMENTED_TRANSPORT,f.samples.single().evidence)
            assertFalse(f.samples.single().eligibleForControl);assertFalse(adapter.capabilities.automaticTare)
            val unsupported=Fixture(adapter);unsupported.start(wnr=false);assertEquals(DeviceState.FAILED,unsupported.session.state)
            val indicateOnly=Fixture(adapter);indicateOnly.start(notify=false);assertEquals(DeviceState.FAILED,indicateOnly.session.state)
        }
    }
    @Test fun unitRevocationAndReconnectDiscardBufferedAuthorization() {
        val f=Fixture();f.ready();val old=f.session.generation
        f.receive(unit(1));assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        f.receive(weight());assertEquals(1,f.samples.size)
        f.receive(unit()+weight());assertEquals(DeviceState.READY,f.session.state)
        val partial=unit();f.receive(partial.copyOfRange(0,4));f.session.disconnect();f.start()
        f.session.onNotification(old,f.endpoint,unit()+weight())
        f.receive(partial.copyOfRange(4,partial.size)+weight());assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        assertEquals(2,f.samples.size)
        f.receive(unit()+weight());assertEquals(DeviceState.READY,f.session.state)
    }
    @Test fun malformedUnitRevokesGramsAndConflictingTareAckCannotConfirmZero() {
        val f=Fixture();f.ready()
        f.receive(weight()+unit(2))
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        f.receive(weight());assertEquals(1,f.samples.size)
        f.receive(unit()+weight());assertEquals(DeviceState.READY,f.session.state)
        val results=mutableListOf<OperationResult>()
        f.session.tare({true}) {results+=it};f.complete()
        f.receive(ack(true)+ack(false)+weight())
        assertTrue(results.single() is OperationResult.Failed)
    }
    @Test fun tareNeedsGattAndAckThenSubsequentZeroAndDoesNotReplayPreAckWeight() {
        val f=Fixture();f.ready()
        val tracker=StandaloneTare(clock={f.now});var serial=0L
        f.observed={tracker.sample(++serial,it.hundredthsGram)}
        val token=tracker.begin()!!
        f.session.tare({true}) {tracker.written(token,it,serial)}
        f.receive(weight()+ack(true)) // Early ACK; earlier weight cannot confirm it.
        assertEquals(StandaloneTare.State.WRITING,tracker.state)
        f.complete();assertEquals(StandaloneTare.State.WAITING_ZERO,tracker.state)
        f.receive(weight());assertEquals(StandaloneTare.State.CONFIRMED,tracker.state)
        assertEquals(3,f.writes().size) // Two init commands plus one explicit tare only.
    }
    @Test fun negativeAckTimeoutAndDisconnectNeverConfirmTare() {
        val f=Fixture();f.ready();val results=mutableListOf<OperationResult>()
        f.session.tare({true}) {results+=it};f.receive(ack(false)+weight());assertTrue(results.isEmpty())
        f.complete();assertTrue(results.single() is OperationResult.Failed)
        results.clear();f.session.tare({true}) {results+=it};f.complete();f.receive(weight())
        f.now+=5000;f.session.tick();assertTrue(results.single() is OperationResult.Unknown)
        results.clear();f.session.tare({true}) {results+=it};f.session.disconnect()
        assertTrue(results.single() is OperationResult.Unknown)
        assertEquals(5,f.writes().size)
    }
    @Test fun lateAckOrGattCallbackBeforeTickCannotClearUnknownTare() {
        for(gattCompleted in listOf(false,true)) {
            val f=Fixture();f.ready()
            val tracker=StandaloneTare(clock={f.now});var serial=0L
            f.observed={tracker.sample(++serial,it.hundredthsGram)}
            val token=tracker.begin()!!
            f.session.tare({true}) {tracker.written(token,it,serial)}
            if(gattCompleted)f.complete() else f.receive(ack(true))
            f.now+=5000 // No tick: the callback itself must enforce the acknowledgement deadline.
            if(gattCompleted)f.receive(ack(true)+weight()) else f.complete()
            assertEquals(StandaloneTare.State.UNKNOWN,tracker.state)
            f.receive(weight());assertEquals(StandaloneTare.State.UNKNOWN,tracker.state)
        }
    }
    @Test fun strictAllowlistAndLegacyReadOnlyRemainSeparate() {
        val adapter=TimemoreDotScaleProtocolAdapter
        fun permits(operation:GattOperation.Write)=OutboundWritePolicy.permits(DeviceRole.BOOKOO,operation,adapter)
        assertTrue(permits(GattOperation.Write(adapter.writeEndpoint!!,TimemoreCodec.tare().frame.toByteArray(),false)))
        assertFalse(permits(GattOperation.Write(adapter.writeEndpoint!!,TimemoreCodec.tare().frame.toByteArray(),true)))
        assertFalse(permits(GattOperation.Write(KnownGatt.bookooWrite,TimemoreCodec.tare().frame.toByteArray(),false)))
        val f=Fixture(TimemoreLegacyReadOnlyScaleProtocolAdapter);f.start()
        f.receive(byteArrayOf(0x10,20,0,0,0,30,0,0,0))
        assertEquals(DeviceState.READY,f.session.state);assertTrue(f.writes().isEmpty())
        assertEquals(300,f.samples.single().hundredthsGram);assertEquals(200,f.samples.single().secondaryHundredthsGram)
        var result:OperationResult?=null;f.session.tare({true}) {result=it}
        assertTrue(result is OperationResult.Failed);assertTrue(f.writes().isEmpty())
    }
}
