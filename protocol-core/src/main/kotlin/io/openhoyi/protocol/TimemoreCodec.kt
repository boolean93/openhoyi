package io.openhoyi.protocol

sealed interface TimemoreMessage { val raw: ByteFrame }
data class TimemoreWeight(
    val hundredthsGram: Int, val flowHundredths: Int, val timerSeconds: Int,
    val overloaded: Boolean, override val raw: ByteFrame,
) : TimemoreMessage
data class TimemoreUnit(val unit: Int, override val raw: ByteFrame) : TimemoreMessage
data class TimemoreBattery(val percent: Int, override val raw: ByteFrame) : TimemoreMessage
data class TimemoreAck(val command: Int, val success: Boolean, override val raw: ByteFrame) : TimemoreMessage

/**
 * Independently implemented from TIMEMORE's Black Mirror SCALE BLE protocol v1.0.3:
 * https://github.com/TIMEMORE-COFFEE/open-scale-protocol/blob/main/Timemore%20Black%20Mirror%20SCALE%20BLE%20Protocol.md
 * CRC-16/IBM: init FFFF, reflected A001, no xorout, high byte first on the wire.
 */
object TimemoreCodec {
    // Total frame size, including the six-byte header and two-byte CRC. Known frames need at most 17.
    internal const val MAX_FRAME_SIZE = 256

    fun setGrams(): EncodedCommand = encode(0x03, 0x06, byteArrayOf(0))
    fun readUnit(): EncodedCommand = encode(0x02, 0x06, byteArrayOf())
    fun tare(): EncodedCommand = encode(0x03, 0x0D, byteArrayOf())

    fun decode(bytes: ByteArray): DecodeResult<TimemoreMessage> {
        val raw = ByteFrame(bytes)
        val b = raw.toByteArray()
        fun invalid(reason: String) = DecodeResult.Invalid(reason, raw)
        if (b.size !in 8..MAX_FRAME_SIZE) return invalid("Invalid TIMEMORE frame size")
        if (b.u(0) != 0xA5 || b.u(1) != 0x5A) return invalid("Invalid TIMEMORE header")
        val length = b.be16(4)
        if (length + 8 != b.size) return invalid("TIMEMORE payload length mismatch")
        val opcode = b.u(2)
        val command = b.u(3)
        val crc = b.be16(b.size - 2)
        // Vendor text reserves notify CRC; accepting zero is existing compatibility policy,
        // not hardware proof. Never exempt read/write responses or nonzero CRCs.
        if (!(opcode == 0x01 && crc == 0) && crc != timemoreCrc(b, b.size - 2)) {
            return invalid("TIMEMORE CRC mismatch")
        }
        return when {
            opcode == 0x01 && command == 0x01 -> {
                if (length != 9) return invalid("TIMEMORE weight payload must be nine bytes")
                val weight = tenthsToHundredths(b.be32(6))
                    ?: return invalid("TIMEMORE weight overflows hundredths")
                val flow = b.be16(10).toShort().toInt() * 10
                val timer = b.be16(12)
                if (timer > 3599 || b.u(14) !in 0..1) return invalid("Invalid TIMEMORE timer/overload")
                DecodeResult.Valid(TimemoreWeight(weight, flow, timer, b.u(14) == 1, raw))
            }
            opcode in 0x01..0x02 && command == 0x05 -> {
                if (length != 2 || b.u(7) > 100) return invalid("Invalid TIMEMORE battery payload")
                DecodeResult.Valid(TimemoreBattery(b.u(7), raw))
            }
            opcode in 0x01..0x02 && command == 0x06 -> {
                if (length != 1 || b.u(6) !in 0..1) return invalid("Invalid TIMEMORE unit payload")
                DecodeResult.Valid(TimemoreUnit(b.u(6), raw))
            }
            opcode == 0x03 && command in listOf(0x06, 0x0D) -> {
                if (length != 1 || b.u(6) !in 0..1) return invalid("Invalid TIMEMORE acknowledgement")
                DecodeResult.Valid(TimemoreAck(command, b.u(6) == 1, raw))
            }
            else -> DecodeResult.Unknown(raw)
        }
    }

    private fun encode(opcode: Int, command: Int, payload: ByteArray): EncodedCommand {
        val bytes = byteArrayOf(0xA5.toByte(), 0x5A, opcode.toByte(), command.toByte(),
            (payload.size shr 8).toByte(), payload.size.toByte()) + payload + byteArrayOf(0, 0)
        val crc = timemoreCrc(bytes, bytes.size - 2)
        bytes[bytes.size - 2] = (crc shr 8).toByte()
        bytes[bytes.size - 1] = crc.toByte()
        return EncodedCommand(ByteFrame(bytes))
    }
}

/** Per-connection decoder: 256-byte frame buffer plus fewer than 512 held boundary-candidate bytes. */
class TimemoreStreamDecoder {
    private val buffer = ByteArray(TimemoreCodec.MAX_FRAME_SIZE)
    private var size = 0
    private val candidate = NotificationBoundaryCandidate(0xA5, 0x5A, 6, { it.be16(4) + 8 }) { TimemoreCodec.decode(it) !is DecodeResult.Invalid }

    fun feed(bytes: ByteArray): List<DecodeResult<TimemoreMessage>> {
        val incoming = candidate.prepare(size > 0, bytes) ?: return emptyList()
        val results = mutableListOf<DecodeResult<TimemoreMessage>>()
        if (candidate.ambiguous) {
            size = 0
            return listOf(DecodeResult.Invalid("Ambiguous scale notification boundary", ByteFrame(incoming)))
        }
        when (notificationBoundary(buffer.copyOf(size), incoming, 6, 0xA5, 0x5A,
            { it.be16(4) + 8 }, TimemoreCodec::decode, "TIMEMORE CRC mismatch")) {
            NotificationBoundary.RESYNC -> size = 0
            NotificationBoundary.AMBIGUOUS -> {
                size = 0
                return listOf(DecodeResult.Invalid("Ambiguous scale notification boundary", ByteFrame(incoming)))
            }
            NotificationBoundary.CONTINUE -> Unit
        }
        for (byte in incoming) {
            buffer[size++] = byte
            while (size > 0) {
                if (buffer.u(0) != 0xA5) { discard(1); continue }
                if (size < 2) break
                if (buffer.u(1) != 0x5A) { discard(1); continue }
                if (size < 6) break
                val total = buffer.be16(4) + 8
                if (total > buffer.size) {
                    results += DecodeResult.Invalid("TIMEMORE frame exceeds 256 bytes", ByteFrame(buffer.copyOf(6)))
                    discard(1) // Keep scanning, including an overlapping A5 prefix.
                    continue
                }
                if (size < total) break
                val result = TimemoreCodec.decode(buffer.copyOf(total))
                results += result
                // Corrupt lengths/CRC may hide a later header. A valid/unknown frame is consumed exactly once.
                discard(if (result is DecodeResult.Invalid) 1 else total)
            }
        }
        return results
    }

    private fun discard(count: Int) {
        buffer.copyInto(buffer, 0, count, size)
        size -= count
    }
}

/** Read-only candidate: documented MIT gaggimate 0x10 + dripper int32LE + scale int32LE.
 * https://github.com/gaggimate/esp-arduino-ble-scales/blob/main/src/scales/timemore.cpp
 * Conflicting int16 interpretations are not mixed into this format; actual hardware remains unverified.
 */
data class TimemoreLegacySample(
    val hundredthsGram: Int, val secondaryHundredthsGram: Int, val raw: ByteFrame,
)
object TimemoreLegacyCodec {
    fun decode(bytes: ByteArray): DecodeResult<TimemoreLegacySample> {
        val raw = ByteFrame(bytes)
        val b = raw.toByteArray()
        if (b.size != 9 || b.u(0) != 0x10) return DecodeResult.Invalid("Invalid legacy TIMEMORE frame", raw)
        val primary = tenthsToHundredths(b.le32(5))
        val secondary = tenthsToHundredths(b.le32(1))
        if (primary == null || secondary == null) return DecodeResult.Invalid("Legacy TIMEMORE weight overflows hundredths", raw)
        return DecodeResult.Valid(TimemoreLegacySample(primary, secondary, raw))
    }
}

private fun ByteArray.u(i: Int): Int = this[i].toInt() and 255
private fun ByteArray.be16(i: Int): Int = (u(i) shl 8) or u(i + 1)
private fun ByteArray.be32(i: Int): Int = (u(i) shl 24) or (u(i + 1) shl 16) or (u(i + 2) shl 8) or u(i + 3)
private fun ByteArray.le32(i: Int): Int = (u(i + 3) shl 24) or (u(i + 2) shl 16) or (u(i + 1) shl 8) or u(i)
private fun tenthsToHundredths(value: Int): Int? {
    val scaled = value.toLong() * 10
    return if (scaled in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) scaled.toInt() else null
}
private fun timemoreCrc(bytes: ByteArray, count: Int): Int {
    var crc = 0xFFFF
    for (i in 0 until count) {
        crc = crc xor bytes.u(i)
        repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
    }
    return crc
}
