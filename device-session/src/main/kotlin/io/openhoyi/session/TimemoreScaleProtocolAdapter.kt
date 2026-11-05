package io.openhoyi.session

import io.openhoyi.protocol.*

/** DOT TES017 / Basic3 Link TES016, public v1.0.3 protocol. No hardware/control validation.
 * Decode state (units, battery and fragmented packets) belongs to one connection, never a singleton. */
abstract class TimemoreDocumentedScaleProtocolAdapter(final override val id:String):ScaleProtocolAdapter {
    override val capabilities=ScaleCapabilities(tare=true,deviceFlow=true,battery=true)
    override val writeEndpoint=Endpoint("0000fff0-0000-1000-8000-00805f9b34fb","0000fff2-0000-1000-8000-00805f9b34fb")
    override val notifyEndpoint=Endpoint(writeEndpoint.service,"0000fff1-0000-1000-8000-00805f9b34fb")
    override val transportVerified=false
    override val documentedTransport=true
    override val requiresWriteWithoutResponse=true
    override val requiresTareAcknowledgement=true
    // Unit write ACK is NOT unit readback. Never send mode 08, tare, reset or pairing commands here.
    override val initializationCommands get()=listOf(TimedCommand(0,TimemoreCodec.setGrams().frame),TimedCommand(200,TimemoreCodec.readUnit().frame))
    override val tareCommand get()=TimemoreCodec.tare()
    override fun permitsWrite(operation:GattOperation.Write):Boolean =
        operation.endpoint==writeEndpoint && !operation.withResponse &&
            (initializationCommands.any { it.frame.toByteArray().contentEquals(operation.bytes) } ||
                tareCommand.frame.toByteArray().contentEquals(operation.bytes))
    /** Stateless probing cannot establish units; the live session uses newDecoder(). */
    override fun decode(bytes:ByteArray,receivedAtMs:Long):DecodeResult<ScaleObservation> = newDecoder()(bytes,receivedAtMs)
    override fun newDecoder(onEvent:(ScaleProtocolEvent)->Unit):(ByteArray,Long)->DecodeResult<ScaleObservation> {
        val stream=TimemoreStreamDecoder()
        var gramsConfirmed=false
        var battery:Int?=null
        return decoder@{bytes,receivedAtMs->
            var result:DecodeResult<ScaleObservation> = DecodeResult.Unknown(ByteFrame(bytes))
            var tareAcknowledgement:Boolean?=null
            for(decoded in stream.feed(bytes)) when(decoded) {
                is DecodeResult.Valid -> when(val message=decoded.value) {
                    is TimemoreUnit -> {
                        gramsConfirmed=message.unit==0
                        // A later unit change in a coalesced notification invalidates earlier weight.
                        if(!gramsConfirmed) {
                            result=DecodeResult.Unknown(message.raw)
                            onEvent(ScaleProtocolEvent.ReadingInvalidated)
                        }
                    }
                    is TimemoreBattery -> battery=message.percent
                    is TimemoreWeight -> result=when {
                        !gramsConfirmed->DecodeResult.Unknown(message.raw)
                        message.overloaded->{
                            onEvent(ScaleProtocolEvent.ReadingInvalidated)
                            DecodeResult.Invalid("Timemore scale overload",message.raw)
                        }
                        else->DecodeResult.Valid(ScaleObservation(message.hundredthsGram,receivedAtMs,
                            capabilities,ScaleEvidence.DOCUMENTED_TRANSPORT,
                            deviceFlowHundredths=message.flowHundredths,batteryPercent=battery,raw=message.raw))
                    }
                    is TimemoreAck -> {
                        if(message.command==0x0D) {
                            // A weight earlier in this batch cannot confirm a later tare ACK.
                            result=DecodeResult.Unknown(message.raw)
                            if(tareAcknowledgement!=false)tareAcknowledgement=message.success
                        }
                        if(!message.success) {
                            // Failed unit selection cannot leave a previous unit confirmation trusted.
                            if(message.command==0x06) {
                                gramsConfirmed=false
                                onEvent(ScaleProtocolEvent.ReadingInvalidated)
                            }
                            result=DecodeResult.Invalid("Timemore command ${message.command} rejected",message.raw)
                        }
                    }
                }
                is DecodeResult.Invalid -> {
                    // A malformed/unknown unit must revoke trust too; corrupt weight is never fresh.
                    val raw=decoded.raw.toByteArray()
                    if(raw.size>=4 && raw[3].toInt() and 255==0x06)gramsConfirmed=false
                    onEvent(ScaleProtocolEvent.ReadingInvalidated)
                    result=decoded
                }
                is DecodeResult.Unknown -> if(result !is DecodeResult.Valid)result=decoded
            }
            tareAcknowledgement?.let {onEvent(ScaleProtocolEvent.TareAcknowledgement(it))}
            result
        }
    }
}
object TimemoreDotScaleProtocolAdapter:TimemoreDocumentedScaleProtocolAdapter("timemore-dot")
object TimemoreBasicLinkScaleProtocolAdapter:TimemoreDocumentedScaleProtocolAdapter("timemore-basic-link")

/** Experimental 181D/2A9D nine-byte dual-sensor profile. No heartbeat, tare or other writes.
 * Primary=second sensor on the wire; no claim that all Black Mirror 2/DUO firmware shares it. */
object TimemoreLegacyReadOnlyScaleProtocolAdapter:ScaleProtocolAdapter {
    override val id="timemore-legacy-read-only"
    override val capabilities=ScaleCapabilities()
    override val writeEndpoint:Endpoint?=null
    override val notifyEndpoint=Endpoint("0000181d-0000-1000-8000-00805f9b34fb","00002a9d-0000-1000-8000-00805f9b34fb")
    override val initializationCommands=emptyList<TimedCommand>()
    override val tareCommand:EncodedCommand?=null
    override val transportVerified=false
    override val readOnlyTransport=true
    override fun permitsWrite(operation:GattOperation.Write)=false
    override fun decode(bytes:ByteArray,receivedAtMs:Long):DecodeResult<ScaleObservation> =
        when(val decoded=TimemoreLegacyCodec.decode(bytes)) {
            is DecodeResult.Valid->DecodeResult.Valid(ScaleObservation(decoded.value.hundredthsGram,
                receivedAtMs,capabilities,ScaleEvidence.LIVE_READ_ONLY,raw=decoded.value.raw,
                secondaryHundredthsGram=decoded.value.secondaryHundredthsGram))
            is DecodeResult.Invalid->decoded
            is DecodeResult.Unknown->decoded
        }
}
