package io.openhoyi.protocol

/** Offline-only legacy scale formats. No Android transport or control path references this codec. */
enum class LegacyScaleFamily { ACAIA, FELICITA, DIFLUID }

/** A candidate reading is not authenticated telemetry and must not drive extraction control. */
data class LegacyScaleWeightCandidate(val weightGrams: Double, val raw: ByteFrame)

object LegacyScaleCandidateCodec {
    fun decode(family: LegacyScaleFamily, bytes: ByteArray): DecodeResult<LegacyScaleWeightCandidate> {
        val raw = ByteFrame(bytes)
        fun invalid(reason: String) = DecodeResult.Invalid(reason, raw)
        fun candidate(weight: Double) = DecodeResult.Valid(LegacyScaleWeightCandidate(weight, raw))
        fun u(index: Int) = bytes[index].toInt() and 0xFF
        return when (family) {
            LegacyScaleFamily.ACAIA -> {
                if (bytes.size < 2) return invalid("Truncated Acaia header")
                if (u(0) != 0xEF || u(1) != 0xDD) return DecodeResult.Unknown(raw)
                if (bytes.size < 3) return invalid("Truncated Acaia message")
                if (u(2) != 12) return DecodeResult.Unknown(raw)
                if (bytes.size < 5) return invalid("Truncated Acaia event")
                if (u(4) != 5) return DecodeResult.Unknown(raw)
                // Legacy Message(type=5) reads payload bytes 0,1,4,5 from frame offset 5.
                if (bytes.size < 11) return invalid("Truncated Acaia weight event")
                val divisor = when (u(9)) { 1 -> 10.0; 2 -> 100.0; 3 -> 1_000.0; 4 -> 10_000.0; else -> return DecodeResult.Unknown(raw) }
                val magnitude = (u(5) or (u(6) shl 8)) / divisor
                candidate(if (u(10) and 2 != 0) -magnitude else magnitude)
            }
            LegacyScaleFamily.FELICITA -> {
                // Old 2/4-byte extraction evidence only, never a full ASCII notification.
                if (bytes.size > 4) return DecodeResult.Unknown(raw)
                if (bytes.size < 2) return invalid("Truncated Felicita weight")
                candidate(((u(0) shl 8) or u(1)) / 100.0)
            }
            LegacyScaleFamily.DIFLUID -> {
                if (bytes.size < 2) return invalid("Truncated DiFluid header")
                if (u(0) != 0xDF || u(1) != 0xDF) return DecodeResult.Unknown(raw)
                if (bytes.size < 3) return invalid("Truncated DiFluid message")
                if (u(2) != 3) return DecodeResult.Unknown(raw)
                if (bytes.size < 9) return invalid("Truncated DiFluid type-3 weight")
                // The old App's type-3 path treats bit 7 at index 5 as an unknown sign case.
                if (u(5) and 0x80 != 0) return DecodeResult.Unknown(raw)
                val value = ((u(5).toLong() shl 24) or (u(6).toLong() shl 16) or
                    (u(7).toLong() shl 8) or u(8).toLong())
                val grams = value / 10.0
                if (grams > 6_000.0) DecodeResult.Unknown(raw) else candidate(grams)
            }
        }
    }
}
