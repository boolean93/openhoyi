package io.openhoyi.bluetooth

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.AcaiaCodec
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test

/** Real classifier/registry/session/decoder path with a simulated GATT boundary, not device acceptance. */
class LunarConnectionPathTest {
    private fun hex(value:String)=value.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    private val grams=hex("EFDD08035A02055A")
    private val weight=hex("EFDD0C08053930000002004335")
    private val weightAndTimer=hex("EFDD0C0C05E80000000100070005030108")
    private val notify=Endpoint("49535343-fe7d-4ae5-8fa9-9fafd205e455","49535343-1e4d-4bd9-ba61-23c647249616")
    private val write=Endpoint(notify.service,"49535343-8841-43f4-a8d4-ecbe34729bb3")

    private inner class Fixture {
        var now=0L
        val observations=mutableListOf<ScaleObservation>()
        val operations=mutableListOf<Triple<Long,Long,GattOperation>>()
        val writeTimes=mutableListOf<Long>()
        val closed=mutableListOf<Long>()
        private var completed=0
        private val driver=object:GattDriver {
            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                operations+=Triple(generation,token,operation)
                if(operation is GattOperation.Write)writeTimes+=now
                return true
            }
            override fun close(generation:Long) {closed+=generation}
        }
        // Use the live advertisement rather than a stale generic cached name, as ScanCoordinator does.
        private val name=ScaleAdvertisementClassifier.discoveryName("ACAIA","LUNAR 123456")
        private val protocolId=requireNotNull(ScaleAdvertisementClassifier.protocolId(name))
        val adapter=requireNotNull(ScaleSelectionPolicy.adapter(protocolId))
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},scaleAdapter=adapter,
            scaleObservation={observations+=it})

        private fun complete(result:OperationResult=OperationResult.Success()) {
            assertTrue("No queued GATT operation to complete",completed<operations.size)
            val (generation,token,_)=operations[completed++]
            session.onComplete(generation,token,result)
        }
        fun boot() {
            assertEquals("LUNAR 123456",name)
            assertSame(AcaiaPearlScaleProtocolAdapter,adapter)
            session.connect("AA:BB:CC:DD:EE:FF")
            assertTrue(operations.last().third is GattOperation.Connect)
            complete()
            assertEquals(GattOperation.Discover,operations.last().third)
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(write,true,false,false,false),
                CharacteristicInfo(notify,false,false,true,false))))
            val subscription=operations.last().third as GattOperation.Subscribe
            assertEquals(notify,subscription.endpoint)
            assertFalse(subscription.indication)
            complete()
            assertEquals(DeviceState.INITIALIZING,session.state)
            advanceTo(5500)
            assertEquals(DeviceState.SYNCHRONIZING,session.state)
            assertEquals(listOf(2500L,5000L,5500L),writeTimes)
            assertEquals(listOf("EFDD0B3031323334353637383930313233349A6D",
                "EFDD0002000200","EFDD0C0900010102020103041106"),writes().map {ByteFrame(it.bytes).hex()})
            assertTrue(writes().all {it.endpoint==write})
        }
        /** Run the production tick/queue and GATT callbacks, not a separate timing model. */
        fun advanceTo(target:Long) {
            require(target>=now)
            while(now<target) {
                now=minOf(now+100,target)
                session.tick()
                while(completed<operations.size) {
                    assertTrue("Only initialization/heartbeat writes expected",operations[completed].third is GattOperation.Write)
                    complete()
                }
            }
        }
        fun notify(bytes:ByteArray) {session.onNotification(session.generation,notify,bytes)}
        fun writes()=operations.mapNotNull {it.third as? GattOperation.Write}
    }

    @Test fun lunarApHasFullSynchronizationWindowAfterInitializationAndNeedsStatusPlusWeight() {
        val f=Fixture();f.boot()
        f.notify(grams)
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        assertTrue(f.observations.isEmpty())
        f.advanceTo(10_499) // 4,999ms after the final AP initialization callback.
        f.notify(weight)
        assertEquals(DeviceState.READY,f.session.state)
        assertEquals(12345,f.observations.single().hundredthsGram)
        assertEquals(10_499L,f.observations.single().receivedAtMs)
        assertEquals(ScaleEvidence.DOCUMENTED_TRANSPORT,f.observations.single().evidence)
        assertFalse(f.observations.single().eligibleForControl)
        assertFalse(f.observations.single().capabilities.automaticTare)
    }

    @Test fun lunarApWeightWithoutStatusCannotBecomeReadyAndFailsAtSynchronizationDeadline() {
        val f=Fixture();f.boot()
        f.notify(weightAndTimer)
        f.notify(weight)
        f.advanceTo(10_499);f.notify(weight)
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        assertTrue(f.observations.isEmpty())
        f.advanceTo(10_500)
        assertEquals(DeviceState.FAILED,f.session.state)
        assertTrue(f.closed.isNotEmpty())
        assertTrue(f.observations.isEmpty())
        assertEquals(4,f.writes().size) // Three initialization writes and one serialized heartbeat only.
        assertEquals("EFDD0002000200",ByteFrame(f.writes().last().bytes).hex())
    }

    @Test fun lunarApFragmentsWorkAndBadFrameRequiresFreshGramStatusToRecover() {
        val f=Fixture();f.boot()
        f.notify(grams.copyOfRange(0,4))
        f.notify(grams.copyOfRange(4,grams.size)+weight.copyOfRange(0,7))
        assertTrue(f.observations.isEmpty())
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        f.notify(weight.copyOfRange(7,weight.size))
        assertEquals(DeviceState.READY,f.session.state)
        assertEquals(1,f.observations.size)
        f.notify(weight.copyOf().apply {this[lastIndex]=0})
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        f.notify(weight)
        assertEquals(1,f.observations.size)
        assertEquals(DeviceState.SYNCHRONIZING,f.session.state)
        f.notify(grams.copyOfRange(0,5))
        f.notify(grams.copyOfRange(5,grams.size)+weight)
        assertEquals(DeviceState.READY,f.session.state)
        assertEquals(listOf(12345,12345),f.observations.map {it.hundredthsGram})
    }

    @Test fun lunarApCombinedWeightAndTimerMustReadWeightWithoutRevokingGramStatus() {
        val f=Fixture();f.boot();f.notify(grams)
        // Length 12: normal six-byte weight (232 / 10 = 23.2g), followed by a timer event.
        // Checksums independently sum alternating payload bytes to 0x01 / 0x08.
        val combined=weightAndTimer
        f.notify(combined)
        val afterCombined=f.session.state
        val combinedReadings=f.observations.map {it.hundredthsGram}
        f.notify(weight)
        val afterNormalWeight=f.session.state
        val normalReadings=f.observations.map {it.hundredthsGram}
        f.notify(grams+weight)
        assertEquals("Fresh status plus standard weight must still recover",DeviceState.READY,f.session.state)
        assertEquals("parse=${AcaiaCodec.decode(combined)}; after standard weight=$afterNormalWeight/$normalReadings; fresh status recovery=${f.session.state}",
            DeviceState.READY,afterCombined)
        assertEquals(listOf(2320),combinedReadings)
        assertEquals(DeviceState.READY,afterNormalWeight)
        assertEquals(listOf(2320,12345),normalReadings)
    }
}
