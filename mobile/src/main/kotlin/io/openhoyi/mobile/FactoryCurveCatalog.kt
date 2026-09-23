package io.openhoyi.mobile

import java.io.InputStream
import java.util.Base64

/** Factory-v3 metadata copied from the legacy local bundle after its flow normalization. Read-only. */
data class FactoryCurve(
    val id: String,
    val category: String,
    val name: String,
    val tips: String,
    val iconPath: String,
    val flowMl: Int,
    val weightTenthsGram: Int,
    val weightCompensationTenthsGram: Int,
    val preinfusionSeconds: Int,
    val temperatureC: Int,
    val pressureLogic: Boolean,
    val variableFlowLogic: Boolean,
    val segmentCount: Int,
    val targets: List<Int>,
    val segmentFlowMl: List<Int>,
    val firstDurationSeconds: Int,
)

object FactoryCurveCatalog {
    const val VERSION = 3
    const val SOURCE_SHA256 = "b55c8b125d272fcb04e81a9b968dfa193202d94bbd1f1f245bacb33896164037"
    private val categories = setOf("dark", "medium", "light", "super")
    fun load(input: InputStream): List<FactoryCurve> {
        val lines = input.bufferedReader(Charsets.UTF_8).use { it.readLines() }
        require(lines.firstOrNull() == "# factory-v3\tsource-sha256=$SOURCE_SHA256")
        val curves = lines.drop(1).mapIndexed { index, line ->
            val fields = line.split('\t')
            require(fields.size == 22) { "factory curve field count at $index" }
            val id = "factory-v3-${(index + 1).toString().padStart(3, '0')}"
            require(fields[0] == id && fields[1] in categories) { "factory curve id/category at $index" }
            fun decode(at: Int) = String(Base64.getUrlDecoder().decode(fields[at]), Charsets.UTF_8)
            fun number(at: Int) = fields[at].toInt()
            fun flag(at: Int): Boolean { require(fields[at] == "0" || fields[at] == "1"); return fields[at] == "1" }
            FactoryCurve(id, fields[1], decode(2), decode(3), decode(4),
                number(5), number(6), number(7), number(8), number(9), flag(10), flag(11), number(12),
                listOf(number(13), number(16), number(18), number(20)),
                listOf(number(14), number(17), number(19), number(21)), number(15))
        }
        require(curves.size == 100)
        require(categories.all { category -> curves.count { it.category == category } == 25 })
        require(curves.all { it.name.isNotBlank() && it.segmentCount in 1..4 && it.temperatureC in 0..255 &&
            it.flowMl in 0..65535 && it.segmentFlowMl.take(it.segmentCount).sum() == it.flowMl })
        return java.util.Collections.unmodifiableList(curves)
    }
}
