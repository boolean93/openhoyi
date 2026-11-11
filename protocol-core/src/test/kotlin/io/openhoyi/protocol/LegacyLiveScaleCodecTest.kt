package io.openhoyi.protocol

/** Minimal documented wire cases, not BLE/device/control acceptance. Lead hooks this into checks. */
fun legacyLiveScaleChecks() {
    fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun acaia(type: Int, payload: ByteArray): ByteArray {
        val sums = IntArray(2)
        payload.forEachIndexed { i, b -> sums[i % 2] = (sums[i % 2] + (b.toInt() and 255)) % 256 }
        return byteArrayOf(0xEF.toByte(), 0xDD.toByte(), type.toByte()) + payload + byteArrayOf(sums[0].toByte(), sums[1].toByte())
    }
    fun aWeight(value: Int, decimals: Int = 2, negative: Boolean = false): ByteArray =
        acaia(12, byteArrayOf(8, 5, value.toByte(), (value shr 8).toByte(), 0, 0,
            decimals.toByte(), if (negative) 2 else 0))
    fun dFrame(function: Int, cmd: Int, payload: ByteArray): ByteArray {
        val body = byteArrayOf(0xDF.toByte(), 0xDF.toByte(), function.toByte(), cmd.toByte(), payload.size.toByte()) + payload
        return body + byteArrayOf(body.fold(0) { sum, b -> (sum + (b.toInt() and 255)) % 256 }.toByte())
    }
    fun dWeight(value: Int) = dFrame(3, 0, ByteArray(4) { (value ushr (24 - it * 8)).toByte() } + ByteArray(9))
    fun aRead(b: ByteArray): AcaiaMessage {
        val result = AcaiaCodec.decode(b)
        check(result is DecodeResult.Valid) { "Expected Acaia message: $result" }
        return result.value
    }
    fun dRead(b: ByteArray): DiFluidWeight {
        val result = DiFluidCodec.decode(b)
        check(result is DecodeResult.Valid) { "Expected DiFluid message: $result" }
        return result.value as DiFluidWeight
    }
    check(AcaiaCodec.identify(AcaiaVariant.A).frame.hex() == "EFDD0B" + "2D".repeat(15) + "683B")
    check(AcaiaCodec.identify(AcaiaVariant.AP).frame.hex() == "EFDD0B3031323334353637383930313233349A6D")
    check(AcaiaCodec.subscribe(AcaiaVariant.A).frame.hex() == "EFDD0C0900010102020503041506")
    check(AcaiaCodec.subscribe(AcaiaVariant.AP).frame.hex() == "EFDD0C0900010102020103041106")
    check(AcaiaCodec.heartbeat().frame.hex() == "EFDD0002000200")
    check(AcaiaCodec.tare().frame.hex() == "EFDD04" + "00".repeat(15) + "0400")
    check(DiFluidCodec.subscribe().frame.hex() == "DFDF01000101C1")
    check(DiFluidCodec.setGrams().frame.hex() == "DFDF01040100C4")
    check(DiFluidCodec.readUnit().frame.hex() == "DFDF010400C3")
    check(DiFluidCodec.tare().frame.hex() == "DFDF03020101C5")
    check(FelicitaLiveCodec.tare().frame.hex() == "54")

    val positive = hex("EFDD0C08053930000002004335")
    val negative = hex("EFDD0C08053930000002024337")
    check((aRead(positive) as AcaiaWeight).let {
        it.rawMagnitude == 12345 && it.decimalPlaces == 2 && !it.negative && it.hundredths == 12345
    })
    check((aRead(negative) as AcaiaWeight).hundredths == -12345)
    check((aRead(aWeight(65535, 1)) as AcaiaWeight).hundredths == 655350)
    check((aRead(aWeight(12345, 3)) as AcaiaWeight).hundredths == 1235)
    check((aRead(aWeight(12350, 4, true)) as AcaiaWeight).hundredths == -124)
    check((aRead(aWeight(4, 3, true)) as AcaiaWeight).hundredths == 0)
    val grams = hex("EFDD08035A02055A")
    check((aRead(grams) as AcaiaUnit).unit == 2)
    check((aRead(acaia(8, byteArrayOf(3, 90, 5))) as AcaiaUnit).unit == 5)
    check((aRead(acaia(8, byteArrayOf(3, 90, 9))) as AcaiaUnit).unit == 9) // Revoke old unit trust.
    check(AcaiaCodec.decode(aWeight(100, 0)) is DecodeResult.Invalid)
    check(AcaiaCodec.decode(aWeight(100, 5)) is DecodeResult.Invalid)
    check(AcaiaCodec.decode(positive.copyOf().apply { this[lastIndex] = 0 }) is DecodeResult.Invalid)
    check(AcaiaCodec.decode(positive.copyOf(12)) is DecodeResult.Invalid)
    check(AcaiaCodec.decode(positive + byteArrayOf(0)) is DecodeResult.Invalid)
    check(AcaiaCodec.decode(acaia(12, byteArrayOf(2, 6))) is DecodeResult.Unknown)
    check(AcaiaCodec.decode(acaia(8, byteArrayOf(1))) is DecodeResult.Invalid)

    val aStream = AcaiaStreamDecoder()
    check(aStream.feed(positive.copyOf(7)).isEmpty())
    check(aStream.feed(positive.copyOfRange(7, positive.size) + grams + negative).let {
        it.size == 3 && (it[0] as DecodeResult.Valid).value is AcaiaWeight &&
            (it[1] as DecodeResult.Valid).value is AcaiaUnit &&
            ((it[2] as DecodeResult.Valid).value as AcaiaWeight).hundredths == -12345
    })
    check(aStream.feed(byteArrayOf()).isEmpty())
    check(AcaiaStreamDecoder().feed(positive.copyOfRange(7, positive.size)).isEmpty()) // New connection has no fragment.
    val aRecovered = aStream.feed(ByteArray(4096) { 0x33 } + hex("EFEFDD0CFF") + positive)
    check(aRecovered.first() is DecodeResult.Invalid)
    check(aRecovered.filterIsInstance<DecodeResult.Valid<AcaiaMessage>>().size == 1)
    val badA = positive.copyOf().apply { this[lastIndex] = 0 }
    check(aStream.feed(badA + positive).let { it.first() is DecodeResult.Invalid && it.count { r -> r is DecodeResult.Valid } == 1 })
    val maxA = acaia(7, byteArrayOf(251.toByte()) + ByteArray(250))
    check(maxA.size == 256 && aStream.feed(maxA.copyOf(255)).isEmpty())
    check(aStream.feed(maxA.copyOfRange(255, 256)).single() is DecodeResult.Unknown)

    val dPositive = hex("DFDF03000D000004D2000000000000000000A4")
    val dNegative = hex("DFDF03000DFFFFFB2E000000000000000000F5")
    check(dRead(dPositive).let { it.rawTenths == 1234 && it.hundredths == 12340 })
    check(dRead(dNegative).hundredths == -12340)
    check(dRead(dWeight(214748364)).hundredths == 2147483640)
    check(DiFluidCodec.decode(dWeight(214748365)) is DecodeResult.Invalid)
    check(DiFluidCodec.decode(dWeight(Int.MIN_VALUE)) is DecodeResult.Invalid)
    val unitless = DiFluidCodec.decode(dFrame(3, 0, ByteArray(12)))
    check(unitless is DecodeResult.Valid && unitless.value is DiFluidUnitlessWeight)
    check(DiFluidCodec.decode(dFrame(3, 0, ByteArray(11))) is DecodeResult.Invalid)
    check(DiFluidCodec.decode(dPositive.copyOf(18)) is DecodeResult.Invalid)
    check(DiFluidCodec.decode(dPositive + byteArrayOf(0)) is DecodeResult.Invalid)
    check(DiFluidCodec.decode(dPositive.copyOf().apply { this[lastIndex] = 0 }) is DecodeResult.Invalid)
    check(dRead(hex("DFDF03000D000002F800000000000A27B000A9")).hundredths == 7600) // Official 76g example.
    val unitResponse = DiFluidCodec.decode(hex("DFDF01040100C4"))
    check(unitResponse is DecodeResult.Valid && (unitResponse.value as DiFluidUnit).unit == 0)
    for (unit in listOf(1, 2, 9)) {
        val payload = ByteArray(13).apply { this[12] = unit.toByte() }
        val nonGram = DiFluidCodec.decode(dFrame(3, 0, payload))
        check(nonGram is DecodeResult.Valid && (nonGram.value as DiFluidUnit).unit == unit)
        val status = DiFluidCodec.decode(dFrame(1, 4, byteArrayOf(unit.toByte())))
        check(status is DecodeResult.Valid && (status.value as DiFluidUnit).unit == unit)
    }
    check(DiFluidCodec.decode(dFrame(3, 0, ByteArray(14))) is DecodeResult.Invalid)
    check(DiFluidCodec.decode(dFrame(1, 4, byteArrayOf())) is DecodeResult.Invalid)
    val dStream = DiFluidStreamDecoder()
    check(dStream.feed(dPositive.copyOf(5)).isEmpty())
    check(dStream.feed(dPositive.copyOfRange(5, dPositive.size) + dNegative).let {
        it.size == 2 && ((it.last() as DecodeResult.Valid).value as DiFluidWeight).hundredths == -12340
    })
    check(dStream.feed(byteArrayOf()).isEmpty())
    val dRecovered = dStream.feed(ByteArray(4096) { 0x22 } + hex("DFDF0300FF") + dPositive)
    check(dRecovered.first() is DecodeResult.Invalid)
    check(dRecovered.count { it is DecodeResult.Valid } == 1)
    val badD = dPositive.copyOf().apply { this[lastIndex] = 0 }
    check(dStream.feed(badD + dNegative).let { it.first() is DecodeResult.Invalid && it.count { r -> r is DecodeResult.Valid } == 1 })
    val maxD = dFrame(2, 0, ByteArray(250))
    check(maxD.size == 256 && dStream.feed(maxD.copyOf(255)).isEmpty())
    check(dStream.feed(maxD.copyOfRange(255, 256)).single() is DecodeResult.Unknown)
    println("PASS legacy live command goldens, checked Acaia units/precision and bounded signed DiFluid framing")
}
