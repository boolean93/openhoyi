package io.openhoyi.mobile

import kotlinx.serialization.json.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiHoyiScreenshotProposalTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val raw = """{"sourceAttachmentId":"00000000-0000-0000-0000-000000000001","name":"日晒","mode":"pressure","logic":"follow","firstStageUnit":"bar","temperatureC":92,"totalWaterMl":78,"targetWeightG":0.0,"compensationG":0.0,"preinfusionSeconds":5,"firstDurationSeconds":10,"stages":[{"target":2.0,"unit":"bar","waterMl":25},{"target":6.0,"unit":"bar","waterMl":5},{"target":9.0,"unit":"bar","waterMl":48}]}"""
    private fun changed(key: String, value: JsonElement) = JsonObject(Json.parseToJsonElement(raw).jsonObject + (key to value)).toString()
    private fun document(value: String = raw): CustomCurveDocument {
        val curve = JSONObject(AiHoyiScreenshotProposal.proposal(value, setOf(id))).getJSONObject("curve")
        assertEquals(3, curve.getInt("schema")); assertFalse(curve.has("id"))
        return CustomCurveDocument.decode(curve.put("id", CustomCurveDocument.newId()).toString())
    }
    @Test fun transcriptionRequiresActualImageButDoesNotLockOtherBusinessOperations() {
        val structured = JsonObject(mapOf("operation" to JsonPrimitive("transcribe_screenshot"), "transcription" to Json.parseToJsonElement(raw))).toString()
        assertEquals("create", JSONObject(AiHoyiScreenshotProposal.forTool(structured, setOf(id))).getString("operation"))
        assertTrue(runCatching { AiHoyiScreenshotProposal.forTool(structured, emptySet()) }.isFailure)
        for (operation in listOf("create", "replace", "copy_from_factory")) {
            val value = "{\"operation\":\"$operation\",\"curve\":{}}"
            assertEquals(value, AiHoyiScreenshotProposal.forTool(value, emptySet()))
            assertEquals(value, AiHoyiScreenshotProposal.forTool(value, setOf(id)))
        }
    }
    @Test fun officialDisplayedValuesBecomeCompleteSchema3WithoutModelFlags() {
        val d = document(); val c = requireNotNull(d.configuration)
        assertEquals("日晒", d.name); assertEquals(92, d.temperatureC); assertEquals(78, d.totalWaterLimitMl)
        assertEquals(0, d.targetHundredthsGram); assertTrue(c.pressureLogic)
        assertFalse(c.variableFlowLogic); assertFalse(c.firstSegmentFlowMode)
        assertEquals(5, c.preinfusionSeconds); assertEquals(10, c.firstDurationSeconds)
        assertEquals(listOf(20,60,90), d.stages.map { it.target })
        assertEquals(listOf(250,50,480), d.stages.map { it.waterTenthsMl })
        assertEquals(listOf(CustomCurveDocument.Stage(0,0)), c.inactiveStages)
        assertFalse(document(changed("logic", JsonPrimitive("constant"))).configuration!!.pressureLogic)
    }
    @Test fun missingUnknownIncompatibleAndUntrustedValuesNeverDefault() {
        for (key in Json.parseToJsonElement(raw).jsonObject.keys) {
            assertTrue(key, runCatching { document(changed(key, JsonNull)) }.isFailure)
        }
        for ((key, value) in listOf("logic" to JsonPrimitive("hs"), "firstStageUnit" to JsonPrimitive("ml_per_second"),
            "logic" to JsonPrimitive("unknown"), "temperatureC" to JsonPrimitive(106), "targetWeightG" to JsonPrimitive(0.01),
            "compensationG" to JsonPrimitive(-0.01), "totalWaterMl" to JsonPrimitive(78.1), "firstDurationSeconds" to JsonPrimitive(10.1))) {
            assertTrue(key, runCatching { document(changed(key, value)) }.isFailure)
        }
        assertTrue(runCatching { AiHoyiScreenshotProposal.proposal(raw, emptySet()) }.isFailure)
        assertTrue(runCatching { document(changed("pressureLogic", JsonPrimitive(false))) }.isFailure)
        val wrongUnit = raw.replace("\"unit\":\"bar\"", "\"unit\":\"ml_per_second\"")
        assertTrue(runCatching { document(wrongUnit) }.isFailure)
    }
    @Test fun flowLogicAndFirstStageDisplayedUnitsMapIndependently() {
        for ((logic, firstUnit) in listOf("hs" to "bar", "ls" to "ml_per_second")) {
            val flow = raw.replace("\"mode\":\"pressure\"", "\"mode\":\"flow\"")
                .replace("\"logic\":\"follow\"", "\"logic\":\"$logic\"")
                .replace("\"firstStageUnit\":\"bar\"", "\"firstStageUnit\":\"$firstUnit\"")
                .replace("\"target\":6.0,\"unit\":\"bar\"", "\"target\":6.0,\"unit\":\"ml_per_second\"")
                .replace("\"target\":9.0,\"unit\":\"bar\"", "\"target\":9.0,\"unit\":\"ml_per_second\"")
                .let { if (firstUnit == "ml_per_second") it.replace("\"target\":2.0,\"unit\":\"bar\"", "\"target\":2.0,\"unit\":\"ml_per_second\"") else it }
            val d = document(flow); val c = requireNotNull(d.configuration)
            assertTrue(c.variableFlowLogic); assertEquals(logic == "hs", c.pressureLogic)
            assertEquals(firstUnit == "ml_per_second", c.firstSegmentFlowMode)
        }
    }

    @Test fun duplicatePhotoFieldsAreRejectedEvenWhenEscapedOrIdentical() {
        val variants = listOf(
            raw.replace("\"logic\":\"follow\"", "\"logic\":\"constant\",\"logic\":\"follow\""),
            raw.replace("\"temperatureC\":92", "\"temperatureC\":92,\"temperatureC\":92"),
            raw.replace("\"target\":2.0", "\"target\":2.0,\"targ\\u0065t\":2.0"),
        )
        for (variant in variants) {
            assertNotEquals(raw, variant)
            assertTrue(runCatching { document(variant) }.isFailure)
            val envelope = """{"operation":"transcribe_screenshot","transcription":$variant}"""
            assertTrue(runCatching { AiHoyiScreenshotProposal.forTool(envelope, setOf(id)) }.isFailure)
        }
    }

    @Test fun envelopeRejectsDuplicateFieldsEvenIfOperationWouldNormalizeToCreate() {
        val duplicateTranscription = """{"operation":"transcribe_screenshot","transcription":$raw,"transcription":$raw}"""
        val duplicateOperation = """{"operation":"transcribe\u005fscreenshot","operat\u0069on":"create","curve":{}}"""
        for (variant in listOf(duplicateTranscription, duplicateOperation)) {
            assertTrue(runCatching { AiHoyiScreenshotProposal.forTool(variant, setOf(id)) }.isFailure)
        }
    }

    @Test fun quotedBracesAndSameKeysInDifferentStagesDoNotCountAsDuplicates() {
        val name = """named { "logic": "follow" } [path\file]"""
        val value = changed("name", JsonPrimitive(name))
        assertEquals(name, document(value).name)
        val envelope = """{"operation":"transcribe_screenshot","transcription":$value}"""
        assertEquals("create", JSONObject(AiHoyiScreenshotProposal.forTool(envelope, setOf(id))).getString("operation"))
    }

    @Test fun unrelatedProposalsKeepOriginalRouting() {
        val ordinary = """{"operation":"create","curve":{"name":"transcribe_screenshot","logic":false,"logic":true}}"""
        assertEquals(ordinary, AiHoyiScreenshotProposal.forTool(ordinary, setOf(id)))
    }
}
