package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import java.security.MessageDigest

/** Offline comparison only. A matching report never authorizes a BLE command. */
object LegacyCurveWireAudit {
    data class Report(val exportSha256: String, val curveCount: Int, val frameCount: Int)

    private const val ENCODER_SHA256 = "635ddbe7bbc63d8d74b5053cd5c2ca1d5e8a0dfab0f24447171e5043fd01855f"
    private val slots = listOf(7, 1, 2, 3, 4, 5)
    private val framePattern = Regex("02[0-9A-F]{38}")
    private val headerPattern = Regex(
        "# legacy-curve-wire-v1\\tsource-sha256=[a-f0-9]{64}\\tencoder-sha256=$ENCODER_SHA256\\texport-sha256=([a-f0-9]{64})")

    fun verify(export: ByteArray, proof: String): Report {
        val bundle = LegacyCurveCodec.parse(export)
        val hash = MessageDigest.getInstance("SHA-256").digest(export)
            .joinToString("") { "%02x".format(it) }
        val lines = proof.split('\n').filter { it.isNotEmpty() }
        require(headerPattern.matchEntire(lines.firstOrNull().orEmpty())?.groupValues?.get(1) == hash) {
            "Legacy curve proof header or export hash mismatch"
        }
        val active = bundle.curves.filter { it.rawJson != "null" }
        require(lines.size == 1 + active.size * slots.size) { "Incomplete legacy curve proof" }
        var lineIndex = 1
        for (curve in active) for (slot in slots) {
            val fields = lines[lineIndex++].split('\t')
            require(fields.size == 4 && fields[0] == curve.index.toString() && fields[1] == slot.toString() &&
                fields[2].matches(framePattern) && fields[3].matches(framePattern)) {
                "Invalid legacy curve proof row at index ${curve.index}, slot $slot"
            }
            for (scale in listOf(false, true)) {
                val profile = LegacyCurveAdapter.profile(curve, scale, slot)
                    ?: throw IllegalArgumentException("Legacy curve cannot be converted at index ${curve.index}, slot $slot")
                val nativeFrame = CoffeeCommands.start(profile.parameters).frame.hex()
                require(nativeFrame == fields[if (scale) 3 else 2]) {
                    "Legacy curve frame mismatch at index ${curve.index}, slot $slot, scale=$scale"
                }
            }
        }
        return Report(hash, active.size, active.size * slots.size * 2)
    }
}
