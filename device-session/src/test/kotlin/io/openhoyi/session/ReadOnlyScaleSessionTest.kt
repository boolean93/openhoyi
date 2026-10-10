package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Test
import org.junit.Assert.*

class ReadOnlyScaleSessionTest {
    private class Driver:GattDriver {
        val operations=mutableListOf<Triple<Long,Long,GattOperation>>()
        val closed=mutableListOf<Long>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {operations+=Triple(generation,token,operation);return true}
        override fun close(generation:Long) {closed+=generation}
    }
    private class Fixture {
        var now=100L
        val driver=Driver()
        val seen=mutableListOf<ScaleObservation>()
        var bookooCallbacks=0
        val endpoint=requireNotNull(FelicitaReadOnlyScaleProtocolAdapter.notifyEndpoint)
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},scaleAdapter=FelicitaReadOnlyScaleProtocolAdapter,
            scaleObservation={seen+=it},weightFrame={_,_->bookooCallbacks++})
        fun complete(result:OperationResult=OperationResult.Success()) {
            val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)
        }
        fun subscribe(indicate:Boolean=false) {
            session.connect("read-only-fixture");complete()
            complete(OperationResult.Success(listOf(CharacteristicInfo(endpoint,false,false,!indicate,indicate))))
            assertEquals(GattOperation.Subscribe(endpoint,indicate),driver.operations.last().third)
        }
        fun synchronized() {subscribe();complete();assertEquals(DeviceState.SYNCHRONIZING,session.state)}
        fun noWrites() {assertTrue(driver.operations.none {it.third is GattOperation.Write});assertEquals(0,bookooCallbacks)}
    }
    private fun frame(unit:String=" g")=byteArrayOf(1,2,43)+"001800".toByteArray()+unit.toByteArray()+byteArrayOf(83,79,48,34,137.toByte(),13,10)
    @Test fun subscriptionFailureAndTimeoutCannotPromoteEarlyOrLateNotification() {
        for(timeout in listOf(false,true)) {
            val f=Fixture();f.subscribe();val old=f.session.generation
            val(g,t,_)=f.driver.operations.last()
            f.session.onNotification(old,f.endpoint,frame());assertTrue(f.seen.isEmpty())
            if(timeout) {f.now+=4999;f.session.tick();assertEquals(DeviceState.SUBSCRIBING,f.session.state)
                f.now++;f.session.tick()}
            else f.complete(OperationResult.Failed("subscription rejected"))
            assertEquals(DeviceState.FAILED,f.session.state);assertTrue(old in f.driver.closed)
            f.session.onComplete(g,t,OperationResult.Success())
            f.session.onNotification(old,f.endpoint,frame())
            assertEquals(DeviceState.FAILED,f.session.state);assertTrue(f.seen.isEmpty());f.noWrites()
        }
    }
    @Test fun missingAmbiguousOrNonNotifyingCharacteristicFailsWithoutWrites() {
        val endpoint=requireNotNull(FelicitaReadOnlyScaleProtocolAdapter.notifyEndpoint)
        val valid=CharacteristicInfo(endpoint,false,false,true,false)
        for(characteristics in listOf(emptyList(),listOf(valid,valid),
            listOf(CharacteristicInfo(endpoint,true,false,false,false)),
            listOf(CharacteristicInfo(KnownGatt.bookooNotify,false,false,true,false)))) {
            val f=Fixture();f.session.connect("fixture");f.complete()
            f.complete(OperationResult.Success(characteristics))
            assertEquals(DeviceState.FAILED,f.session.state)
            assertFalse(f.driver.operations.any {it.third is GattOperation.Subscribe});f.noWrites()
        }
    }
    @Test fun indicationSubscriptionAlsoRequiresCompleteGramObservation() {
        val f=Fixture();f.subscribe(indicate=true);f.complete()
        f.session.onNotification(f.session.generation,f.endpoint,frame("oz"))
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertTrue(f.seen.isEmpty())
        f.session.onNotification(f.session.generation,f.endpoint,frame())
        assertEquals(DeviceState.READY,f.session.state);assertEquals(1800,f.seen.single().hundredthsGram);f.noWrites()
    }
    @Test fun reconnectRejectsOldGenerationAndPreservesSignedReadOnlySampleProvenance() {
        val f=Fixture();f.synchronized();val old=f.session.generation
        f.session.onNotification(old,f.endpoint,frame())
        f.session.onDisconnected(old,"injected radio loss")
        assertEquals(DeviceState.FAILED,f.session.state)
        f.synchronized();val current=f.session.generation;assertNotEquals(old,current)
        val before=f.seen.size
        f.session.onNotification(old,f.endpoint,frame())
        f.session.onDisconnected(old,"late old disconnect")
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state);assertEquals(before,f.seen.size)
        for((offset,weight) in listOf(-999999,-1800,0,1800,999999).withIndex()) {
            val bytes=frame().apply {
                this[2]=(if(weight<0) '-'.code else '+'.code).toByte()
                val digits=kotlin.math.abs(weight).toString().padStart(6,'0').toByteArray(Charsets.US_ASCII)
                digits.copyInto(this,3)
            }
            f.now+=offset+1
            f.session.onNotification(current,f.endpoint,bytes)
            val sample=f.seen.last()
            assertEquals(weight,sample.hundredthsGram);assertEquals(f.now,sample.receivedAtMs)
            assertEquals(ByteFrame(bytes),sample.raw);assertEquals(ScaleEvidence.LIVE_READ_ONLY,sample.evidence)
            assertFalse(sample.eligibleForControl);assertFalse(sample.capabilities.tare)
        }
        assertEquals(before+5,f.seen.size);assertEquals(DeviceState.READY,f.session.state);f.noWrites()
    }
    @Test fun documentedReadOnlyScaleSubscribesWithoutAnyCharacteristicWrite() {
        var now=0L;val driver=Driver();val seen=mutableListOf<ScaleObservation>()
        val adapter=FelicitaReadOnlyScaleProtocolAdapter
        val endpoint=requireNotNull(adapter.notifyEndpoint)
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},scaleAdapter=adapter,scaleObservation={seen+=it})
        fun complete(result:OperationResult=OperationResult.Success()) {val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)}
        session.connect("felicita-fixture")
        session.onNotification(session.generation,endpoint,frame());assertTrue(seen.isEmpty())
        complete()
        complete(OperationResult.Success(listOf(CharacteristicInfo(endpoint,false,false,true,false))))
        assertEquals(GattOperation.Subscribe(endpoint,false),driver.operations.last().third)
        session.onNotification(session.generation,endpoint,frame());assertTrue(seen.isEmpty())
        complete();assertEquals(DeviceState.SYNCHRONIZING,session.state)
        session.onNotification(session.generation,endpoint,frame("oz"));assertEquals(DeviceState.SYNCHRONIZING,session.state)
        session.onNotification(session.generation,KnownGatt.bookooNotify,frame());assertEquals(DeviceState.SYNCHRONIZING,session.state)
        session.onNotification(session.generation,endpoint,frame().apply {this[17]=0});assertEquals(DeviceState.SYNCHRONIZING,session.state)
        session.onNotification(session.generation,endpoint,frame())
        assertEquals(DeviceState.READY,session.state);assertEquals(1800,seen.single().hundredthsGram)
        assertEquals(ScaleEvidence.LIVE_READ_ONLY,seen.single().evidence);assertFalse(seen.single().eligibleForControl)
        assertFalse(adapter.capabilities.tare);assertFalse(adapter.capabilities.validatedWeightControl)
        var tare:OperationResult?=null;session.tare({true}) {tare=it};assertTrue(tare is OperationResult.Failed)
        assertTrue(driver.operations.none {it.third is GattOperation.Write})
        val old=session.generation;session.disconnect();session.onNotification(old,endpoint,frame());assertEquals(1,seen.size)
        session.connect("felicita-fixture");session.onNotification(old,endpoint,frame());assertEquals(1,seen.size)
    }

    @Test fun readOnlyAdapterCannotSmuggleWriteEndpointOrControlCapability() {
        for(adapter in listOf(
            object:ScaleProtocolAdapter by FelicitaReadOnlyScaleProtocolAdapter {override val writeEndpoint=KnownGatt.bookooWrite},
            object:ScaleProtocolAdapter by FelicitaReadOnlyScaleProtocolAdapter {override val capabilities=ScaleCapabilities(validatedWeightControl=true)},
            object:ScaleProtocolAdapter by FelicitaReadOnlyScaleProtocolAdapter {override val tareCommand=BookooCodec.tare()},
            object:ScaleProtocolAdapter by FelicitaReadOnlyScaleProtocolAdapter {override val initializationCommands=BookooCodec.initializationCommands()},
            object:ScaleProtocolAdapter by FelicitaReadOnlyScaleProtocolAdapter {override val transportVerified=true}
        )) {
            val driver=Driver()
            try {DeviceSession(DeviceRole.BOOKOO,driver,{0},scaleAdapter=adapter);fail("Read-only contract must reject writes/control")}
            catch(_:IllegalArgumentException) {assertTrue(driver.operations.isEmpty())}
        }
        val driver=Driver();val guard=GuardedGattDriver(DeviceRole.BOOKOO,driver,FelicitaReadOnlyScaleProtocolAdapter)
        assertFalse(guard.execute(1,1,GattOperation.Write(requireNotNull(FelicitaReadOnlyScaleProtocolAdapter.notifyEndpoint),byteArrayOf(0x54),true)))
        assertTrue(driver.operations.isEmpty())
    }
    @Test fun subscriptionAloneTimesOutAndOfflineEvidenceCannotMakeItReady() {
        var now=0L;val driver=Driver();val seen=mutableListOf<ScaleObservation>()
        val adapter=object:ScaleProtocolAdapter by FelicitaReadOnlyScaleProtocolAdapter {
            override fun decode(bytes:ByteArray,receivedAtMs:Long)=ScaleObservationDecoder.offline(LegacyScaleFamily.FELICITA,bytes,receivedAtMs)
        }
        val endpoint=requireNotNull(adapter.notifyEndpoint)
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},scaleAdapter=adapter,scaleObservation={seen+=it})
        fun complete(result:OperationResult=OperationResult.Success()) {val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)}
        session.connect("fixture");complete();complete(OperationResult.Success(listOf(CharacteristicInfo(endpoint,false,false,true,false))));complete()
        session.onNotification(session.generation,endpoint,frame());assertEquals(DeviceState.SYNCHRONIZING,session.state);assertTrue(seen.isEmpty())
        now=4999;session.tick();assertEquals(DeviceState.SYNCHRONIZING,session.state)
        now=5000;session.tick();assertEquals(DeviceState.FAILED,session.state)
        session.onNotification(session.generation,endpoint,frame());assertTrue(seen.isEmpty())
        assertTrue(driver.operations.none {it.third is GattOperation.Write})
    }
    @Test fun readyObserverReplacingConnectionCannotDeliverOldWeight() {
        val driver=Driver();val seen=mutableListOf<ScaleObservation>();lateinit var session:DeviceSession
        val adapter=FelicitaReadOnlyScaleProtocolAdapter;val endpoint=requireNotNull(adapter.notifyEndpoint)
        session=DeviceSession(DeviceRole.BOOKOO,driver,{0},stateChanged={if(it==DeviceState.READY)session.connect("replacement")},
            scaleAdapter=adapter,scaleObservation={seen+=it})
        fun complete(result:OperationResult=OperationResult.Success()) {val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)}
        session.connect("original");complete();complete(OperationResult.Success(listOf(CharacteristicInfo(endpoint,false,false,true,false))));complete()
        session.onNotification(session.generation,endpoint,frame())
        assertEquals("replacement",session.address);assertEquals(DeviceState.CONNECTING,session.state);assertTrue(seen.isEmpty())
    }

    @Test fun coffeeWithReadOnlyScaleAdapterStillRequiresItsOwnWriteEndpointAndAuthentication() {
        val driver=Driver();val coffeeFrames=mutableListOf<HoyiMessage>()
        val session=DeviceSession(DeviceRole.COFFEE,driver,{0},coffeeFrame={value,_->coffeeFrames+=value},
            scaleAdapter=FelicitaReadOnlyScaleProtocolAdapter)
        try {session.connect("coffee");fail("Coffee authentication is mandatory")}
        catch(_:IllegalArgumentException) {assertTrue(driver.operations.isEmpty())}
        fun complete(result:OperationResult=OperationResult.Success()) {val(g,t,_)=driver.operations.last();session.onComplete(g,t,result)}
        val auth=CoffeeAuthentication(java.time.LocalDateTime.of(2026,10,10,0,0),"123456")
        session.connect("coffee",auth);complete()
        complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
        assertEquals(DeviceState.FAILED,session.state);assertTrue(driver.operations.none {it.third is GattOperation.Write})
        session.connect("coffee",auth);complete()
        complete(OperationResult.Success(listOf(CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false),
            CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false))))
        complete()
        val write=driver.operations.last().third as GattOperation.Write
        assertEquals(KnownGatt.coffeeWrite,write.endpoint);assertArrayEquals(auth.encode().frame.toByteArray(),write.bytes)
        complete();assertEquals(DeviceState.SYNCHRONIZING,session.state)
        session.onNotification(session.generation,requireNotNull(FelicitaReadOnlyScaleProtocolAdapter.notifyEndpoint),frame())
        assertEquals(DeviceState.SYNCHRONIZING,session.state);assertTrue(coffeeFrames.isEmpty())
        val settings="830113FD5C007D0F350019006E".chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        session.onNotification(session.generation,KnownGatt.coffeeNotify,settings)
        assertEquals(DeviceState.READY,session.state);assertEquals(1,coffeeFrames.size)
    }
}
