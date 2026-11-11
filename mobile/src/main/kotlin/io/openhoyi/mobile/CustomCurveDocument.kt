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
    /** Null retains schema-1 automatic stage total; a value is the existing base water ceiling. */
    val totalWaterLimitMl: Int? = null,
    /** Schema 3 carries the complete recipe, never an executable frame or source proof. */
    val configuration: Configuration? = null,
) {
    enum class ControlMode { PRESSURE, FLOW, FLOW_RAW }
    data class Stage(val target: Int, val waterTenthsMl: Int)
    data class Configuration(
        val pressureLogic: Boolean = false,
        val variableFlowLogic: Boolean = false,
        val firstSegmentFlowMode: Boolean = false,
        val preinfusionSeconds: Int = 0,
        val firstDurationSeconds: Int = 0,
        val compensationHundredthsGram: Int = 0,
        val inactiveStages: List<Stage> = emptyList(),
    )
    val isEditable: Boolean get() = controlMode != ControlMode.FLOW_RAW
    fun isFlowStage(index: Int): Boolean = configuration?.let {
        it.variableFlowLogic && (index > 0 || it.firstSegmentFlowMode)
    } == true
    val allStages: List<Stage> get() = stages + (configuration?.inactiveStages ?: List(4 - stages.size) { Stage(0, 0) })
    fun validate() {
        require(id.startsWith("draft-") && runCatching { UUID.fromString(id.removePrefix("draft-")).toString() == id.removePrefix("draft-") }.getOrDefault(false)) { "Invalid draft ID" }
        require(name.isNotBlank() && name.length <= 100)
        // Friendly draft limits, narrower than the wire's unsigned 8/16-bit fields.
        require(temperatureC in 75..105)
        require(targetHundredthsGram in 0..20000)
        require(stages.size in 1..4)
        require(controlMode != ControlMode.FLOW || configuration != null)
        configuration?.let { c ->
            require(controlMode != ControlMode.FLOW_RAW)
            require((controlMode == ControlMode.FLOW) == c.variableFlowLogic)
            require(!c.firstSegmentFlowMode || c.variableFlowLogic)
            require(c.preinfusionSeconds in 0..127 && c.firstDurationSeconds in 0..255)
            require(c.compensationHundredthsGram in -10000..10000)
            require(c.inactiveStages.size == 4 - stages.size)
            c.inactiveStages.forEach { require(it.target in 0..255 && it.waterTenthsMl in 0..65535) }
        }
        stages.forEachIndexed { index, stage ->
            val targetRange = when {
                controlMode == ControlMode.FLOW_RAW -> 0..255
                isFlowStage(index) -> 10..100
                controlMode == ControlMode.FLOW -> 5..100
                else -> 0..120
            }
            require(stage.target in targetRange)
            require(stage.waterTenthsMl in (if (controlMode == ControlMode.PRESSURE && configuration == null) 1 else 0)..65535)
            if (controlMode == ControlMode.FLOW) require(stage.waterTenthsMl <= 9990)
        }
        if (controlMode == ControlMode.FLOW) {
            require(stages.size in 2..4 && requireNotNull(configuration).firstDurationSeconds <= 254)
            require(stages.first().waterTenthsMl > 0 || configuration.firstDurationSeconds > 0)
            require(stages.drop(1).all { it.waterTenthsMl > 0 })
        }
        totalWaterLimitMl?.let {
            if (isEditable) {
                if (it !in 1..maximumSupportedWaterLimitMl)
                    throw CurveWaterLimitException(maximumSupportedWaterLimitMl)
            } else require(it in 1..65535)
        }
    }
    val assignedWaterTenthsMl: Int get() = stages.sumOf { it.waterTenthsMl }
    val maximumSupportedWaterLimitMl: Int get() = maxOf((assignedWaterTenthsMl + 9) / 10, 800)
    fun encode(): String { validate(); return toJson().toString() }
    internal fun toJson(): JSONObject {
        val array = JSONArray()
        stages.forEach { array.put(JSONObject().put("target", it.target).put("waterTenthsMl", it.waterTenthsMl)) }
        return JSONObject().put("schema", if (configuration != null) 3 else if (totalWaterLimitMl == null) 1 else 2).put("kind", "openhoyi.local-curve").put("id", id).put("name", name)
            .put("temperatureC", temperatureC).put("targetHundredthsGram", targetHundredthsGram)
            .put("controlMode", when (controlMode) { ControlMode.PRESSURE -> "pressure"; ControlMode.FLOW -> "flow"; ControlMode.FLOW_RAW -> "flow-raw" }).put("stages", array)
            .also { json ->
                if (configuration != null) {
                    val c = configuration
                    json.put("totalWaterLimitMl", totalWaterLimitMl ?: JSONObject.NULL)
                    json.put("configuration", JSONObject().put("pressureLogic", c.pressureLogic)
                        .put("variableFlowLogic", c.variableFlowLogic).put("firstSegmentFlowMode", c.firstSegmentFlowMode)
                        .put("preinfusionSeconds", c.preinfusionSeconds).put("firstDurationSeconds", c.firstDurationSeconds)
                        .put("compensationHundredthsGram", c.compensationHundredthsGram)
                        .put("inactiveStages", JSONArray().also { tails -> c.inactiveStages.forEach {
                            tails.put(JSONObject().put("target", it.target).put("waterTenthsMl", it.waterTenthsMl))
                        } }))
                } else totalWaterLimitMl?.let { json.put("totalWaterLimitMl", it) }
            }
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
            val flow = factory?.variableFlowLogic ?: requireNotNull(parameters).variableFlowLogic
            val count = factory?.segmentCount ?: requireNotNull(parameters).segmentCount
            return CustomCurveDocument(id, item.name, factory?.temperatureC ?: requireNotNull(profile).temperatureC,
                factory?.weightTenthsGram?.let { Math.multiplyExact(it, 10) } ?: requireNotNull(profile).targetHundredthsGram,
                if (flow) ControlMode.FLOW else ControlMode.PRESSURE,
                (0 until count).map { Stage(targets[it], water[it]) },
                factory?.flowMl ?: requireNotNull(parameters).maximumWaterMl,
                Configuration(factory?.pressureLogic ?: requireNotNull(parameters).pressureLogic, flow,
                    parameters?.firstSegmentFlowMode ?: false,
                    factory?.preinfusionSeconds ?: requireNotNull(parameters).preinfusionSeconds,
                    factory?.firstDurationSeconds ?: requireNotNull(parameters).firstDurationSeconds,
                    factory?.weightCompensationTenthsGram?.let { Math.multiplyExact(it, 10) } ?: requireNotNull(profile).compensationHundredthsGram,
                    (count until 4).map { Stage(targets[it], water[it]) })).also { it.validate() }
        }
        fun decode(value: String): CustomCurveDocument {
            require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Curve document too large" }
            return fromJson(CurveDocumentJson.objectFrom(value))
        }
        internal fun fromJson(root: JSONObject): CustomCurveDocument {
            val schema = CurveDocumentJson.number(root, "schema")
            require(schema in 1..3) { "Unsupported curve version" }
            val keys = setOf("schema", "kind", "id", "name", "temperatureC", "targetHundredthsGram", "controlMode", "stages")
            CurveDocumentJson.keys(root, when(schema) { 1 -> keys; 2 -> keys + "totalWaterLimitMl"; else -> keys + setOf("totalWaterLimitMl", "configuration") })
            require(CurveDocumentJson.string(root, "kind") == "openhoyi.local-curve")
            val mode = when (CurveDocumentJson.string(root, "controlMode")) {
                "pressure" -> ControlMode.PRESSURE
                "flow" -> { require(schema == 3); ControlMode.FLOW }
                "flow-raw" -> ControlMode.FLOW_RAW
                else -> error("Unsupported control mode")
            }
            val stages = root.getJSONArray("stages")
            require(stages.length() in 1..4)
            fun stage(stage: JSONObject): Stage {
                CurveDocumentJson.keys(stage, setOf("target", "waterTenthsMl"))
                return Stage(CurveDocumentJson.number(stage, "target"), CurveDocumentJson.number(stage, "waterTenthsMl"))
            }
            val configuration = if (schema == 3) root.getJSONObject("configuration").let { c ->
                CurveDocumentJson.keys(c, setOf("pressureLogic", "variableFlowLogic", "firstSegmentFlowMode", "preinfusionSeconds", "firstDurationSeconds", "compensationHundredthsGram", "inactiveStages"))
                val tails = c.getJSONArray("inactiveStages")
                require(tails.length() == 4 - stages.length())
                Configuration(CurveDocumentJson.boolean(c, "pressureLogic"), CurveDocumentJson.boolean(c, "variableFlowLogic"),
                    CurveDocumentJson.boolean(c, "firstSegmentFlowMode"), CurveDocumentJson.number(c, "preinfusionSeconds"),
                    CurveDocumentJson.number(c, "firstDurationSeconds"), CurveDocumentJson.number(c, "compensationHundredthsGram"),
                    (0 until tails.length()).map { stage(tails.getJSONObject(it)) })
            } else null
            return CustomCurveDocument(CurveDocumentJson.string(root, "id"), CurveDocumentJson.string(root, "name"),
                CurveDocumentJson.number(root, "temperatureC"), CurveDocumentJson.number(root, "targetHundredthsGram"), mode,
                (0 until stages.length()).map { stage(stages.getJSONObject(it)) },
                if (schema == 1 || (schema == 3 && root.isNull("totalWaterLimitMl"))) null else CurveDocumentJson.number(root, "totalWaterLimitMl"), configuration).also { it.validate() }
        }
    }
}

internal class CurveWaterLimitException(val maximumMl: Int) : IllegalArgumentException()

internal object CurveDocumentJson {
    fun objectFrom(value: String): JSONObject {
        val tokenizer = JSONTokener(value)
        val root = tokenizer.nextValue()
        require(root is JSONObject && tokenizer.nextClean() == '\u0000') { "Invalid/trailing curve data" }
        return root
    }
    fun keys(value: JSONObject, expected: Set<String>) { require(value.keys().asSequence().toSet() == expected) }
    fun string(value: JSONObject, key: String): String { val raw = value.get(key); require(raw is String); return raw }
    fun boolean(value: JSONObject, key: String): Boolean { val raw = value.get(key); require(raw is Boolean); return raw }
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
        value is Boolean -> if (value) "t" else "f"
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
