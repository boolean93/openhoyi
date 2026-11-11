package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class LegacyScaleSessionTest {
    private fun hex(s:String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    private val unit=hex("EFDD08035A02055A")
    private val weight=hex("EFDD0C08053930000002004335")
    private fun felicita(unit:String=" g")=byteArrayOf(1,2,43)+"001800".toByteArray()+unit.toByteArray()+byteArrayOf(83,79,48,34,137.toByte(),13,10)
    private class Driver:GattDriver {
        val operations=mutableListOf<Triple<Long,Long,GattOperation>>()
        val closed=mutableListOf<Long>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
            operations+=Triple(generation,token,operation);return true
        }
        override fun close(generation:Long) {closed+=generation}
    }
    private class Fixture(val adapter:ScaleProtocolAdapter,onState:(DeviceState)->Unit={}) {
        var now=100L
        val driver=Driver()
        val seen=mutableListOf<ScaleObservation>()
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},stateChanged=onState,scaleAdapter=adapter,scaleObservation={seen+=it})
        fun complete(result:OperationResult=OperationResult.Success()) {
            val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)
        }
        fun characteristics():List<CharacteristicInfo> {
            val notify=requireNotNull(adapter.notifyEndpoint)
            val write=adapter.writeEndpoint
            return if(write==notify)listOf(CharacteristicInfo(notify,true,false,true,false))
                else listOfNotNull(write?.let {CharacteristicInfo(it,true,false,false,false)},
                    CharacteristicInfo(notify,false,false,true,false))
        }
        fun subscribe() {
            session.connect("AA:BB:CC:DD:EE:FF");complete()
            complete(OperationResult.Success(characteristics()));complete()
        }
        fun boot() {
            subscribe();val start=now
            for(command in adapter.initializationCommands) {now=start+command.delayMs;session.tick();complete()}
            assertEquals(DeviceState.SYNCHRONIZING,session.state)
        }
        fun notify(bytes:ByteArray) {session.onNotification(session.generation,requireNotNull(adapter.notifyEndpoint),bytes)}
        fun writes()=driver.operations.mapNotNull {it.third as? GattOperation.Write}
    }
    @Test fun explicitProfileSelectionsArePersistentAndDoNotUpgradeOldReadOnlyBinding() {
        val address="AA:BB:CC:DD:EE:FF"
        for(adapter in ScaleProtocolRegistry.connectable+ScaleProtocolRegistry.readOnlyCandidates) {
            val selection=ScaleSelectionPolicy.remembered(address,adapter.id)!!
            assertSame(adapter,ScaleSelectionPolicy.adapter(selection.protocolId))
        }
        assertSame(FelicitaReadOnlyScaleProtocolAdapter,ScaleSelectionPolicy.adapter("felicita-ascii-read-only"))
        assertFalse(FelicitaReadOnlyScaleProtocolAdapter.capabilities.tare)
        assertNotEquals(FelicitaReadOnlyScaleProtocolAdapter.id,FelicitaScaleProtocolAdapter.id)
        assertNull(ScaleSelectionPolicy.adapter("acaia"));assertNull(ScaleSelectionPolicy.adapter("difluid"))
    }
    @Test fun felicitaHasNoConnectionWritesAndRequiresGramReadingBeforeExplicitTare() {
        val f=Fixture(FelicitaScaleProtocolAdapter);f.boot()
        assertTrue(f.writes().isEmpty());f.notify(felicita("oz"))
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertTrue(f.seen.isEmpty())
        f.notify(felicita());assertEquals(DeviceState.READY,f.session.state)
        assertEquals(1800,f.seen.last().hundredthsGram)
        var result:OperationResult?=null
        f.session.tare({true}) {result=it}
        assertEquals("54",ByteFrame(f.writes().single().bytes).hex())
        assertNull(result);f.complete();assertTrue(result is OperationResult.Success)
        assertFalse(f.seen.last().eligibleForControl);assertFalse(f.seen.last().capabilities.automaticTare)
    }
    @Test fun felicitaEmptyInitializationStillRequiresUniqueWritableEndpoint() {
        val f=Fixture(FelicitaScaleProtocolAdapter)
        f.session.connect("fixture");f.complete()
        val endpoint=requireNotNull(f.adapter.notifyEndpoint)
        f.complete(OperationResult.Success(listOf(CharacteristicInfo(endpoint,false,false,true,false))))
        assertEquals(DeviceState.FAILED,f.session.state);assertTrue(f.writes().isEmpty())
    }
    @Test fun acaiaProfilesUseDifferentEndpointsInitializationAndOnlyConfirmedGrams() {
        for(adapter in listOf(AcaiaLegacyScaleProtocolAdapter,AcaiaPearlScaleProtocolAdapter)) {
            val f=Fixture(adapter);f.boot()
            assertEquals(adapter.initializationCommands.map {it.frame.hex()},f.writes().map {ByteFrame(it.bytes).hex()})
            assertTrue(f.writes().all {it.endpoint==adapter.writeEndpoint})
            f.notify(weight);assertTrue(f.seen.isEmpty());assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
            f.notify(unit);assertTrue(f.seen.isEmpty())
            f.notify(weight);assertEquals(DeviceState.READY,f.session.state)
            assertEquals(12345,f.seen.last().hundredthsGram)
            assertEquals(ScaleEvidence.DOCUMENTED_TRANSPORT,f.seen.last().evidence)
            assertFalse(f.seen.last().eligibleForControl)
            assertFalse(f.seen.last().capabilities.automaticTare)
        }
        assertEquals(KnownGatt.coffeeWrite.characteristic,AcaiaPearlScaleProtocolAdapter.writeEndpoint.characteristic)
        assertNotEquals(KnownGatt.coffeeWrite.service,AcaiaPearlScaleProtocolAdapter.writeEndpoint.service)
    }
    @Test fun earlyUnitPacketsCannotSeedSubscriptionAndReconnectClearsUnitState() {
        val f=Fixture(AcaiaLegacyScaleProtocolAdapter)
        f.session.connect("fixture")
        f.notify(unit);f.complete();f.complete(OperationResult.Success(f.characteristics()))
        f.notify(unit);f.complete()
        val start=f.now
        for(command in f.adapter.initializationCommands) {f.now=start+command.delayMs;f.session.tick();f.complete()}
        f.notify(weight);assertTrue(f.seen.isEmpty())
        f.notify(unit+weight);assertEquals(1,f.seen.size)
        f.session.disconnect();f.boot();f.notify(weight);assertEquals(1,f.seen.size)
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
    }
    @Test fun acaiaHeartbeatIsSingleFlightAndMissedIntervalsAreNotReplayed() {
        val f=Fixture(AcaiaLegacyScaleProtocolAdapter);f.boot();f.notify(unit+weight)
        val initCount=f.writes().size
        f.now+=4499;f.session.tick();assertEquals(initCount,f.writes().size)
        f.now+=60_000;f.session.tick();assertEquals(initCount+1,f.writes().size)
        repeat(3) {f.session.tick()};assertEquals(initCount+1,f.writes().size)
        assertEquals(AcaiaCodec.heartbeat().frame.hex(),ByteFrame(f.writes().last().bytes).hex())
        f.complete();f.session.tick();assertEquals(initCount+1,f.writes().size)
        f.now+=4500;f.session.tick();assertEquals(initCount+2,f.writes().size)
    }
    @Test fun synchronousReadyObserverCannotSuppressHeartbeatScheduling() {
        lateinit var f:Fixture
        f=Fixture(AcaiaLegacyScaleProtocolAdapter) {state->if(state==DeviceState.SYNCHRONIZING)f.notify(unit+weight)}
        f.subscribe();val start=f.now
        for(command in f.adapter.initializationCommands) {f.now=start+command.delayMs;f.session.tick();f.complete()}
        assertEquals(DeviceState.READY,f.session.state)
        val count=f.writes().size
        f.now+=4500;f.session.tick();assertEquals(count+1,f.writes().size)
        assertEquals(AcaiaCodec.heartbeat().frame.hex(),ByteFrame(f.writes().last().bytes).hex())
    }
    @Test fun failedOrUnknownHeartbeatStopsConnectionWithoutRetry() {
        for(failure in listOf(OperationResult.Failed("rejected"),OperationResult.Unknown("unknown"))) {
            val f=Fixture(AcaiaPearlScaleProtocolAdapter);f.boot();f.notify(unit+weight)
            f.now+=4500;f.session.tick();val count=f.writes().size
            f.complete(failure);assertEquals(DeviceState.FAILED,f.session.state)
            f.now+=60_000;f.session.tick();assertEquals(count,f.writes().size)
        }
    }
    @Test fun timeoutAndOldHeartbeatCompletionCannotRearmOrClearReplacementOwner() {
        val f=Fixture(AcaiaLegacyScaleProtocolAdapter);f.boot();f.notify(unit+weight)
        f.now+=4500;f.session.tick();val old=f.driver.operations.last()
        f.now+=5000;f.session.tick();assertEquals(DeviceState.FAILED,f.session.state)
        f.boot();f.notify(unit+weight);val initCount=f.writes().size
        f.session.onComplete(old.first,old.second,OperationResult.Success())
        f.now+=4500;f.session.tick();assertEquals(initCount+1,f.writes().size)
        f.session.onComplete(old.first,old.second,OperationResult.Success())
        f.session.tick();assertEquals(initCount+1,f.writes().size)
        f.session.disconnect();f.now+=10_000;f.session.tick();assertEquals(initCount+1,f.writes().size)
    }
    @Test fun malformedUnitOrWeightRevokesTrustAndDoesNotRefreshReading() {
        val f=Fixture(AcaiaLegacyScaleProtocolAdapter);f.boot();f.notify(unit+weight)
        f.notify(weight.copyOf().apply {this[lastIndex]=0})
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertEquals(1,f.seen.size)
        f.notify(weight);assertEquals(1,f.seen.size)
        f.notify(unit+weight);assertEquals(2,f.seen.size)
        f.notify(hex("EFDD08035A05055D"));f.notify(weight)
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertEquals(2,f.seen.size)
    }
    @Test fun difluidUsesPerFrameGramUnitAndDiscoveredServiceWithoutTaringOnConnect() {
        val sample=hex("DFDF03000D000002F800000000000A27B000A9") // Official 76 g sample.
        for(adapter in listOf(DiFluidScaleProtocolAdapter,DiFluidTiScaleProtocolAdapter)) {
            val f=Fixture(adapter);f.boot()
            assertEquals(listOf("DFDF01000101C1","DFDF01040100C4","DFDF010400C3"),f.writes().map {ByteFrame(it.bytes).hex()})
            assertTrue(f.writes().all {it.endpoint==adapter.writeEndpoint})
            f.notify(hex("DFDF01040100C4"));assertTrue(f.seen.isEmpty()) // A unit ACK is not a weight.
            f.notify(sample.copyOfRange(0,10));assertTrue(f.seen.isEmpty())
            f.notify(sample.copyOfRange(10,sample.size));assertEquals(DeviceState.READY,f.session.state)
            assertEquals(7600,f.seen.last().hundredthsGram);assertFalse(f.seen.last().eligibleForControl)
            val ounce=sample.copyOf().apply {this[17]=1;this[lastIndex]=0xAA.toByte()}
            f.notify(ounce);assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertEquals(1,f.seen.size)
            f.notify(sample);assertEquals(DeviceState.READY,f.session.state)
            var result:OperationResult?=null;f.session.tare({true}) {result=it}
            assertEquals("DFDF03020101C5",ByteFrame(f.writes().last().bytes).hex())
            assertNull(result);f.complete();assertTrue(result is OperationResult.Success)
        }
        val f=Fixture(DiFluidTiScaleProtocolAdapter);f.session.connect("fixture");f.complete()
        f.complete(OperationResult.Success(Fixture(DiFluidScaleProtocolAdapter).characteristics()))
        assertEquals(DeviceState.SUBSCRIBING,f.session.state);assertTrue(f.writes().isEmpty())
    }
    @Test fun ultraRetainsReadingsButNotBookooVerifiedControlEvidenceOrLegacyCallback() {
        val sample=ByteArray(20).apply {
            this[0]=3;this[1]=11;this[6]=43;this[8]=7;this[9]=8;this[10]=43
            this[19]=take(19).fold(0) {a,b->a xor (b.toInt() and 255)}.toByte()
        }
        val f=Fixture(BookooUltraLegacyScaleProtocolAdapter);f.boot();f.notify(sample)
        assertEquals(DeviceState.READY,f.session.state)
        assertEquals(1800,f.seen.last().hundredthsGram)
        assertEquals(ScaleEvidence.DOCUMENTED_TRANSPORT,f.seen.last().evidence)
        assertFalse(f.seen.last().eligibleForControl);assertFalse(f.seen.last().capabilities.automaticTare)
        assertEquals(BookooCodec.initializationCommands().map {it.frame.hex()},f.writes().map {ByteFrame(it.bytes).hex()})
    }
    @Test fun newProfilesCannotExpandWritesOrControlToOtherGattOrFrames() {
        val adapters=listOf(FelicitaScaleProtocolAdapter,BookooUltraLegacyScaleProtocolAdapter,
            AcaiaLegacyScaleProtocolAdapter,AcaiaPearlScaleProtocolAdapter,DiFluidScaleProtocolAdapter,DiFluidTiScaleProtocolAdapter)
        for(adapter in adapters) {
            assertFalse(adapter.transportVerified);assertTrue(adapter.documentedTransport)
            assertFalse(adapter.capabilities.validatedWeightControl);assertFalse(adapter.capabilities.automaticTare)
            assertEquals(adapter in listOf(FelicitaScaleProtocolAdapter,DiFluidScaleProtocolAdapter,DiFluidTiScaleProtocolAdapter),
                adapter.capabilities.protocolSupportedWeightControl)
            val endpoint=requireNotNull(adapter.writeEndpoint)
            for(command in adapter.initializationCommands.map {it.frame}+listOfNotNull(adapter.tareCommand?.frame,adapter.heartbeat?.command?.frame)) {
                assertTrue(OutboundWritePolicy.permits(DeviceRole.BOOKOO,GattOperation.Write(endpoint,command.toByteArray(),true),adapter))
                assertFalse(OutboundWritePolicy.permits(DeviceRole.BOOKOO,GattOperation.Write(KnownGatt.coffeeWrite,command.toByteArray(),true),adapter))
                val corrupt=command.toByteArray().apply {this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()}
                assertFalse(OutboundWritePolicy.permits(DeviceRole.BOOKOO,GattOperation.Write(endpoint,corrupt,true),adapter))
            }
            assertFalse(adapter.initializationCommands.any {it.frame.hex() in listOf("54","DFDF03020101C5","030A0700000E")})
        }
        assertNull(AcaiaPearlScaleProtocolAdapter.tareCommand);assertFalse(AcaiaPearlScaleProtocolAdapter.capabilities.tare)
        val f=Fixture(AcaiaPearlScaleProtocolAdapter);f.boot();f.notify(unit+weight)
        val count=f.writes().size;var result:OperationResult?=null
        f.session.tare({true}) {result=it};assertTrue(result is OperationResult.Failed);assertEquals(count,f.writes().size)
    }
}
