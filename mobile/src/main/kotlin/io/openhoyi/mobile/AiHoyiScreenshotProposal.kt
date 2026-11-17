package io.openhoyi.mobile

import io.openhoyi.protocol.CustomCurveStartPolicy
import kotlinx.serialization.json.*
import java.math.BigDecimal
import org.json.JSONObject

/** Display-value transcription only. Attachment trust is supplied by the app, never the model. */
internal object AiHoyiScreenshotProposal {
    const val SCHEMA = """When native HOYI screenshot import is active, ALL proposals MUST use exact outer keys {"operation":"transcribe_screenshot","transcription":{...}}, never raw create/replace/copy_from_factory or derived curve/schema/flags. transcription exact keys: sourceAttachmentId (the active ID in this tool description), name (visible string), mode ("pressure" for 变压 or "flow" for 变流), logic ("constant" 恒压, "follow" 跟随, "ls" 慢速/Ls, "hs" 快速/Hs), firstStageUnit ("bar" or "ml_per_second"), temperatureC, totalWaterMl (总流量), targetWeightG (总液重), compensationG (克重补偿, signed), preinfusionSeconds (闷蒸等待), firstDurationSeconds (seconds beside FIRST stage water, time limit), stages=[{target,unit:"bar" or "ml_per_second",waterMl}]. Numeric values are exactly displayed JSON numbers in °C, mL, g, seconds, bar or mL/s: DO NOT multiply units or compute internal flags. All keys required; use null for unreadable/unknown values and ask for those values before proposing. Null/unknown/incompatible mode or unit blocks creation. Keep 2–4 visible active stages in order. App maps labels deterministically to complete schema3 and zero inactive slots. Do not optimize, infer missing values/identities, interpret diagram lines as measured time/linear ramps, or silently round/clamp. Ordinary images do not activate this native mode. """

    fun turnAttachments(items: List<AiAttachment>, source: AiAttachment?): List<AiAttachment> {
        if (source == null) return items
        require(source.kind == AiAttachmentKind.IMAGE)
        items.firstOrNull { it.id == source.id }?.let {
            require(it.name == source.name && it.mimeType == source.mimeType && it.size == source.size && it.kind == source.kind)
        }
        return (items.filterNot { it.id == source.id } + source).also { AiAttachmentConversation.encode(it) }
    }

    fun forTool(raw: String, trustedImageId: String?): String {
        val root = Json.parseToJsonElement(raw).jsonObject
        val operation = (root["operation"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (operation == "transcribe_screenshot") {
            require(trustedImageId != null && root.keys == setOf("operation", "transcription"))
            return proposal(root.getValue("transcription").toString(), setOf(trustedImageId))
        }
        require(trustedImageId == null)
        return raw
    }

    fun proposal(raw: String, trustedImageIds: Set<String>): String {
        require(raw.toByteArray(Charsets.UTF_8).size <= AiCurveWriteCoordinator.MAX_ARGUMENT_BYTES)
        val root = Json.parseToJsonElement(raw).jsonObject
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
}
