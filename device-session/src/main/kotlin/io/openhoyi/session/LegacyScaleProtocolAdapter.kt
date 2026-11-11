package io.openhoyi.session

import io.openhoyi.protocol.*

/** Independently recorded legacy profiles. No hardware, automatic-tare or machine-control claim.
 * Wire evidence and exact scope: docs/plans/legacy-scale-parity.md. No endpoint probing/retries. */
abstract class LegacyDocumentedScaleAdapter:ScaleProtocolAdapter {
    final override val transportVerified=false
    final override val documentedTransport=true
    override fun permitsWrite(operation:GattOperation.Write):Boolean = operation.endpoint==writeEndpoint &&
        (initializationCommands.map {it.frame} + listOfNotNull(tareCommand?.frame,heartbeat?.command?.frame))
            .any {it.toByteArray().contentEquals(operation.bytes)}
    override fun decode(bytes:ByteArray,receivedAtMs:Long):DecodeResult<ScaleObservation> = newDecoder()!!(bytes,receivedAtMs)
}

/** Existing saved felicita-ascii-read-only selections stay read-only. Re-select to enable tare. */
object FelicitaScaleProtocolAdapter:LegacyDocumentedScaleAdapter() {
    override val id="felicita-ascii-legacy"
    override val capabilities=ScaleCapabilities(tare=true)
    override val notifyEndpoint=FelicitaReadOnlyScaleProtocolAdapter.notifyEndpoint
    override val writeEndpoint=notifyEndpoint
    override val initializationCommands=emptyList<TimedCommand>()
    override val tareCommand get()=FelicitaLiveCodec.tare()
    override fun newDecoder(onEvent:(ScaleProtocolEvent)->Unit):(ByteArray,Long)->DecodeResult<ScaleObservation> = {bytes,at->
        when(val result=FelicitaNotificationCodec.decode(bytes)) {
            is DecodeResult.Valid->if(result.value.unit==FelicitaNotification.Unit.GRAM)
                DecodeResult.Valid(ScaleObservation(result.value.signedHundredths,at,capabilities,
                    ScaleEvidence.DOCUMENTED_TRANSPORT,raw=result.value.raw))
                else {onEvent(ScaleProtocolEvent.ReadingInvalidated);DecodeResult.Unknown(result.value.raw)}
            is DecodeResult.Invalid->{onEvent(ScaleProtocolEvent.ReadingInvalidated);result}
            is DecodeResult.Unknown->result
        }
    }
}

/** ULTRA-only legacy discovery is not covered by the hardware evidence of existing BOOKOO. */
object BookooUltraLegacyScaleProtocolAdapter:LegacyDocumentedScaleAdapter() {
    override val id="bookoo-ultra-legacy"
    override val capabilities=ScaleCapabilities(tare=true,deviceFlow=true)
    override val writeEndpoint=KnownGatt.bookooWrite
    override val notifyEndpoint=KnownGatt.bookooNotify
    override val initializationCommands get()=BookooCodec.initializationCommands()
    override val tareCommand get()=BookooCodec.tare()
    override fun newDecoder(onEvent:(ScaleProtocolEvent)->Unit):(ByteArray,Long)->DecodeResult<ScaleObservation> = {bytes,at->
        when(val result=BookooCodec.decode(bytes)) {
            is DecodeResult.Valid->DecodeResult.Valid(ScaleObservation(result.value.weightHundredthsGram,at,
                capabilities,ScaleEvidence.DOCUMENTED_TRANSPORT,deviceFlowHundredths=result.value.deviceFlowHundredths,raw=result.value.raw))
            is DecodeResult.Invalid->{onEvent(ScaleProtocolEvent.ReadingInvalidated);result}
            is DecodeResult.Unknown->result
        }
    }
}

abstract class AcaiaScaleProtocolAdapter(private val variant:AcaiaVariant,final override val id:String):LegacyDocumentedScaleAdapter() {
    override val capabilities=ScaleCapabilities(tare=variant==AcaiaVariant.A)
    override val writeEndpoint=if(variant==AcaiaVariant.A)
        Endpoint("00001820-0000-1000-8000-00805f9b34fb","00002a80-0000-1000-8000-00805f9b34fb")
        else Endpoint("49535343-fe7d-4ae5-8fa9-9fafd205e455","49535343-8841-43f4-a8d4-ecbe34729bb3")
    override val notifyEndpoint=if(variant==AcaiaVariant.A)writeEndpoint
        else Endpoint(writeEndpoint.service,"49535343-1e4d-4bd9-ba61-23c647249616")
    override val initializationCommands get()=if(variant==AcaiaVariant.A)listOf(
        TimedCommand(500,AcaiaCodec.identify(variant).frame),
        TimedCommand(1000,AcaiaCodec.subscribe(variant).frame),
        TimedCommand(1200,AcaiaCodec.heartbeat().frame)) else listOf(
        TimedCommand(2500,AcaiaCodec.identify(variant).frame),
        TimedCommand(5000,AcaiaCodec.heartbeat().frame),
        TimedCommand(5500,AcaiaCodec.subscribe(variant).frame))
    override val heartbeat get()=ScaleHeartbeat(AcaiaCodec.heartbeat(),4500)
    override val tareCommand get()=if(variant==AcaiaVariant.A)AcaiaCodec.tare() else null
    override fun newDecoder(onEvent:(ScaleProtocolEvent)->Unit):(ByteArray,Long)->DecodeResult<ScaleObservation> {
        val stream=AcaiaStreamDecoder()
        var gramsConfirmed=false
        return {bytes,at->
            var observation:DecodeResult<ScaleObservation> = DecodeResult.Unknown(ByteFrame(bytes))
            for(result in stream.feed(bytes))when(result) {
                is DecodeResult.Valid->when(val message=result.value) {
                    is AcaiaUnit->{
                        gramsConfirmed=message.unit==2
                        // Unit state applies in stream order; a later status must revoke earlier weight.
                        if(!gramsConfirmed) {
                            observation=DecodeResult.Unknown(message.raw)
                            onEvent(ScaleProtocolEvent.ReadingInvalidated)
                        }
                    }
                    is AcaiaWeight->observation=if(gramsConfirmed)
                        DecodeResult.Valid(ScaleObservation(message.hundredths,at,capabilities,
                            ScaleEvidence.DOCUMENTED_TRANSPORT,raw=message.raw)) else DecodeResult.Unknown(message.raw)
                }
                is DecodeResult.Invalid->{
                    gramsConfirmed=false
                    observation=result
                    onEvent(ScaleProtocolEvent.ReadingInvalidated)
                }
                is DecodeResult.Unknown->Unit
            }
            observation
        }
    }
}
object AcaiaLegacyScaleProtocolAdapter:AcaiaScaleProtocolAdapter(AcaiaVariant.A,"acaia-a-legacy")
object AcaiaPearlScaleProtocolAdapter:AcaiaScaleProtocolAdapter(AcaiaVariant.AP,"acaia-ap-legacy")

/** Official v1.0 sensor payload has a unit in each full 13-byte payload (g=0).
 * Exact 12-byte payloads require a connection-local protocol gram-unit response.
 * No heuristic offsets or implicit unit from successful writes. */
abstract class DiFluidProtocolAdapter(service:String,final override val id:String):LegacyDocumentedScaleAdapter() {
    override val capabilities=ScaleCapabilities(tare=true)
    override val writeEndpoint=Endpoint(service,"0000aa02-0000-1000-8000-00805f9b34fb")
    override val notifyEndpoint=Endpoint(service,"0000aa01-0000-1000-8000-00805f9b34fb")
    override val initializationCommands get()=listOf(TimedCommand(500,DiFluidCodec.subscribe().frame),
        TimedCommand(1000,DiFluidCodec.setGrams().frame),TimedCommand(1200,DiFluidCodec.readUnit().frame))
    override val tareCommand get()=DiFluidCodec.tare()
    override fun newDecoder(onEvent:(ScaleProtocolEvent)->Unit):(ByteArray,Long)->DecodeResult<ScaleObservation> {
        val stream=DiFluidStreamDecoder()
        var gramsConfirmed=false
        return {bytes,at->
            var observation:DecodeResult<ScaleObservation> = DecodeResult.Unknown(ByteFrame(bytes))
            fun invalidate(raw:ByteFrame) {
                observation=DecodeResult.Unknown(raw)
                onEvent(ScaleProtocolEvent.ReadingInvalidated)
            }
            fun weight(value:Int,raw:ByteFrame) {
                observation=DecodeResult.Valid(ScaleObservation(value,at,
                    capabilities,ScaleEvidence.DOCUMENTED_TRANSPORT,raw=raw))
            }
            for(result in stream.feed(bytes))when(result) {
                is DecodeResult.Valid->when(val message=result.value) {
                    is DiFluidWeight->weight(message.hundredths,message.raw)
                    is DiFluidUnitlessWeight->if(gramsConfirmed)weight(message.hundredths,message.raw)
                        else invalidate(message.raw)
                    is DiFluidUnit->{
                        gramsConfirmed=message.unit==0
                        if(!gramsConfirmed)invalidate(message.raw)
                    }
                }
                is DecodeResult.Invalid->{gramsConfirmed=false;observation=result;onEvent(ScaleProtocolEvent.ReadingInvalidated)}
                is DecodeResult.Unknown->Unit
            }
            observation
        }
    }
}
object DiFluidScaleProtocolAdapter:DiFluidProtocolAdapter("000000ee-0000-1000-8000-00805f9b34fb","difluid-ee-legacy")
object DiFluidTiScaleProtocolAdapter:DiFluidProtocolAdapter("000000dd-0000-1000-8000-00805f9b34fb","difluid-dd-legacy")
