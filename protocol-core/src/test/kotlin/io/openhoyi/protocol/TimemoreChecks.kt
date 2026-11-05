package io.openhoyi.protocol

/** Wire-only checks: do not imply firmware, BLE connection or hardware acceptance. */
fun timemoreChecks() {
    fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun be32(value: Int) = ByteArray(4) { (value ushr (24 - it * 8)).toByte() }
    fun le32(value: Int) = be32(value).reversedArray()
    fun frame(op: Int, cmd: Int, payload: ByteArray, zeroCrc: Boolean = false): ByteArray {
        val body = byteArrayOf(0xA5.toByte(), 0x5A, op.toByte(), cmd.toByte(),
            (payload.size shr 8).toByte(), payload.size.toByte()) + payload
        var crc = 0xFFFF
        // Independent bit-at-a-time oracle plus a fixed tare regression below.
        // Neither is a vendor/hardware capture; CRC interpretation still requires hardware acceptance.
        for (byte in body) for (bit in 0..7) {
            val feedback = (crc xor (byte.toInt() ushr bit)) and 1
            crc = (crc ushr 1) xor if (feedback == 1) 0xA001 else 0
        }
        return body + if (zeroCrc) byteArrayOf(0, 0) else byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }
    fun weight(value: Int, flow: Int = -123, timer: Int = 3599, overload: Int = 0, zeroCrc: Boolean = false) =
        frame(1, 1, be32(value) + byteArrayOf((flow shr 8).toByte(), flow.toByte(),
            (timer shr 8).toByte(), timer.toByte(), overload.toByte()), zeroCrc)
    fun message(bytes: ByteArray): TimemoreMessage {
        val result = TimemoreCodec.decode(bytes)
        check(result is DecodeResult.Valid) { "Expected TIMEMORE message, got $result" }
        return result.value
    }
    fun invalid(bytes: ByteArray) { check(TimemoreCodec.decode(bytes) is DecodeResult.Invalid) }

    check(TimemoreCodec.tare().frame.hex() == "A55A030D000064D1")
    check(TimemoreCodec.setGrams().frame == ByteFrame(frame(3, 6, byteArrayOf(0))))
    check(TimemoreCodec.readUnit().frame == ByteFrame(frame(2, 6, byteArrayOf())))
    val positive = message(weight(123456)) as TimemoreWeight
    check(positive.hundredthsGram == 1234560 && positive.flowHundredths == -1230)
    check(positive.timerSeconds == 3599 && !positive.overloaded)
    check((message(weight(-123456, 32767, 0, 1)) as TimemoreWeight).let {
        it.hundredthsGram == -1234560 && it.flowHundredths == 327670 && it.timerSeconds == 0 && it.overloaded
    })
    check((message(weight(214748364)) as TimemoreWeight).hundredthsGram == 2147483640)
    check((message(weight(-214748364)) as TimemoreWeight).hundredthsGram == -2147483640)
    invalid(weight(214748365)); invalid(weight(Int.MIN_VALUE))
    invalid(weight(1, timer = 3600)); invalid(weight(1, overload = 2))
    invalid(frame(1, 1, ByteArray(8))); invalid(frame(1, 1, ByteArray(10)))
    check(message(frame(1, 5, byteArrayOf(4, 100))) is TimemoreBattery)
    check((message(frame(2, 5, byteArrayOf(0, 0))) as TimemoreBattery).percent == 0)
    invalid(frame(2, 5, byteArrayOf(4, 101)))
    check((message(frame(2, 6, byteArrayOf(0))) as TimemoreUnit).unit == 0)
    check((message(frame(1, 6, byteArrayOf(1))) as TimemoreUnit).unit == 1)
    check((message(frame(3, 6, byteArrayOf(1))) as TimemoreAck).let { it.command == 6 && it.success })
    check((message(frame(3, 13, byteArrayOf(0))) as TimemoreAck).let { it.command == 13 && !it.success })
    invalid(frame(2, 6, byteArrayOf(2))); invalid(frame(3, 6, byteArrayOf(2)))
    invalid(frame(3, 13, byteArrayOf()))
    val good = weight(123)
    invalid(good.copyOf().apply { this[6] = (this[6].toInt() xor 1).toByte() })
    invalid(good.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
    check(message(weight(123, zeroCrc = true)) is TimemoreWeight)
    invalid(frame(2, 6, byteArrayOf(0), true))
    invalid(frame(3, 6, byteArrayOf(1), true))
    invalid(frame(0x7F, 0x7F, byteArrayOf(), true))
    val unknown = frame(1, 0x7F, byteArrayOf(2))
    check(TimemoreCodec.decode(unknown) == DecodeResult.Unknown(ByteFrame(unknown)))
    invalid(good.copyOf(good.size - 1)); invalid(good + byteArrayOf(0))
    invalid(good.copyOf().apply { this[0] = 0 })
    invalid(ByteArray(257))

    val stream = TimemoreStreamDecoder()
    for (i in 0 until good.lastIndex) check(stream.feed(byteArrayOf(good[i])).isEmpty())
    val completed = stream.feed(byteArrayOf(good.last()))
    check(completed.size == 1 && (completed.single() as DecodeResult.Valid).value == message(good))
    check(stream.feed(byteArrayOf()).isEmpty()) // No cached weight replay.
    val glued = stream.feed(good + unknown + frame(2, 6, byteArrayOf(0)))
    check(glued.size == 3 && glued[1] is DecodeResult.Unknown)
    check((glued.last() as DecodeResult.Valid).value is TimemoreUnit)
    val resync = TimemoreStreamDecoder()
    check(resync.feed(ByteArray(4096) { 0x33 } + byteArrayOf(0xA5.toByte())).isEmpty())
    // Overlapping prefix and oversized length, then a valid notification.
    val recovered = resync.feed(hex("A55A0101FFFF") + good)
    check(recovered.count { it is DecodeResult.Invalid } == 1)
    check(recovered.filterIsInstance<DecodeResult.Valid<TimemoreMessage>>().map { it.value } == listOf(message(good)))
    val badThenGood = stream.feed(good.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() } + good)
    check(badThenGood.first() is DecodeResult.Invalid)
    check(badThenGood.filterIsInstance<DecodeResult.Valid<TimemoreMessage>>().size == 1)
    // Maximum-size unknown frame split at the cache boundary, followed by a new frame.
    val maximum = frame(2, 0x7F, ByteArray(248))
    check(resync.feed(maximum.copyOf(255)).isEmpty())
    check(resync.feed(maximum.copyOfRange(255, 256) + good).let {
        it.size == 2 && it[0] is DecodeResult.Unknown && (it[1] as DecodeResult.Valid).value is TimemoreWeight
    })
    check(resync.feed(byteArrayOf()).isEmpty())

    fun legacy(dripper: Int, scale: Int) = byteArrayOf(0x10) + le32(dripper) + le32(scale)
    val legacyBytes = legacy(-123456, 234567)
    val old = TimemoreLegacyCodec.decode(legacyBytes)
    check(old is DecodeResult.Valid && old.value.hundredthsGram == 2345670 &&
        old.value.secondaryHundredthsGram == -1234560 && old.value.raw == ByteFrame(legacyBytes))
    val oldNegative = TimemoreLegacyCodec.decode(legacy(123456, -234567))
    check(oldNegative is DecodeResult.Valid && oldNegative.value.hundredthsGram == -2345670 &&
        oldNegative.value.secondaryHundredthsGram == 1234560)
    check(TimemoreLegacyCodec.decode(legacy(Int.MAX_VALUE, 1)) is DecodeResult.Invalid)
    check(TimemoreLegacyCodec.decode(legacy(1, Int.MIN_VALUE)) is DecodeResult.Invalid)
    check(TimemoreLegacyCodec.decode(legacyBytes.copyOf(8)) is DecodeResult.Invalid)
    check(TimemoreLegacyCodec.decode(legacyBytes + byteArrayOf(0)) is DecodeResult.Invalid)
    check(TimemoreLegacyCodec.decode(legacyBytes.copyOf().apply { this[0] = 0 }) is DecodeResult.Invalid)
    println("PASS TIMEMORE CRC, signed weights, metadata/acks, bounded streaming and read-only legacy channels")
}
