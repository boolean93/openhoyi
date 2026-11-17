package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Fake provider responses test native dispatch, not OCR correctness. */
class AiHoyiScreenshotToolTest {
    @Test fun actualSdkDispatchRejectsRawCreateInTrustedImportButNotOrdinaryImageContext() = runBlocking {
        val id = "00000000-0000-0000-0000-000000000001"
        val data = object : AiReadOnlyData {
            override fun currentStatus() = "{}"
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        for (trustedId in listOf(null, id)) {
            val arguments = buildJsonObject { put("proposal_json", """{"operation":"create","curve":{}}""") }.toString()
            val response = JsonObject(Json.parseToJsonElement(aiAnswer("Done")).jsonObject + buildJsonObject { put("choices", buildJsonArray { add(buildJsonObject {
                put("index", 0); put("finish_reason", "tool_calls"); put("message", buildJsonObject {
                    put("role", "assistant"); put("content", JsonNull); put("tool_calls", buildJsonArray { add(buildJsonObject {
                        put("id", "call1"); put("type", "function"); put("function", buildJsonObject {
                            put("name", "propose_custom_curve"); put("arguments", arguments)
                        })
                    }) })
                })
            }) }) }).toString()
            var index = 0; var proposed = 0
            val calls = mutableListOf<FakeAiConnection>(); val budget = AiRequestBudget()
            val transport = AiSafeTransport({ "fake-key" }, { true }, budget, {
                FakeAiConnection(answer = if (index++ == 0) response else aiAnswer("Done")).also(calls::add)
            })
            AiChatSession(data, transport, budget, Dispatchers.Unconfined,
                proposeCurve = { proposed++; AiCurveProposalStatus.AWAITING_EDITOR }, hoyiScreenshotId = trustedId).reply(emptyList(), "import")
            assertEquals(if (trustedId == null) 1 else 0, proposed)
            val tools = Json.parseToJsonElement(calls.first().sent.toString("UTF-8")).jsonObject.getValue("tools").jsonArray
            val function = tools.single { it.jsonObject.getValue("function").jsonObject["name"] == JsonPrimitive("propose_custom_curve") }
                .jsonObject.getValue("function").jsonObject
            val description = function.getValue("description").jsonPrimitive.content
            assertEquals(trustedId != null, description.contains("sourceAttachmentId=$id"))
            val schema = function.getValue("parameters").jsonObject.getValue("properties").jsonObject
                .getValue("proposal_json").jsonObject.getValue("description").jsonPrimitive.content
            assertTrue(schema.contains(AiHoyiScreenshotProposal.SCHEMA))
            assertTrue(calls.last().sent.toString("UTF-8").contains(if (trustedId == null) "awaiting_editor" else "invalid_arguments"))
        }
    }
}
