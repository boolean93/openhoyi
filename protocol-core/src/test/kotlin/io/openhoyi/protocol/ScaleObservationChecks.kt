package io.openhoyi.protocol

/** Software evidence only; never represents a connection or physical control acceptance. */
fun main() {
    fun hex(s:String)=s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val frame=ByteArray(20).apply {
        this[0]=3;this[1]=11;this[6]=45;this[8]=7;this[9]=8;this[10]=43;this[12]=10
        this[19]=take(19).fold(0) { a,b -> a xor (b.toInt() and 255) }.toByte()
    }
    val old=(BookooCodec.decode(frame) as DecodeResult.Valid).value
    val generic=(ScaleObservationDecoder.bookoo(frame,20) as DecodeResult.Valid).value
    check(generic.hundredthsGram==old.weightHundredthsGram)
    check(generic.deviceFlowHundredths==old.deviceFlowHundredths)
    check(generic.raw==old.raw && generic.receivedAtMs==20L)
    check(generic.eligibleForControl && generic.capabilities.tare)
    check(!generic.capabilities.battery && !generic.capabilities.deviceTimer && generic.batteryPercent==null)
    check(ScaleObservationDecoder.bookoo(frame.copyOf(19),20) is DecodeResult.Invalid)
    check(ScaleObservationDecoder.bookoo(frame.apply {this[19]=0},20) is DecodeResult.Invalid)
    check(ScaleObservationDecoder.bookoo(hex("0102"),20) is DecodeResult.Unknown)
    for(family in LegacyScaleFamily.entries) {
        val bytes=when(family) {
            LegacyScaleFamily.ACAIA->hex("EFDD0C0005080700000200")
            LegacyScaleFamily.FELICITA->hex("0708")
            LegacyScaleFamily.DIFLUID->hex("DFDF030000000000B4")
        }
        val candidate=(ScaleObservationDecoder.offline(family,bytes,50) as DecodeResult.Valid).value
        check(candidate.evidence==ScaleEvidence.OFFLINE_CANDIDATE && !candidate.eligibleForControl)
        check(!candidate.capabilities.tare && !candidate.capabilities.validatedWeightControl)
    }
    println("PASS scale observation equivalence, malformed rejection and offline-only evidence")
}
