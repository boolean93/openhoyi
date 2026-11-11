package io.openhoyi.mobile

import io.openhoyi.protocol.*
import io.openhoyi.session.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Synthetic GATT only, but all observations originate from real protocol adapters and notifications. */
class ScaleBusinessPipelineTest {
    private fun hex(s:String)=s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun bookoo()=ByteArray(20).apply {
        this[0]=3;this[1]=11;this[6]=43;this[8]=7;this[9]=8;this[10]=43
        this[19]=take(19).fold(0) {a,b->a xor (b.toInt() and 255)}.toByte()
    }
    private fun felicita()=byteArrayOf(1,2,43)+"001800".toByteArray()+" g".toByteArray()+
        byteArrayOf(83,79,48,34,137.toByte(),13,10)
    private fun timemore(command:Int,vararg payload:Int)=byteArrayOf(0xA5.toByte(),0x5A,1,command.toByte(),0,payload.size.toByte())+
        payload.map(Int::toByte).toByteArray()+byteArrayOf(0,0)
    private class Driver:GattDriver {
        val operations=mutableListOf<Triple<Long,Long,GattOperation>>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
            operations+=Triple(generation,token,operation);return true
        }
        override fun close(generation:Long) {}
    }
    private class Fixture(val adapter:ScaleProtocolAdapter) {
        var now=100L
        val driver=Driver()
        var snapshot=MobileSnapshot()
        val session=DeviceSession(DeviceRole.BOOKOO,driver,{now},
            stateChanged={state->snapshot=snapshot.copy(scaleState=state,
                scaleObservation=snapshot.scaleObservation.takeIf {state==DeviceState.READY})},
            scaleAdapter=adapter,scaleObservation={sample->snapshot=snapshot.copy(scaleObservation=sample,
                scaleCapabilities=sample.capabilities)})
        fun complete(result:OperationResult=OperationResult.Success()) {
            val (g,t,_)=driver.operations.last();session.onComplete(g,t,result)
        }
        fun boot() {
            session.connect("AA:BB:CC:DD:EE:FF");complete()
            val notify=requireNotNull(adapter.notifyEndpoint)
            val write=adapter.writeEndpoint
            val common=write==notify
            val wnr=adapter.requiresWriteWithoutResponse
            val characteristics=if(common)listOf(CharacteristicInfo(notify,true,wnr,true,false))
                else listOfNotNull(write?.let {CharacteristicInfo(it,!wnr,wnr,false,false)},
                    CharacteristicInfo(notify,false,false,true,false))
            complete(OperationResult.Success(characteristics));complete()
            val start=now
            for(command in adapter.initializationCommands) {
                now=start+command.delayMs;session.tick();complete()
            }
            assertEquals(DeviceState.SYNCHRONIZING,session.state)
        }
        fun notify(bytes:ByteArray) {session.onNotification(session.generation,requireNotNull(adapter.notifyEndpoint),bytes)}
        fun read():ScaleObservation=requireNotNull(snapshot.scaleObservation)
    }
    private fun read(adapter:ScaleProtocolAdapter):Fixture {
        val f=Fixture(adapter);f.boot()
        when(adapter) {
            BookooScaleProtocolAdapter,BookooUltraLegacyScaleProtocolAdapter -> f.notify(bookoo())
            FelicitaScaleProtocolAdapter,FelicitaReadOnlyScaleProtocolAdapter -> f.notify(felicita())
            DiFluidScaleProtocolAdapter,DiFluidTiScaleProtocolAdapter -> f.notify(hex("DFDF03000D000002F800000000000A27B000A9"))
            TimemoreDotScaleProtocolAdapter,TimemoreBasicLinkScaleProtocolAdapter ->
                f.notify(timemore(6,0)+timemore(1,0,0,0,123,0,0,0,1,0))
            AcaiaLegacyScaleProtocolAdapter,AcaiaPearlScaleProtocolAdapter -> {
                f.notify(hex("EFDD08035A02055A"));f.notify(hex("EFDD0C08053930000002004335"))
            }
            else -> error("Uncovered fixture ${adapter.id}")
        }
        assertEquals(DeviceState.READY,f.session.state)
        assertEquals(adapter.observationEvidence,f.read().evidence)
        return f
    }
    private val frame=ExtractionTelemetry(7,2,90,20,9200,10,0x40,0,ByteFrame(byteArrayOf()))
    @Test fun realNotificationsFeedGenericSnapshotGateSamplesHistoryAndCsv() {
        val adapters=listOf(BookooScaleProtocolAdapter,BookooUltraLegacyScaleProtocolAdapter,
            FelicitaScaleProtocolAdapter,FelicitaReadOnlyScaleProtocolAdapter,
            DiFluidScaleProtocolAdapter,DiFluidTiScaleProtocolAdapter,
            TimemoreDotScaleProtocolAdapter,TimemoreBasicLinkScaleProtocolAdapter,
            AcaiaLegacyScaleProtocolAdapter,AcaiaPearlScaleProtocolAdapter)
        for(adapter in adapters) {
            val f=read(adapter);val observation=f.read();val snapshot=f.snapshot
            assertSame(observation,ScaleBusinessReading.live(observation,snapshot.scaleState,f.now))
            val gate=ShotGate(DefaultStringResources::resolve,mockMode=false)
            val weighted=CurveCatalog.profiles[2]
            val idle=IdleTelemetry(0,0,0,0,0,0,0,0,ByteFrame(byteArrayOf()))
            val blocked=gate.startBlockMessage(weighted,DeviceState.READY,idle,f.now,snapshot.scaleState,
                observation,f.now,ExtractionState.IDLE,validated=true)?.resourceId
            assertEquals(adapter.id,if(observation.eligibleForTargetWeight)null else R.string.scale_control_unverified,blocked)
            val series=ShotSeries();series.begin("shot",f.now)
            ScaleBusinessReading.recordMachine(series,frame,f.now,observation,snapshot.scaleState)
            assertEquals(adapter.id,observation.hundredthsGram,series.points.single().weightHundredthsGram)
            for ((curve,slot) in listOf("factory-v3-001" to 1,"manual" to 6)) {
                var stored=""
                val history=ShotHistory(object:ShotHistory.Storage {
                    override fun read()=stored
                    override fun write(value:String){stored=value}
                },now={1000},newId={"$curve-${adapter.id}"})
                history.begin(curve,atMs=1000,slot=slot)
                history.transition(ExtractionState.ENDED_OBSERVED,null,
                    ScaleBusinessReading.terminalWeight(observation,snapshot.scaleState,f.now),atMs=2000)
                assertEquals(adapter.id,observation.hundredthsGram,history.entries.single().weightHundredthsGram)
                val output=ByteArrayOutputStream()
                ShotHistoryArchive.write(history.entries,{series.points},output)
                var csv="";var tsv=""
                ZipInputStream(ByteArrayInputStream(output.toByteArray())).use {zip->
                    while(true){val entry=zip.nextEntry?:break;val content=zip.readBytes().toString(Charsets.UTF_8)
                        if(entry.name=="history.csv")csv=content else if(entry.name.endsWith(".tsv"))tsv=content}
                }
                assertTrue(csv.contains(",${observation.hundredthsGram},"))
                assertTrue(tsv.contains("\t${observation.hundredthsGram}\t"))
            }
            f.session.disconnect()
            assertNull(ScaleBusinessReading.terminalWeight(f.snapshot.scaleObservation,f.snapshot.scaleState,f.now))
        }
    }
    @Test fun terminalWeightKeepsZeroAndNegativeButRejectsStaleDisconnectedAndInvalid() {
        val f=read(FelicitaReadOnlyScaleProtocolAdapter)
        val sample=f.read()
        for(value in listOf(0,-50_000))assertEquals(value,
            ScaleBusinessReading.terminalWeight(sample.copy(hundredthsGram=value),DeviceState.READY,f.now))
        assertNull(ScaleBusinessReading.terminalWeight(sample,DeviceState.READY,f.now+1501))
        assertNull(ScaleBusinessReading.terminalWeight(sample,DeviceState.DISCONNECTED,f.now))
        assertNull(ScaleBusinessReading.terminalWeight(sample.copy(hundredthsGram=600_001),DeviceState.READY,f.now))
    }
}
