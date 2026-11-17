package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Real installed Koog artifacts with synthetic transport only; no provider API or stored credential. */
class AiProtocolFinalKoogProbeTest {
    private fun toolResponse(id: String, content: String?): String {
        val root = Json.parseToJsonElement(aiAnswer("unused")).jsonObject
        val choice = root.getValue("choices").jsonArray.single().jsonObject
        val message = buildJsonObject {
            put("role", "assistant"); put("content", content?.let(::JsonPrimitive) ?: JsonNull)
            put("reasoning_content", "SYNTHETIC_PRIVATE_REASON")
            put("tool_calls", buildJsonArray { add(buildJsonObject {
                put("id", id); put("type", "function"); put("function", buildJsonObject { put("name", "current_status"); put("arguments", "{}") })
            }) })
        }
        return JsonObject(root + ("choices" to JsonArray(listOf(JsonObject(choice + mapOf("message" to message, "finish_reason" to JsonPrimitive("tool_calls"))))))).toString()
    }
    @Test fun mixedTextAndToolCallsFinishBothToolsAndPreserveExactProtocol() = runBlocking {
        var reads = 0; val calls = mutableListOf<FakeAiConnection>()
        val data = object : AiReadOnlyData {
            override fun currentStatus(): String { reads++; return "{\"status\":\"unknown\"}" }
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        val answers = listOf(toolResponse("first-call", null), toolResponse("second-call", "Still checking"), aiAnswer("Final answer"))
        val budget = AiRequestBudget(); val history = AiProtocolHistory.empty()
        val safe = AiSafeTransport({ "synthetic-probe-key" }, { true }, budget, { FakeAiConnection(answer = answers[calls.size]).also(calls::add) })
        val turn = AiProtocolTurn(history, emptyList(), "Synthetic question", safe)
        val sdkAnswer = AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(emptyList(), "Synthetic question")
        val reply = turn.complete(sdkAnswer)
        assertEquals(3, calls.size); assertEquals(2, reads); assertEquals("Final answer", reply.text)
        val exactMixed = Json.parseToJsonElement(answers[1]).jsonObject.getValue("choices").jsonArray.single().jsonObject.getValue("message").jsonObject
        assertEquals(exactMixed, reply.history.messages[3])
        val replay = Json.parseToJsonElement(calls[2].sent.toString("UTF-8")).jsonObject.getValue("messages").jsonArray
        assertEquals(exactMixed, replay[4])
        assertEquals(listOf("first-call", "second-call"), reply.history.messages.filter { it["role"] == JsonPrimitive("tool") }.map { it.getValue("tool_call_id").jsonPrimitive.content })
        assertEquals(6, reply.history.messages.size)
        assertFalse(reply.text.contains("SYNTHETIC_PRIVATE_REASON")); assertFalse(reply.text.contains("Still checking"))
        assertTrue(history.messages.isEmpty())
    }
    @Test fun ordinaryFinalTextIsNotTrimmedOrCombinedWithReasoningByTheActualSdk() = runBlocking {
        val visible = "  synthetic answer\n\n"
        val base = Json.parseToJsonElement(aiAnswer(visible)).jsonObject
        val choice = base.getValue("choices").jsonArray.single().jsonObject
        val message = JsonObject(choice.getValue("message").jsonObject + ("reasoning_content" to JsonPrimitive("SYNTHETIC_PRIVATE_REASON")))
        val response = JsonObject(base + ("choices" to JsonArray(listOf(JsonObject(choice + ("message" to message)))))).toString()
        val data = object : AiReadOnlyData {
            override fun currentStatus() = error("No tool expected")
            override fun curves(query: String, offset: Int, limit: Int) = error("No tool expected")
            override fun history(curveId: String?, offset: Int, limit: Int) = error("No tool expected")
        }
        val budget = AiRequestBudget(); var requests = 0
        val safe = AiSafeTransport({ "synthetic-probe-key" }, { true }, budget, { requests++; FakeAiConnection(answer = response) })
        val turn = AiProtocolTurn(AiProtocolHistory.empty(), emptyList(), "Synthetic question", safe)
        val sdkAnswer = AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(emptyList(), "Synthetic question")
        assertEquals(visible, sdkAnswer); assertEquals(visible, turn.complete(sdkAnswer).text); assertEquals(1, requests)
    }
    @Test fun finalTextMismatchStaysProtocolFailureWithSharedHttpDiagnostic() = runBlocking {
        val data = object : AiReadOnlyData {
            override fun currentStatus() = error("No tool expected")
            override fun curves(query: String, offset: Int, limit: Int) = error("No tool expected")
            override fun history(curveId: String?, offset: Int, limit: Int) = error("No tool expected")
        }
        val diagnostics = AiDiagnostics(); val budget = AiRequestBudget(); var requests = 0
        val safe = AiSafeTransport({ "synthetic-probe-key" }, { true }, budget,
            { requests++; FakeAiConnection(answer = aiAnswer("Exact answer")) }, diagnostics = diagnostics)
        val base = AiProtocolHistory.empty()
        val turn = AiProtocolTurn(base, emptyList(), "Synthetic question", safe, diagnostics)
        val actual = AiChatSession(data, turn, budget, Dispatchers.Unconfined, diagnostics = diagnostics).reply(emptyList(), "Synthetic question")
        assertEquals("Exact answer", actual)
        val failure = runCatching { turn.complete("Different answer") }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.PROTOCOL, failure.problem)
        assertEquals(AiFailureKind.PROTOCOL_FINAL_TEXT, failure.diagnostic?.kind)
        assertEquals(AiStage.PROTOCOL, failure.diagnostic?.stage); assertEquals(200, failure.diagnostic?.httpStatus)
        assertEquals(1, requests); assertTrue(base.messages.isEmpty())
    }
    @Test fun projectedSdkTextDoesNotPermitCommittingAnyPendingToolTransaction() = runBlocking {
        val diagnostics = AiDiagnostics(); var requests = 0
        val raw = toolResponse("pending-call", "Still checking")
        val unused = AiSafeTransport({ "synthetic-probe-key" }, { true }, AiRequestBudget(), { error("No HTTP expected") })
        val delegate = object : ai.koog.http.client.KoogHttpClient by unused {
            override suspend fun <T : Any, R : Any> post(path: String, requestBody: T, requestBodyType: kotlin.reflect.KClass<T>,
                responseType: kotlin.reflect.KClass<R>, parameters: Map<String, String>, headers: Map<String, String>): R {
                requests++; diagnostics.httpStatus = 200
                @Suppress("UNCHECKED_CAST") return raw as R
            }
        }
        val base = AiProtocolHistory.empty()
        val turn = AiProtocolTurn(base, emptyList(), "Synthetic question", delegate, diagnostics)
        val request = buildJsonObject {
            put("model", AiModelPolicy.FLASH)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", AiChatPolicy.SYSTEM) })
                add(buildJsonObject { put("role", "user"); put("content", "Synthetic question") })
            })
        }.toString()
        val sdk = turn.post("chat/completions", request, String::class, String::class)
        val sdkRoot = Json.parseToJsonElement(sdk).jsonObject
        assertEquals(JsonNull, sdkRoot.getValue("choices").jsonArray.single().jsonObject.getValue("message").jsonObject["content"])
        assertEquals(Json.parseToJsonElement(raw).jsonObject["usage"], sdkRoot["usage"])
        val failure = runCatching { turn.complete("Still checking") }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.PROTOCOL, failure.problem); assertEquals(AiFailureKind.PROTOCOL_FINAL_TOOLS, failure.diagnostic?.kind)
        assertEquals(200, failure.diagnostic?.httpStatus); assertEquals(1, requests); assertTrue(base.messages.isEmpty())
    }

}
