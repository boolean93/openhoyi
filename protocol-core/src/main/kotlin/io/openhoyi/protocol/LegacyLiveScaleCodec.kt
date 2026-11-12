package io.openhoyi.protocol

/** Wire layouts independently implemented from the old HOYI protocol evidence and MIT references:
 * https://github.com/gaggimate/esp-arduino-ble-scales/blob/main/src/scales/acaia.cpp
 * https://github.com/gaggimate/esp-arduino-ble-scales/blob/main/src/scales/difluid.cpp
 * Protocol support is not device acceptance or permission to use weight for automatic control.
 */
enum class AcaiaVariant { A, AP }
sealed interface AcaiaMessage { val raw: ByteFrame }
data class AcaiaWeight(
    val rawMagnitude: Int, val decimalPlaces: Int, val negative: Boolean, override val raw: ByteFrame,
) : AcaiaMessage {
    /** Hundredths of the currently selected unit, NOT grams until a status confirms unit 2.
     * Round absolute magnitude half-up before applying the sign, including sub-hundredth readings.
     */
    val hundredths: Int get() {
        val magnitude = when (decimalPlaces) {
            1 -> rawMagnitude * 10
            2 -> rawMagnitude
            3 -> (rawMagnitude + 5) / 10
            4 -> (rawMagnitude + 50) / 100
            else -> error("Unsupported Acaia decimal places")
        }
        return if (negative) -magnitude else magnitude
    }
}
/** Raw status unit: 2 grams, 5 ounces. Preserve every other value to revoke prior unit trust. */
data class AcaiaUnit(val unit: Int, override val raw: ByteFrame) : AcaiaMessage

object AcaiaCodec {
    fun identify(variant: AcaiaVariant): EncodedCommand = command(0x0B,
        if (variant == AcaiaVariant.A) ByteArray(15) { 0x2D }
        else "012345678901234".toByteArray(Charsets.US_ASCII))
    fun subscribe(variant: AcaiaVariant): EncodedCommand = command(0x0C,
        byteArrayOf(9, 0, 1, 1, 2, 2, if (variant == AcaiaVariant.A) 5 else 1, 3, 4))
    fun heartbeat(): EncodedCommand = command(0, byteArrayOf(2, 0))
    /** Only the legacy A adapter exposes this old-HOYI command; AP tare is not inferred. */
    fun tare(): EncodedCommand = EncodedCommand(ByteFrame(
        byteArrayOf(0xEF.toByte(), 0xDD.toByte(), 4) + ByteArray(15) + byteArrayOf(4, 0)))

    fun decode(bytes: ByteArray): DecodeResult<AcaiaMessage> {
        val raw = ByteFrame(bytes)
        val b = raw.toByteArray()
        fun invalid(reason: String) = DecodeResult.Invalid(reason, raw)
        if (b.size !in 6..LIVE_FRAME_LIMIT || b.liveU(0) != 0xEF || b.liveU(1) != 0xDD)
            return invalid("Invalid Acaia header/size")
        val length = b.liveU(3)
        if (length < 1 || length + 5 != b.size) return invalid("Invalid Acaia payload length")
        val checksum = acaiaChecksum(b.copyOfRange(3, b.size - 2))
        if (b.liveU(b.size - 2) != checksum.first || b.liveU(b.size - 1) != checksum.second)
            return invalid("Acaia checksum mismatch")
        return when (b.liveU(2)) {
            0x08 -> {
                if (length < 3) return invalid("Truncated Acaia status")
                DecodeResult.Valid(AcaiaUnit(b.liveU(5), raw))
            }
            0x0C -> {
                if (length < 2) return invalid("Truncated Acaia event")
                // Public MIT aioacaia record-chain evidence; no SDK binary implementation.
                // Validate every known record before exposing any weight from this frame.
                var cursor = 4
                val end = b.size - 2
                var weight: AcaiaWeight? = null
                while (cursor < end) {
                    val tag = b.liveU(cursor++)
                    val width = when (tag) {
                        5 -> 6 // weight
                        6, 8 -> 1 // battery, button
                        7 -> 3 // timer
                        11 -> 2 // acknowledgement
                        else -> return DecodeResult.Unknown(raw) // Never guess an unknown boundary.
                    }
                    if (end - cursor < width) return invalid("Truncated Acaia event record")
                    if (tag == 5) {
                        // Public evidence does not establish nonzero high-byte semantics. Never discard them.
                        if (b.liveU(cursor + 2) != 0 || b.liveU(cursor + 3) != 0)
                            return invalid("Unsupported Acaia weight magnitude layout")
                        val decimal = b.liveU(cursor + 4)
                        if (decimal !in 1..4) return invalid("Invalid Acaia decimal places")
                        weight = AcaiaWeight(b.liveU(cursor) or (b.liveU(cursor + 1) shl 8), decimal,
                            b.liveU(cursor + 5) and 2 != 0, raw)
                    }
                    cursor += width
                }
                weight?.let { DecodeResult.Valid(it) } ?: DecodeResult.Unknown(raw)
            }
            else -> DecodeResult.Unknown(raw)
        }
    }

    private fun command(opcode: Int, payload: ByteArray): EncodedCommand {
        val sums = acaiaChecksum(payload)
        return EncodedCommand(ByteFrame(byteArrayOf(0xEF.toByte(), 0xDD.toByte(), opcode.toByte()) +
            payload + byteArrayOf(sums.first.toByte(), sums.second.toByte())))
    }
}

class AcaiaStreamDecoder {
    private val stream = LegacyLiveFrameStream<AcaiaMessage>(0xEF, 0xDD, 4,
        { it.liveU(3).takeIf { length -> length > 0 }?.plus(5) ?: 0 }, AcaiaCodec::decode,
        checksumError = "Acaia checksum mismatch")
    fun feed(bytes: ByteArray): List<DecodeResult<AcaiaMessage>> = stream.feed(bytes)
}

sealed interface DiFluidMessage { val raw: ByteFrame }
/** Official v1.0.0 protocol (Microbalance V300+, Ti V010+):
 * https://github.com/DiFluid/difluid-sdk-demo/blob/master/docs/protocolMicrobalance.md
 * Sensor data embeds its own unit at Data12 (frame offset 17). Only unit 0 emits a gram weight;
 * ounces/grains/unknown units emit DiFluidUnit instead, revoking gram readiness without guessing.
 */
data class DiFluidWeight(val rawTenths: Int, val hundredths: Int, override val raw: ByteFrame) : DiFluidMessage
/** Raw fixed-field weight without a unit; never inherently a gram reading. */
data class DiFluidUnitlessWeight(val rawTenths:Int,val hundredths:Int,override val raw:ByteFrame):DiFluidMessage
/** Raw unit: 0 grams, 1 ounces, 2 grains. Unknown values revoke trust. */
data class DiFluidUnit(val unit: Int, override val raw: ByteFrame) : DiFluidMessage
object DiFluidCodec {
    fun subscribe(): EncodedCommand = command(1, 0, byteArrayOf(1))
    fun setGrams(): EncodedCommand = command(1, 4, byteArrayOf(0))
    fun readUnit(): EncodedCommand = command(1, 4, byteArrayOf())
    fun tare(): EncodedCommand = command(3, 2, byteArrayOf(1))

    fun decode(bytes: ByteArray): DecodeResult<DiFluidMessage> {
        val raw = ByteFrame(bytes)
        val b = raw.toByteArray()
        fun invalid(reason: String) = DecodeResult.Invalid(reason, raw)
        if (b.size !in 6..LIVE_FRAME_LIMIT || b.liveU(0) != 0xDF || b.liveU(1) != 0xDF)
            return invalid("Invalid DiFluid header/size")
        val length = b.liveU(4)
        if (length + 6 != b.size) return invalid("DiFluid payload length mismatch")
        if (b.dropLast(1).sumOf { it.toInt() and 255 } and 255 != b.liveU(b.lastIndex))
            return invalid("DiFluid checksum mismatch")
        if (b.liveU(2) == 1 && b.liveU(3) == 4) {
            if (length != 1) return invalid("Invalid DiFluid unit response length")
            return DecodeResult.Valid(DiFluidUnit(b.liveU(5), raw))
        }
        if (b.liveU(2) != 3 || b.liveU(3) != 0) return DecodeResult.Unknown(raw)
        if (length !in 12..13) return invalid("DiFluid sensor data must have twelve or thirteen bytes")
        if (length == 13 && b.liveU(17) != 0) return DecodeResult.Valid(DiFluidUnit(b.liveU(17), raw))
        val value = (b.liveU(5) shl 24) or (b.liveU(6) shl 16) or (b.liveU(7) shl 8) or b.liveU(8)
        val scaled = value.toLong() * 10
        if (scaled !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
            return invalid("DiFluid sensor value overflows hundredths")
        return if(length==12) DecodeResult.Valid(DiFluidUnitlessWeight(value,scaled.toInt(),raw))
        else DecodeResult.Valid(DiFluidWeight(value, scaled.toInt(), raw))
    }

    private fun command(function: Int, command: Int, payload: ByteArray): EncodedCommand {
        val body = byteArrayOf(0xDF.toByte(), 0xDF.toByte(), function.toByte(), command.toByte(), payload.size.toByte()) + payload
        return EncodedCommand(ByteFrame(body + byteArrayOf((body.sumOf { it.toInt() and 255 } and 255).toByte())))
    }
}
class DiFluidStreamDecoder(onBoundaryResync:()->Unit = {}) {
    private val stream = LegacyLiveFrameStream<DiFluidMessage>(0xDF, 0xDF, 5,
        { it.liveU(4) + 6 }, DiFluidCodec::decode, boundaryResync=true, onBoundaryResync=onBoundaryResync)
    fun feed(bytes: ByteArray): List<DecodeResult<DiFluidMessage>> = stream.feed(bytes)
}
object FelicitaLiveCodec {
    fun tare(): EncodedCommand = EncodedCommand(ByteFrame(byteArrayOf(0x54)))
}

// A fixed total-frame bound, not a growing concatenation buffer. Each connection owns its decoder.
private const val LIVE_FRAME_LIMIT = 256
private fun ByteArray.liveU(index: Int): Int = this[index].toInt() and 255
private fun acaiaChecksum(payload: ByteArray): Pair<Int, Int> {
    var even = 0
    var odd = 0
    payload.forEachIndexed { index, value ->
        if (index % 2 == 0) even += value.toInt() and 255 else odd += value.toInt() and 255
    }
    return (even and 255) to (odd and 255)
}
private class LegacyLiveFrameStream<T>(
    private val first: Int, private val second: Int, private val prefixSize: Int,
    private val totalSize: (ByteArray) -> Int, private val decode: (ByteArray) -> DecodeResult<T>,
    private val checksumError: String? = null,
    private val boundaryResync:Boolean = false,
    private val onBoundaryResync:()->Unit = {},
) {
    private val buffer = ByteArray(LIVE_FRAME_LIMIT)
    private var size = 0
    private val candidate = NotificationBoundaryCandidate(first, second, prefixSize, totalSize) { decode(it) !is DecodeResult.Invalid }
    fun feed(bytes: ByteArray): List<DecodeResult<T>> {
        val incoming = if (checksumError != null) candidate.prepare(size > 0, bytes) ?: return emptyList() else bytes
        val results = mutableListOf<DecodeResult<T>>()
        if (checksumError != null && candidate.ambiguous) {
            size = 0
            return listOf(DecodeResult.Invalid("Ambiguous scale notification boundary", ByteFrame(incoming)))
        }
        if (checksumError != null) when (notificationBoundary(buffer.copyOf(size), incoming,
            prefixSize, first, second, totalSize, decode, checksumError)) {
            NotificationBoundary.RESYNC -> size = 0
            NotificationBoundary.AMBIGUOUS -> {
                size = 0
                return listOf(DecodeResult.Invalid("Ambiguous scale notification boundary", ByteFrame(incoming)))
            }
            NotificationBoundary.CONTINUE -> Unit
        }
        // Some DFDF devices abandon a partial frame after command responses and restart
        // the next notification with a complete frame. Do not synthesize a corrupt frame
        // by joining those two independent records. Prefer a valid continuation, however:
        // header bytes can legally occur in a split payload.
        if(boundaryResync && size>=prefixSize && bytes.size>=prefixSize &&
            bytes.liveU(0)==first && bytes.liveU(1)==second) {
            val pendingTotal=totalSize(buffer)
            val newTotal=totalSize(bytes)
            if(pendingTotal in prefixSize+1..LIVE_FRAME_LIMIT && size<pendingTotal &&
                size+bytes.size>=pendingTotal && newTotal in prefixSize+1..LIVE_FRAME_LIMIT && bytes.size>=newTotal) {
                val joined=buffer.copyOf(size)+bytes.copyOf(pendingTotal-size)
                // Only a validated boundary frame can replace a checksum-invalid join.
                // Malformed complete frames and unit messages still go through normal decode.
                val joinResult=decode(joined)
                if(joinResult is DecodeResult.Invalid && joinResult.reason=="DiFluid checksum mismatch" &&
                    decode(bytes.copyOf(newTotal)) !is DecodeResult.Invalid) {
                    size=0
                    onBoundaryResync()
                }
            }
        }
        for (byte in incoming) {
            buffer[size++] = byte
            while (size > 0) {
                if (buffer.liveU(0) != first) { discard(1); continue }
                if (size < 2) break
                if (buffer.liveU(1) != second) { discard(1); continue }
                if (size < prefixSize) break
                val total = totalSize(buffer)
                if (total !in prefixSize + 1..LIVE_FRAME_LIMIT) {
                    results += DecodeResult.Invalid("Legacy live scale length exceeds frame bounds", ByteFrame(buffer.copyOf(prefixSize)))
                    discard(1)
                    continue
                }
                if (size < total) break
                val result = decode(buffer.copyOf(total))
                results += result
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
