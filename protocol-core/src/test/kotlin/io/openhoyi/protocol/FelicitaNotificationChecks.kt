package io.openhoyi.protocol

/** Published notification fixtures, not hardware or connection acceptance. */
internal fun runFelicitaNotificationChecks() {
    fun frame(digits:String="000000",sign:Char='+',unit:String=" g",battery:Int=137):ByteArray =
        byteArrayOf(1,2,sign.code.toByte())+digits.toByteArray(Charsets.US_ASCII)+
            unit.toByteArray(Charsets.US_ASCII)+byteArrayOf(83,79,48,34,battery.toByte(),13,10)
    fun read(bytes:ByteArray)=ScaleObservationDecoder.offline(LegacyScaleFamily.FELICITA,bytes,50)
    fun weight(bytes:ByteArray,expected:Int) {
        val result=read(bytes)
        check(result is DecodeResult.Valid) {"Expected offline Felicita weight, got $result"}
        check(result.value.hundredthsGram==expected) {"Felicita ASCII weight expected $expected, got ${result.value.hundredthsGram}"}
        check(result.value.receivedAtMs==50L && result.value.raw==ByteFrame(bytes))
        check(result.value.evidence==ScaleEvidence.OFFLINE_CANDIDATE && !result.value.eligibleForControl)
        check(!result.value.capabilities.tare && !result.value.capabilities.deviceTimer && !result.value.capabilities.validatedWeightControl)
    }
    weight(frame(),0)
    weight(frame("123456"),123456)
    weight(frame("123456",'-'),-123456)
    weight(frame("999999"),999999)
    weight(frame("000000",'-'),0)
    check(read(frame(unit="oz")) is DecodeResult.Unknown) {"Ounces must not become grams"}
    check(read(frame(unit="kg")) is DecodeResult.Unknown)
    check(read(frame(sign='?')) is DecodeResult.Invalid)
    check(read(frame("123a56")) is DecodeResult.Invalid)
    check(read(frame().apply {this[17]=0}) is DecodeResult.Invalid)
    check(read(frame().copyOf(3)) is DecodeResult.Invalid)
    check(read(frame().copyOf(4)) is DecodeResult.Invalid)
    check(read(frame().copyOf(17)) is DecodeResult.Invalid)
    check(read(frame()+byteArrayOf(0)) is DecodeResult.Invalid)
    check(read(frame().apply {this[0]=3}) is DecodeResult.Unknown)
    weight(frame(battery=0),0) // Unknown battery cannot invalidate a valid displayed weight.
    val legacy=LegacyScaleCandidateCodec.decode(LegacyScaleFamily.FELICITA,byteArrayOf(0x30,0x39,0xab.toByte(),0xcd.toByte()))
    check(legacy is DecodeResult.Valid && legacy.value.weightGrams==123.45)
    check(LegacyScaleCandidateCodec.decode(LegacyScaleFamily.FELICITA,frame()) is DecodeResult.Unknown)
    val ounces=FelicitaNotificationCodec.decode(frame("001234",'-',"oz"))
    check(ounces is DecodeResult.Valid && ounces.value.signedHundredths==-1234 && ounces.value.unit==FelicitaNotification.Unit.OUNCE)
    println("PASS Felicita ASCII units, sign, framing, malformed rejection and offline-only observations")
}
