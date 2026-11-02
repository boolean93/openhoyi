package io.openhoyi.session

import io.openhoyi.protocol.*

/** Protocol boundary owned by the existing scale DeviceSession. Offline candidates have no
 * endpoints, initialization or writes, and cannot be supplied to a connectable session. */
interface ScaleProtocolAdapter {
    val id:String
    val capabilities:ScaleCapabilities
    val writeEndpoint:Endpoint?
    val notifyEndpoint:Endpoint?
    val initializationCommands:List<TimedCommand>
    val tareCommand:EncodedCommand?
    val transportVerified:Boolean
    /** Explicit documented subscription-only transport; never permits a characteristic write. */
    val readOnlyTransport:Boolean get()=false
    fun decode(bytes:ByteArray,receivedAtMs:Long):DecodeResult<ScaleObservation>
    fun permitsWrite(operation:GattOperation.Write):Boolean
}
object BookooScaleProtocolAdapter:ScaleProtocolAdapter {
    override val id="bookoo"
    override val capabilities=ScaleObservationDecoder.bookooCapabilities
    override val writeEndpoint get()=KnownGatt.bookooWrite
    override val notifyEndpoint get()=KnownGatt.bookooNotify
    override val initializationCommands get()=BookooCodec.initializationCommands()
    override val tareCommand get()=BookooCodec.tare()
    override val transportVerified=true
    override fun decode(bytes:ByteArray,receivedAtMs:Long)=ScaleObservationDecoder.bookoo(bytes,receivedAtMs)
    override fun permitsWrite(operation:GattOperation.Write):Boolean = operation.endpoint==writeEndpoint &&
        (initializationCommands.any { it.frame.toByteArray().contentEquals(operation.bytes) } ||
            tareCommand.frame.toByteArray().contentEquals(operation.bytes))
}
class OfflineScaleProtocolAdapter(val family:LegacyScaleFamily):ScaleProtocolAdapter {
    override val id=family.name.lowercase(java.util.Locale.ROOT)
    override val capabilities=ScaleCapabilities()
    override val writeEndpoint:Endpoint?=null
    override val notifyEndpoint:Endpoint?=null
    override val initializationCommands=emptyList<TimedCommand>()
    override val tareCommand:EncodedCommand?=null
    override val transportVerified=false
    override fun decode(bytes:ByteArray,receivedAtMs:Long)=ScaleObservationDecoder.offline(family,bytes,receivedAtMs)
    override fun permitsWrite(operation:GattOperation.Write)=false
}
object ScaleProtocolRegistry {
    val connectable:List<ScaleProtocolAdapter> = listOf(BookooScaleProtocolAdapter)
    val offlineCandidates:List<ScaleProtocolAdapter> = LegacyScaleFamily.entries.map(::OfflineScaleProtocolAdapter)
    val readOnlyCandidates:List<ScaleProtocolAdapter> = listOf(FelicitaReadOnlyScaleProtocolAdapter)
}

/** Published FFE0/FFE1 ASCII notification shape, not all Felicita models/firmware.
 * Beanconqueror b975d393 constants.ts + felicita-readme.md. No application-level writes. */
object FelicitaReadOnlyScaleProtocolAdapter:ScaleProtocolAdapter by OfflineScaleProtocolAdapter(LegacyScaleFamily.FELICITA) {
    override val id="felicita-ascii-read-only"
    override val readOnlyTransport=true
    override val notifyEndpoint=Endpoint("0000ffe0-0000-1000-8000-00805f9b34fb","0000ffe1-0000-1000-8000-00805f9b34fb")
    override fun decode(bytes:ByteArray,receivedAtMs:Long):DecodeResult<ScaleObservation> =
        when(val decoded=FelicitaNotificationCodec.decode(bytes)) {
            is DecodeResult.Valid -> if(decoded.value.unit==FelicitaNotification.Unit.GRAM)
                DecodeResult.Valid(ScaleObservation(decoded.value.signedHundredths,receivedAtMs,
                    capabilities,ScaleEvidence.LIVE_READ_ONLY,raw=decoded.value.raw))
                else DecodeResult.Unknown(decoded.value.raw)
            is DecodeResult.Invalid -> decoded
            is DecodeResult.Unknown -> decoded
        }
}
