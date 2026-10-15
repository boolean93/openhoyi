package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.math.BigDecimal
import java.util.UUID

/** Shareable parameters only. No shot, bean, private notes, telemetry or executable wire frame. */
data class CustomCurveDocument(
    val id: String,
    val name: String,
    val temperatureC: Int,
    val targetHundredthsGram: Int,
    val controlMode: ControlMode,
    val stages: List<Stage>,
) {
    enum class ControlMode { PRESSURE, FLOW_RAW }
    data class Stage(val target: Int, val waterTenthsMl: Int)
    fun validate() {
        require(id.startsWith("draft-") && runCatching { UUID.fromString(id.removePrefix("draft-")).toString() == id.removePrefix("draft-") }.getOrDefault(false)) { "Invalid draft ID" }
        require(name.isNotBlank() && name.length <= 100)
        // Friendly draft limits, narrower than the wire's unsigned 8/16-bit fields.
        require(temperatureC in 75..105)
        require(targetHundredthsGram in 0..20000)
        require(stages.size in 1..4)
        stages.forEach {
            require(it.target in 0..if (controlMode == ControlMode.PRESSURE) 120 else 255)
            require(it.waterTenthsMl in (if (controlMode == ControlMode.PRESSURE) 1 else 0)..65535)
        }
    }
    fun encode(): String { validate(); return toJson().toString() }
    internal fun toJson(): JSONObject {
        val array = JSONArray()
        stages.forEach { array.put(JSONObject().put("target", it.target).put("waterTenthsMl", it.waterTenthsMl)) }
        return JSONObject().put("schema", 1).put("kind", "openhoyi.local-curve").put("id", id).put("name", name)
            .put("temperatureC", temperatureC).put("targetHundredthsGram", targetHundredthsGram)
            .put("controlMode", if (controlMode == ControlMode.PRESSURE) "pressure" else "flow-raw").put("stages", array)
    }
    companion object {
        const val MAX_BYTES = 64 * 1024
        fun newId(): String = "draft-${UUID.randomUUID()}"
        fun fromLibraryItem(item: CurveLibraryItem, id: String = newId()): CustomCurveDocument {
            item.customDocument?.let { return it }
            val factory = item.factoryCurve
            val profile = item.controlProfile
            require(factory != null || profile != null)
            val parameters = profile?.parameters
            val targets = factory?.targets ?: requireNotNull(parameters).let { listOf(it.target1, it.target2, it.target3, it.target4) }
            val water = factory?.segmentFlowMl?.map { Math.multiplyExact(it, 10) }
                ?: requireNotNull(parameters).let { listOf(it.firstFlowTenths, it.secondFlowTenths, it.thirdFlowTenths, it.fourthFlowTenths) }
            val flow = factory?.variableFlowLogic ?: requireNotNull(parameters).let { it.variableFlowLogic || it.firstSegmentFlowMode }
            val count = factory?.segmentCount ?: requireNotNull(parameters).segmentCount
            return CustomCurveDocument(id, item.name, factory?.temperatureC ?: requireNotNull(profile).temperatureC,
                factory?.weightTenthsGram?.let { Math.multiplyExact(it, 10) } ?: requireNotNull(profile).targetHundredthsGram,
                if (flow) ControlMode.FLOW_RAW else ControlMode.PRESSURE,
                (0 until count).map { Stage(targets[it], water[it]) }).also { it.validate() }
        }
        fun decode(value: String): CustomCurveDocument {
            require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Curve document too large" }
            return fromJson(CurveDocumentJson.objectFrom(value))
        }
        internal fun fromJson(root: JSONObject): CustomCurveDocument {
            CurveDocumentJson.keys(root, setOf("schema", "kind", "id", "name", "temperatureC", "targetHundredthsGram", "controlMode", "stages"))
            require(CurveDocumentJson.number(root, "schema") == 1) { "Unsupported curve version" }
            require(CurveDocumentJson.string(root, "kind") == "openhoyi.local-curve")
            val mode = when (CurveDocumentJson.string(root, "controlMode")) {
                "pressure" -> ControlMode.PRESSURE
                "flow-raw" -> ControlMode.FLOW_RAW
                else -> error("Unsupported control mode")
            }
            val stages = root.getJSONArray("stages")
            require(stages.length() in 1..4)
            return CustomCurveDocument(CurveDocumentJson.string(root, "id"), CurveDocumentJson.string(root, "name"),
                CurveDocumentJson.number(root, "temperatureC"), CurveDocumentJson.number(root, "targetHundredthsGram"), mode,
                (0 until stages.length()).map {
                    val stage = stages.getJSONObject(it)
                    CurveDocumentJson.keys(stage, setOf("target", "waterTenthsMl"))
                    Stage(CurveDocumentJson.number(stage, "target"), CurveDocumentJson.number(stage, "waterTenthsMl"))
                }).also { it.validate() }
        }
    }
}

internal object CurveDocumentJson {
    fun objectFrom(value: String): JSONObject {
        val tokenizer = JSONTokener(value)
        val root = tokenizer.nextValue()
        require(root is JSONObject && tokenizer.nextClean() == '\u0000') { "Invalid/trailing curve data" }
        return root
    }
    fun keys(value: JSONObject, expected: Set<String>) { require(value.keys().asSequence().toSet() == expected) }
    fun string(value: JSONObject, key: String): String { val raw = value.get(key); require(raw is String); return raw }
    fun number(value: JSONObject, key: String): Int {
        val raw = value.get(key)
        require(raw is Int || raw is Long)
        val long = (raw as Number).toLong()
        require(long in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return long.toInt()
    }
    private fun canonical(value: Any?): String = when {
        value == null || value === JSONObject.NULL -> "z"
        value is JSONObject -> "{" + value.keys().asSequence().toList().sorted().joinToString("") { canonical(it) + canonical(value.get(it)) } + "}"
        value is JSONArray -> "[" + (0 until value.length()).joinToString("") { canonical(value.get(it)) } + "]"
        value is String -> "s${value.length}:$value"
        value is Number -> "n$value;"
        else -> error("Unsupported curve value")
    }
    fun checksum(array: JSONArray): String = MessageDigest.getInstance("SHA-256").digest(canonical(array).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Exact decimal input; no floating-point recipe values. */
internal object CurveDraftNumber {
    fun parse(value: String, decimalPlaces: Int): Int {
        require(decimalPlaces in 0..2)
        val fraction = if (decimalPlaces == 0) "" else "(?:\\.[0-9]{1,$decimalPlaces})?"
        val text = value.trim()
        require(Regex("[0-9]+$fraction").matches(text))
        return try { BigDecimal(text).movePointRight(decimalPlaces).intValueExact() }
        catch (e: ArithmeticException) { throw IllegalArgumentException("Draft number exceeds range", e) }
    }
    fun format(raw: Int, decimalPlaces: Int): String = BigDecimal.valueOf(raw.toLong(), decimalPlaces).stripTrailingZeros().toPlainString()
}
