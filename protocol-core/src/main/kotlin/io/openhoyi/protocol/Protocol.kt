package io.openhoyi.protocol

/** Value semantics and defensive copies, including at the transport boundary. */
class ByteFrame(bytes: ByteArray, private val sensitive: Boolean = false) {
    private val bytes = bytes.copyOf()
    val size: Int get() = bytes.size
    fun toByteArray(): ByteArray = bytes.copyOf()
    /** Explicit wire export; callers must never log authentication frames. */
    fun hex(): String = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
    override fun toString(): String = if (sensitive) "ByteFrame([REDACTED], size=$size)" else "ByteFrame(${hex()})"
    override fun equals(other: Any?): Boolean = other is ByteFrame && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
}
sealed interface DecodeResult<out T> {
    data class Valid<T>(val value: T) : DecodeResult<T>
    data class Unknown(val raw: ByteFrame) : DecodeResult<Nothing>
    data class Invalid(val reason: String, val raw: ByteFrame) : DecodeResult<Nothing>
}
sealed interface HoyiMessage { val raw: ByteFrame }
data class Settings(
    val firmwareMajor: Int, val firmwareMinor: Int, val firmwarePatch: Int, val flags: Int,
    val brewTemperatureC: Int, val brewCompensationTenthsC: Int, val steamTemperatureC: Int,
    val standbyMinutes: Int, val standbyTemperatureC: Int, val cupCount: Int, val filterFlags: Int,
    override val raw: ByteFrame,
) : HoyiMessage {
    val firmwareInteger: Int get() = (firmwareMajor shl 8) or (firmwareMinor shl 4) or firmwarePatch
    val brewHeating: Boolean get() = flags and 32 != 0
    val steamHeating: Boolean get() = flags and 16 != 0
    val filterInstalled: Boolean? get() = if (firmwareInteger >= 276) filterFlags and 2 != 0 else null
}
data class IdleTelemetry(
    val brewTemperatureHundredthsC: Int, val steamTemperatureHundredthsC: Int,
    val brewPressureTenthsBar: Int, val steamPressureTenthsBar: Int, val sleepStateRaw: Int,
    val alarmBits: Int, val cupCount: Int, val extraSensorRaw: Int, override val raw: ByteFrame,
) : HoyiMessage
/** Ambiguous fields retain wire names/bits; session inference belongs above this layer. */
data class ExtractionTelemetry(
    val slotOrPhase: Int, val elapsedSeconds: Int, val pressureTenthsBar: Int,
    val totalWaterTenthsMl: Int, val brewTemperatureHundredthsC: Int, val flowTenthsMlPerSecond: Int,
    val statusBits: Int, val manualStageRaw: Int, override val raw: ByteFrame,
) : HoyiMessage {
    val valveOpen: Boolean get() = statusBits and 64 != 0
    val brewWait: Boolean get() = statusBits and 32 != 0
}
data class SleepDay(val sleepHour: Int, val sleepMinute: Int, val wakeHour: Int, val wakeMinute: Int)
class SleepPart(val firstDaySundayIndex: Int, val enabledBits: Int?, days: List<SleepDay>, override val raw: ByteFrame) : HoyiMessage {
    val days: List<SleepDay> = java.util.Collections.unmodifiableList(days.toList())
}
object HoyiCodec {
    fun decode(bytes: ByteArray): DecodeResult<HoyiMessage> {
        val raw=ByteFrame(bytes); val b=raw.toByteArray()
        fun invalid()=DecodeResult.Invalid("Unexpected HOYI notification length",raw)
        if (b.isEmpty()) return invalid()
        fun u(i:Int)=b[i].toInt() and 255
        fun word(i:Int)=(u(i) shl 8) or u(i+1)
        return when(u(0)) {
            0x83 -> {
                if(b.size<2) return invalid()
                when {
                    u(1)<0x40 -> {
                        if(b.size!=13) return invalid()
                        DecodeResult.Valid(Settings(u(1),u(2) shr 4,u(2) and 15,u(3),u(4),u(5),u(6),u(7),u(8),word(9),u(11),raw))
                    }
                    u(1)==0x40 || u(1)==0x80 -> {
                        val first=u(1)==0x40
                        if(b.size != if(first) 20 else 15) return invalid()
                        val offset=if(first)3 else 2
                        val days=(0 until if(first)4 else 3).map { val i=offset+it*4; SleepDay(u(i),u(i+1),u(i+2),u(i+3)) }
                        DecodeResult.Valid(SleepPart(if(first)0 else 4,if(first)u(2) else null,days,raw))
                    }
                    else -> DecodeResult.Unknown(raw)
                }
            }
            0x40 -> {
                if(b.size!=19) return invalid()
                DecodeResult.Valid(IdleTelemetry(word(2),word(4),u(6),u(7),u(8),word(12),word(14),word(16),raw))
            }
            0x80 -> {
                if(b.size!=13) return invalid()
                DecodeResult.Valid(ExtractionTelemetry(u(2),word(3),u(5),word(6),word(8),u(10),u(11),u(12),raw))
            }
            else -> DecodeResult.Unknown(raw)
        }
    }
}
data class BookooSample(val weightHundredthsGram: Int, val deviceFlowHundredths: Int,
    val weightSignRaw: Int, val flowSignRaw: Int, val raw: ByteFrame)
data class TimedCommand(val delayMs: Long, val frame: ByteFrame)
class EncodedCommand(val frame: ByteFrame) { override fun toString(): String = "EncodedCommand($frame)" }
object BookooCodec {
    fun decode(bytes: ByteArray): DecodeResult<BookooSample> {
        val raw=ByteFrame(bytes); val b=raw.toByteArray()
        fun invalid(reason:String)=DecodeResult.Invalid(reason,raw)
        if(b.isEmpty() || (b.size==1 && b[0].toInt()==3)) return invalid("Truncated BOOKOO frame")
        if(b.size<2 || b[0].toInt()!=3 || b[1].toInt()!=11) return DecodeResult.Unknown(raw)
        if(b.size!=20) return invalid("BOOKOO notification must be 20 bytes")
        fun u(i:Int)=b[i].toInt() and 255
        if(b.fold(0){a,v -> a xor (v.toInt() and 255)}!=0) return invalid("BOOKOO XOR mismatch")
        // Observed sign bytes are ASCII +/-. Legacy binary sign semantics are not assumed.
        if(u(6) !in listOf(43,45) || u(10) !in listOf(43,45)) return invalid("Unverified BOOKOO sign encoding")
        val weight=((u(7) shl 16) or (u(8) shl 8) or u(9)) * if(u(6)==45)-1 else 1
        val flow=((u(11) shl 8) or u(12)) * if(u(10)==45)-1 else 1
        return DecodeResult.Valid(BookooSample(weight,flow,u(6),u(10),raw))
    }
    fun initializationCommands(): List<TimedCommand> = listOf("030A02000308","030A0300141E","030A0700000E","030A08010000").mapIndexed { i,h -> TimedCommand((i+1)*500L,frameHex(h)) }
    fun tare(): EncodedCommand = EncodedCommand(frameHex("030A01000008"))
}
internal fun frameHex(hex:String)=ByteFrame(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
