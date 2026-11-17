package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiProtocolHistoryTest {
    private val data = object : AiReadOnlyData {
        override fun currentStatus() = "{\"status\":\"unknown\"}"
        override fun curves(query: String, offset: Int, limit: Int) = "{}"
        override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
    }
    private fun answer(text: String, reason: String, tool: Boolean = false): String {
        val root = Json.parseToJsonElement(aiAnswer(text)).jsonObject
        val choice = root.getValue("choices").jsonArray.first().jsonObject
        var message = choice.getValue("message").jsonObject + ("reasoning_content" to JsonPrimitive(reason))
        if (tool) message = message + mapOf("content" to JsonNull, "tool_calls" to buildJsonArray { add(buildJsonObject {
            put("id", "call1"); put("type", "function"); put("function", buildJsonObject { put("name", "current_status"); put("arguments", " { } ") })
        }) })
        return JsonObject(root + ("choices" to buildJsonArray { add(JsonObject(choice + mapOf("message" to JsonObject(message), "finish_reason" to JsonPrimitive(if (tool) "tool_calls" else "stop")))) })).toString()
    }
    private suspend fun send(history: AiProtocolHistory, lines: List<AiChatLine>, input: String, answers: List<String>, calls: MutableList<FakeAiConnection>): AiChatReply {
        val budget = AiRequestBudget(); var index = 0
        val safe = AiSafeTransport({ "fake-protocol-key" }, { true }, budget, {
            FakeAiConnection(answer = answers[index++]).also(calls::add)
        })
        val turn = AiProtocolTurn(history, lines, input, safe)
        val text = AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(lines, input)
        return turn.complete(text)
    }
    private fun withReason(raw: String, reason: JsonElement?): String {
        val root = Json.parseToJsonElement(raw).jsonObject
        val choice = root.getValue("choices").jsonArray.first().jsonObject
        val message = choice.getValue("message").jsonObject
        val next = if (reason == null) JsonObject(message - "reasoning_content") else JsonObject(message + ("reasoning_content" to reason))
        return JsonObject(root + ("choices" to JsonArray(listOf(JsonObject(choice + ("message" to next)))))).toString()
    }
    @Test fun nullableAndAbsentReasoningRoundTripWithoutFabrication() = runBlocking {
        for (reason in listOf<JsonElement?>(null, JsonNull, JsonPrimitive(""), JsonPrimitive("PRIVATE_REASON"))) {
            val calls = mutableListOf<FakeAiConnection>()
            val first = send(AiProtocolHistory.empty(), emptyList(), "first", listOf(withReason(aiAnswer("one"), reason)), calls)
            val exact = first.history.messages.last()
            assertEquals(reason != null, exact.containsKey("reasoning_content")); assertEquals(reason, exact["reasoning_content"])
            send(first.history, listOf(AiChatLine(true, "first"), AiChatLine(false, "one")), "second", listOf(withReason(aiAnswer("two"), reason)), calls)
            val replay = Json.parseToJsonElement(calls.last().sent.toString("UTF-8")).jsonObject.getValue("messages").jsonArray[2].jsonObject
            assertEquals(exact, replay)
        }
    }
    @Test fun multipleToolsWithNonemptyAssistantContentPreserveExactProtocol() = runBlocking {
        val original = Json.parseToJsonElement(answer("", "PRIVATE_TOOL_REASON", true)).jsonObject
        val choice = original.getValue("choices").jsonArray.first().jsonObject
        val message = choice.getValue("message").jsonObject
        val firstCall = message.getValue("tool_calls").jsonArray.first().jsonObject
        val secondCall = JsonObject(firstCall + ("id" to JsonPrimitive("call2")))
        val exact = JsonObject(message + ("content" to JsonPrimitive("Let me check.")) + ("reasoning_content" to JsonNull) +
            ("tool_calls" to JsonArray(listOf(firstCall, secondCall))))
        val toolResponse = JsonObject(original + ("choices" to JsonArray(listOf(JsonObject(choice + ("message" to exact)))))).toString()
        val calls = mutableListOf<FakeAiConnection>()
        val first = send(AiProtocolHistory.empty(), emptyList(), "first", listOf(toolResponse, aiAnswer("one")), calls)
        assertEquals(exact, first.history.messages[1])
        assertTrue(first.history.messages[1].getValue("tool_calls").jsonArray.all { "index" !in it.jsonObject })
        assertEquals(setOf("call1", "call2"), first.history.messages.filter { it["role"] == JsonPrimitive("tool") }.map { it["tool_call_id"]!!.jsonPrimitive.content }.toSet())
        val replay = Json.parseToJsonElement(calls.last().sent.toString("UTF-8")).jsonObject.getValue("messages").jsonArray
        assertEquals(exact, replay[2])
        assertTrue(replay[2].jsonObject.getValue("tool_calls").jsonArray.all { "index" !in it.jsonObject })
    }
    @Test fun invalidReasoningTypesAndProtocolMismatchesHaveClosedDiagnostics() = runBlocking {
        for (reason in listOf<JsonElement>(JsonPrimitive(42), buildJsonObject { put("PRIVATE_SENTINEL", true) })) {
            val error = runCatching { send(AiProtocolHistory.empty(), emptyList(), "first", listOf(withReason(aiAnswer("one"), reason)), mutableListOf()) }.exceptionOrNull() as AiFailure
            assertEquals(AiFailureKind.PROTOCOL_REASON, error.diagnostic?.kind)
            assertFalse(error.toString().contains("PRIVATE_SENTINEL")); assertNull(error.cause)
            val message = buildJsonObject { put("role", "assistant"); put("content", "one"); put("reasoning_content", reason) }
            val stored = runCatching { AiProtocolHistory.checked(listOf(message)) }.exceptionOrNull() as AiFailure
            assertEquals(AiFailureKind.PROTOCOL_REASON, stored.diagnostic?.kind)
        }
        val safe = AiSafeTransport({ "fake-protocol-key" }, { true }, AiRequestBudget(), { error("must not send") })
        val turn = AiProtocolTurn(AiProtocolHistory.empty(), emptyList(), "first", safe)
        val prefix = runCatching { turn.post("chat/completions", "{\"messages\":[]}", String::class, String::class) }.exceptionOrNull() as AiFailure
        assertEquals(AiFailureKind.PROTOCOL_PREFIX, prefix.diagnostic?.kind)
        val final = runCatching { turn.complete("PRIVATE_SENTINEL") }.exceptionOrNull() as AiFailure
        assertEquals(AiFailureKind.PROTOCOL_FINAL_MISSING, final.diagnostic?.kind)
        assertEquals(AiProblem.PROTOCOL, final.problem)
    }
    @Test fun mismatchedToolIdsFailBeforeAnotherNetworkRequest() = runBlocking {
        val budget = AiRequestBudget(); var requests = 0
        val safe = AiSafeTransport({ "fake-protocol-key" }, { true }, budget, {
            requests++; FakeAiConnection(answer = answer("", "PRIVATE_TOOL_REASON", true))
        })
        val turn = AiProtocolTurn(AiProtocolHistory.empty(), emptyList(), "first", safe)
        val mutate = object : ai.koog.http.client.KoogHttpClient by turn {
            override suspend fun <T : Any, R : Any> post(path: String, requestBody: T, requestBodyType: kotlin.reflect.KClass<T>,
                responseType: kotlin.reflect.KClass<R>, parameters: Map<String, String>, headers: Map<String, String>): R {
                if (requests == 0) return turn.post(path, requestBody, requestBodyType, responseType, parameters, headers)
                val raw = (requestBody as String).replace("call1", "WRONG_CALL")
                @Suppress("UNCHECKED_CAST")
                return turn.post(path, raw, String::class, String::class, parameters, headers) as R
            }
        }
        val error = runCatching { AiChatSession(data, mutate, budget, Dispatchers.Unconfined).reply(emptyList(), "first") }.exceptionOrNull() as AiFailure
        assertEquals(AiFailureKind.PROTOCOL_TOOLS, error.diagnostic?.kind); assertEquals(1, requests)
        assertFalse(error.diagnostic!!.label().contains("WRONG_CALL"))
    }
    @Test fun ordinaryAnswerReasoningSurvivesNextUserTurnWithoutLeakingIntoVisibleText() = runBlocking {
        val calls = mutableListOf<FakeAiConnection>()
        val first = send(AiProtocolHistory.empty(), emptyList(), "first", listOf(answer("one", "PRIVATE_REASON_ONE")), calls)
        assertEquals("one", first.text); assertFalse(first.toString().contains("PRIVATE")); assertFalse(first.history.toString().contains("PRIVATE"))
        send(first.history, listOf(AiChatLine(true, "first"), AiChatLine(false, "one")), "second", listOf(answer("two", "PRIVATE_REASON_TWO")), calls)
        val body = Json.parseToJsonElement(calls.last().sent.toString("UTF-8")).jsonObject
        assertEquals("enabled", body.getValue("thinking").jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("low", body["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("high", body.getValue("safety_check").jsonObject["level"]?.jsonPrimitive?.content)
        assertEquals("PRIVATE_REASON_ONE", body.getValue("messages").jsonArray[2].jsonObject["reasoning_content"]?.jsonPrimitive?.content)
    }
    @Test fun fullToolProtocolAndEveryReasoningMessageSurviveNextUserTurn() = runBlocking {
        val calls = mutableListOf<FakeAiConnection>()
        val first = send(AiProtocolHistory.empty(), emptyList(), "first", listOf(answer("", "PRIVATE_TOOL_REASON", true), answer("one", "PRIVATE_FINAL_REASON")), calls)
        val next = send(first.history, listOf(AiChatLine(true, "first"), AiChatLine(false, "one")), "second", listOf(answer("two", "PRIVATE_NEXT_REASON")), calls)
        val body = Json.parseToJsonElement(calls.last().sent.toString("UTF-8")).jsonObject
        val messages = body.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "tool", "assistant", "user"), messages.map { it["role"]?.jsonPrimitive?.content })
        assertEquals("PRIVATE_TOOL_REASON", messages[2]["reasoning_content"]?.jsonPrimitive?.content)
        assertEquals("PRIVATE_FINAL_REASON", messages[4]["reasoning_content"]?.jsonPrimitive?.content)
        assertEquals("call1", messages[3]["tool_call_id"]?.jsonPrimitive?.content)
        val originalCall = messages[2].getValue("tool_calls").jsonArray.first().jsonObject
        // Complete tool messages omit the SSE-only fragment locator.
        assertFalse(originalCall.containsKey("index"))
        assertEquals(setOf("id", "type", "function"), originalCall.keys)
        assertEquals("call1", originalCall["id"]?.jsonPrimitive?.content)
        assertEquals("function", originalCall["type"]?.jsonPrimitive?.content)
        assertEquals("current_status", originalCall.getValue("function").jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals(" { } ", originalCall.getValue("function").jsonObject["arguments"]?.jsonPrimitive?.content)
        assertEquals(6, next.history.messages.size)
        assertEquals("two", next.text)
    }
    @Test fun nativeResultUpdatesOnlyFinalVisibleAnswerAndPreservesToolProtocol() = runBlocking {
        val calls = mutableListOf<FakeAiConnection>()
        val first = send(AiProtocolHistory.empty(), emptyList(), "first",
            listOf(answer("", "PRIVATE_TOOL_REASON", true), answer("one", "PRIVATE_FINAL_REASON")), calls)
        val updatedText = "one\n\n[OpenHOYI local result] Saved locally"
        val updated = first.history.withNativeResult(updatedText)
        assertEquals(first.history.messages.dropLast(1), updated.messages.dropLast(1))
        assertTrue(AiContextPolicy.matches(updated, listOf(AiChatLine(true, "first"), AiChatLine(false, updatedText))))
        send(updated, listOf(AiChatLine(true, "first"), AiChatLine(false, updatedText)), "next", listOf(answer("two", "PRIVATE_NEXT_REASON")), calls)
        assertTrue(calls.last().sent.toString("UTF-8").contains("Saved locally"))
    }
    @Test fun syntheticThreePostScenarioPassesFinalByteAndMessageGuards() = runBlocking {
        val calls = mutableListOf<FakeAiConnection>()
        val first = send(AiProtocolHistory.empty(), emptyList(), AiLiveProbePolicy.TOOL_PROMPT,
            listOf(answer("", "PRIVATE_TOOL_REASON", true), answer("one", "PRIVATE_FINAL_REASON")), calls)
        send(first.history, listOf(AiChatLine(true, AiLiveProbePolicy.TOOL_PROMPT), AiChatLine(false, "one")),
            AiLiveProbePolicy.FOLLOW_PROMPT, listOf(answer("two", "PRIVATE_FOLLOW_REASON")), calls)
        assertEquals(3, calls.size)
        for (call in calls) {
            val raw = call.sent.toString("UTF-8")
            assertTrue(runCatching { AiLiveProbePolicy.scenarioRequest(raw.replace("web_search", "unknown_remote_tool")) }.isFailure)
            assertTrue(runCatching { AiLiveProbePolicy.scenarioRequest(raw.replace("present_curve_cards", "unknown_card_tool")) }.isFailure)
            val bytes = AiLiveProbePolicy.scenarioRequest(raw)
            assertTrue(bytes.size <= 8192)
            assertFalse(bytes.toString(Charsets.UTF_8).contains("\"name\":\"web_search\""))
            assertEquals(256, Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject["max_tokens"]?.jsonPrimitive?.int)
        }
    }
    @Test fun failedPartialTurnDoesNotMutateCommittedHistoryAndWholeProtocolLimitsApply() = runBlocking {
        val calls = mutableListOf<FakeAiConnection>()
        val first = send(AiProtocolHistory.empty(), emptyList(), "first", listOf(answer("one", "PRIVATE_REASON")), calls)
        val original = first.history.messages.toList()
        val failed = runCatching { send(first.history, listOf(AiChatLine(true, "first"), AiChatLine(false, "one")), "second", listOf("{}"), calls) }
        assertTrue(failed.isFailure); assertEquals(original, first.history.messages)
        val huge = buildJsonObject { put("role", "assistant"); put("content", "one"); put("reasoning_content", "x".repeat(AiProtocolHistory.MAX_BYTES)) }
        assertEquals(AiProblem.CONTEXT_LIMIT, (runCatching { AiProtocolHistory.checked(listOf(huge)) }.exceptionOrNull() as AiFailure).problem)
        assertTrue(runCatching { AiProtocolTurn(AiProtocolHistory.empty(), listOf(AiChatLine(false, "unrecoverable")), "next", AiSafeTransport({ "fake-key" }, { true }, AiRequestBudget())) }.isFailure)
        val state = AiChatViewState().apply { protocol = first.history; lines += AiChatLine(false, "one") }
        state.clear(); assertTrue(state.protocol.messages.isEmpty()); assertTrue(state.lines.isEmpty())
    }
    @Test fun lowReportedUsageCannotBypassFullWireBudgetBeforeNetwork() = runBlocking {
        val visible = listOf(AiChatLine(true, "old"), AiChatLine(false, "answer"))
        val messages = AiContextPolicy.lines(visible).toMutableList()
        messages[1] = JsonObject(messages[1] + ("reasoning_content" to JsonPrimitive("r".repeat(255000))))
        val history = AiProtocolHistory.checked(messages, reportedInput = 1, reportedOutput = 1)
        var calls = 0; val budget = AiRequestBudget()
        val safe = AiSafeTransport({ "fake-wire-key" }, { true }, budget, { calls++; FakeAiConnection() })
        val turn = AiProtocolTurn(history, visible, "next", safe)
        val failure = runCatching { AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(visible, "next") }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.CONTEXT_LIMIT, failure.problem); assertEquals(0, calls)
        assertEquals(2, history.messages.size)
    }

}
