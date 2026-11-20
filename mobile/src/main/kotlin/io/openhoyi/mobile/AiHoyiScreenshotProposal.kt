package io.openhoyi.mobile

import io.openhoyi.protocol.CustomCurveStartPolicy
import kotlinx.serialization.json.*
import java.math.BigDecimal
import org.json.JSONObject

/** Display-value transcription only. Attachment trust is supplied by the app, never the model. */
internal object AiHoyiScreenshotProposal {
    const val SCHEMA = """For HOYI screenshot transcription use exact outer keys {"operation":"transcribe_screenshot","transcription":{...}}, never raw create/replace/copy_from_factory or derived curve/schema/flags. transcription exact keys: sourceAttachmentId (the App-provided ID beside the actual outgoing image), name (visible string), mode ("pressure" for 变压 or "flow" for 变流), logic ("constant" 恒压, "follow" 跟随, "ls" 慢速/Ls, "hs" 快速/Hs), firstStageUnit ("bar" or "ml_per_second"), temperatureC, totalWaterMl (总流量), targetWeightG (总液重), compensationG (克重补偿, signed), preinfusionSeconds (闷蒸等待), firstDurationSeconds (seconds beside FIRST stage water, time limit), stages=[{target,unit:"bar" or "ml_per_second",waterMl}]. Numeric values are exactly displayed JSON numbers in °C, mL, g, seconds, bar or mL/s: DO NOT multiply units or compute internal flags. All keys required; use null for unreadable/unknown values and ask for those values before proposing. Null/unknown/incompatible mode or unit blocks creation. Keep 2–4 visible active stages in order. App maps labels deterministically to complete schema3 and zero inactive slots. Do not optimize, infer missing values/identities, interpret diagram lines as measured time/linear ramps, or silently round/clamp. Bean/package images use ordinary recommendation/create tools, not this transcription. """

    fun forTool(raw: String, availableImageIds: Set<String>): String {
        val root = Json.parseToJsonElement(raw).jsonObject
        val operation = (root["operation"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        // The element parser normalizes duplicate names, so check the raw envelope
        // before trusting the selected operation or converting its transcription.
        checkPhotoKeys(raw, required = operation == "transcribe_screenshot")
        if (operation == "transcribe_screenshot") {
            require(root.keys == setOf("operation", "transcription"))
            val transcription = root.getValue("transcription").jsonObject
            require(transcription.toString().toByteArray(Charsets.UTF_8).size <= AiCurveWriteCoordinator.MAX_ARGUMENT_BYTES)
            return proposal(transcription, availableImageIds)
        }
        return raw
    }

    fun proposal(raw: String, trustedImageIds: Set<String>): String {
        require(raw.toByteArray(Charsets.UTF_8).size <= AiCurveWriteCoordinator.MAX_ARGUMENT_BYTES)
        val root = Json.parseToJsonElement(raw).jsonObject
        checkPhotoKeys(raw, required = true)
        return proposal(root, trustedImageIds)
    }

    private fun proposal(root: JsonObject, trustedImageIds: Set<String>): String {
        require(root.keys == setOf("sourceAttachmentId", "name", "mode", "logic", "firstStageUnit", "temperatureC",
            "totalWaterMl", "targetWeightG", "compensationG", "preinfusionSeconds", "firstDurationSeconds", "stages"))
        fun text(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.also { require(it.isString) }?.content
            ?.takeIf { it.isNotBlank() } ?: error("Missing screenshot value")
        fun number(o: JsonObject, k: String, places: Int): Int {
            val p = o[k] as? JsonPrimitive ?: error("Missing screenshot value")
            require(!p.isString && p != JsonNull && Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(p.content))
            return BigDecimal(p.content).movePointRight(places).intValueExact()
        }
        require(text(root, "sourceAttachmentId") in trustedImageIds)
        val flow = when (text(root, "mode")) { "pressure" -> false; "flow" -> true; else -> error("Unknown mode") }
        val logic = when (text(root, "logic")) {
            "constant" -> { require(!flow); false }; "follow" -> { require(!flow); true }
            "ls" -> { require(flow); false }; "hs" -> { require(flow); true }; else -> error("Unknown logic")
        }
        val firstFlow = when (text(root, "firstStageUnit")) {
            "bar" -> false; "ml_per_second" -> { require(flow); true }; else -> error("Unknown first unit")
        }
        val stages = (root["stages"] as? JsonArray)?.also { require(it.size in 2..4) }?.mapIndexed { i, element ->
            val s = element.jsonObject
            require(s.keys == setOf("target", "unit", "waterMl"))
            require(text(s, "unit") == if (flow && (i > 0 || firstFlow)) "ml_per_second" else "bar")
            CustomCurveDocument.Stage(number(s, "target", 1), number(s, "waterMl", 1)).also { require(it.waterTenthsMl % 10 == 0) }
        } ?: error("Missing stages")
        val document = CustomCurveDocument(CustomCurveDocument.newId(), text(root, "name"), number(root, "temperatureC", 0),
            number(root, "targetWeightG", 2), if (flow) CustomCurveDocument.ControlMode.FLOW else CustomCurveDocument.ControlMode.PRESSURE,
            stages, number(root, "totalWaterMl", 0), CustomCurveDocument.Configuration(logic, flow, firstFlow,
                number(root, "preinfusionSeconds", 0), number(root, "firstDurationSeconds", 0), number(root, "compensationG", 2),
                List(4 - stages.size) { CustomCurveDocument.Stage(0, 0) }))
        document.validate()
        require(document.targetHundredthsGram % 10 == 0 && document.configuration!!.compensationHundredthsGram % 10 == 0)
        require(listOf(false, true).all { scale -> CustomCurveAdapter.profile(document, scale)?.let { CustomCurveStartPolicy.permits(it.parameters) } == true })
        return JSONObject().put("operation", "create").put("curve", JSONObject(document.encode()).also { it.remove("id") }).toString()
    }

    /** Json has already checked syntax. Strings followed by ':' are object member
     * names; check equality in the owning object, decoding escaped names with Json. */
    private fun checkPhotoKeys(raw: String, required: Boolean) {
        val scopes = ArrayDeque<MutableSet<String>>()
        var photo = required
        var duplicate = false
        var index = 0
        fun stringEnd(start: Int): Int {
            var end = start + 1
            while (raw[end] != '"') {
                if (raw[end] == '\\') end++
                end++
            }
            return end + 1
        }
        fun decoded(start: Int, end: Int): String {
            val quoted = raw.substring(start, end)
            return if ('\\' in quoted) Json.parseToJsonElement(quoted).jsonPrimitive.content
                else quoted.substring(1, quoted.length - 1)
        }
        fun afterWhitespace(start: Int): Int {
            var i = start
            while (i < raw.length && raw[i] in " \t\r\n") i++
            return i
        }
        while (index < raw.length) when (raw[index++]) {
            '{' -> scopes.addLast(hashSetOf())
            '}' -> scopes.removeLast()
            '"' -> {
                val start = index - 1
                index = stringEnd(start)
                val colon = afterWhitespace(index)
                if (raw.getOrNull(colon) == ':') {
                    val key = decoded(start, index)
                    if (!scopes.last().add(key)) duplicate = true
                    // A repeated top-level operation may otherwise disguise a photo
                    // proposal as an ordinary create/replace after normalization.
                    if (!photo && scopes.size == 1 && key == "operation") {
                        val value = afterWhitespace(colon + 1)
                        if (raw.getOrNull(value) == '"') {
                            photo = decoded(value, stringEnd(value)) == "transcribe_screenshot"
                        }
                    }
                }
            }
        }
        require(!photo || !duplicate) { "Duplicate screenshot field" }
    }
}
