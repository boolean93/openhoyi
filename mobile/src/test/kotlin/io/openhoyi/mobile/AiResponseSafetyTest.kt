package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class AiResponseSafetyTest {
    private val sentinel = "PRIVATE_RESPONSE_SENTINEL"
    private val data = object : AiReadOnlyData {
        override fun currentStatus(): String = error("No tool may execute")
        override fun curves(query: String, offset: Int, limit: Int): String = error("No tool may execute")
        override fun history(curveId: String?, offset: Int, limit: Int): String = error("No tool may execute")
    }
    private fun response(reason: JsonElement? = JsonPrimitive("tool_calls"), error: JsonElement? = null,
        refusal: JsonElement? = null, usage: Boolean = true, selector: Boolean = false): String {
        val base = Json.parseToJsonElement(aiAnswer(sentinel)).jsonObject
        val choice = base.getValue("choices").jsonArray.single().jsonObject
        val message = JsonObject(choice.getValue("message").jsonObject + mapOf(
            "reasoning_content" to JsonPrimitive(sentinel),
            "tool_calls" to buildJsonArray { add(buildJsonObject {
                put("id", "proposal"); put("type", "function"); put("function", buildJsonObject {
                    put("name", if (selector) "present_curve_cards" else "propose_custom_curve")
                    put("arguments", if (selector) buildJsonObject { put("ids_json", "[]") }.toString() else "{\"proposal_json\":\"{}\"}")
                })
            }) }) + if (refusal == null) emptyMap() else mapOf("refusal" to refusal))
        val nextChoice = JsonObject((choice - "finish_reason") + ("message" to message) +
            if (reason == null) emptyMap() else mapOf("finish_reason" to reason))
        return JsonObject((if (usage) base else base - "usage") + ("choices" to JsonArray(listOf(nextChoice))) +
            if (error == null) emptyMap() else mapOf("error" to error)).toString()
    }
    @Test fun completeHttp200ErrorsRefusalsAndUnknownFinishCannotExecuteOrCommit() = runBlocking {
        val cases = listOf(
            response(error = buildJsonObject { put("message", sentinel) }),
            response(reason = JsonPrimitive("length"), error = buildJsonObject { put("message", sentinel) }),
            response(reason = JsonPrimitive("unknown")), response(reason = null), response(reason = JsonNull),
            response(refusal = JsonPrimitive(sentinel)), response(reason = JsonPrimitive("length"), refusal = JsonPrimitive(sentinel)),
            response(refusal = buildJsonObject { put("text", sentinel) }), response(refusal = JsonPrimitive(false)),
            response(reason = JsonPrimitive("content_filter")),
            response(refusal = JsonPrimitive(sentinel), selector = true))
        for (raw in cases) {
            val oldLines = listOf(AiChatLine(true, "old question"), AiChatLine(false, "old answer"))
            val original = AiProtocolHistory.checked(AiContextPolicy.lines(oldLines))
            val ledger = AiUsageLedger(); val budget = AiRequestBudget(); val calls = mutableListOf<FakeAiConnection>()
            val safe = AiSafeTransport({ "fake-response-key" }, { true }, budget,
                { FakeAiConnection(answer = "\n\n$raw").also(calls::add) }, usage = AiUsageRecorder(listOf(ledger)))
            val turn = AiProtocolTurn(original, oldLines, "new question", safe)
            var proposals = 0
            var selections = 0
            val failure = runCatching { AiChatSession(data, turn, budget, Dispatchers.Unconfined,
                proposeCurve = { proposals++; AiCurveProposalStatus.AWAITING_EDITOR },
                presentCurveCards = { selections++; "{\"status\":\"selected\"}" }).reply(oldLines, "new question") }.exceptionOrNull() as AiFailure
            assertFalse(failure is AiPartialCompletion); assertFalse(failure.toString().contains(sentinel)); assertNull(failure.cause)
            assertEquals(1, calls.size); assertEquals(0, proposals); assertEquals(0, selections)
            assertTrue(runCatching { turn.complete(sentinel) }.isFailure)
            assertEquals(AiContextPolicy.lines(oldLines), original.messages)
            assertEquals(1L, ledger.snapshot().requests); assertEquals(1L, ledger.snapshot().models.single().reported)
            val sent = Json.parseToJsonElement(calls.single().sent.toString("UTF-8")).jsonObject
            assertEquals(JsonPrimitive(true), sent["stream"])
            assertEquals(JsonPrimitive("high"), sent.getValue("safety_check").jsonObject["level"])
        }
    }
    @Test fun nullOrEmptyRefusalAndNullErrorRemainCompatibleWithValidAnswer() {
        val base = Json.parseToJsonElement(aiAnswer("valid")).jsonObject
        for (refusal in listOf(JsonNull, JsonPrimitive(""))) {
            val choice = base.getValue("choices").jsonArray.single().jsonObject
            val message = JsonObject(choice.getValue("message").jsonObject + ("refusal" to refusal))
            AiResponsePolicy.validate(JsonObject(base + ("error" to JsonNull) +
                ("choices" to JsonArray(listOf(JsonObject(choice + ("message" to message)))))).toString())
        }
    }
    @Test fun missingUsageOnErrorStaysUnknownInsteadOfMeasuredZero() = runBlocking {
        var calls = 0; val ledger = AiUsageLedger()
        val safe = AiSafeTransport({ "fake-response-key" }, { true }, AiRequestBudget(), {
            calls++; FakeAiConnection(answer = response(error = JsonPrimitive(sentinel), usage = false))
        }, usage = AiUsageRecorder(listOf(ledger)))
        assertTrue(runCatching { safe.post("chat/completions", "{}", String::class, String::class) }.isFailure)
        assertEquals(1, calls); assertEquals(1L, ledger.snapshot().unknownChat)
        assertFalse(ledger.snapshot().models.single().hasReportedUsage)
    }
    @Test fun mixedSearchErrorNeverBecomesCitationOrSuccessfulSearchAndDoesNotRetry() = runBlocking {
        val raw = """{"error":{"message":"$sentinel"},"results":[{"url":"https://coffee.org/bean","title":"do not cite"}]}"""
        var calls = 0; val ledger = AiUsageLedger()
        val safe = AiSafeTransport({ "fake-response-key" }, { true }, AiRequestBudget(), { url ->
            assertEquals(AiSafeTransport.SEARCH_ENDPOINT, url.toString()); calls++
            FakeAiConnection(answer = raw, url = URL(AiSafeTransport.SEARCH_ENDPOINT))
        }, usage = AiUsageRecorder(listOf(ledger)))
        val result = AiWebSearch.execute("public coffee", safe::search)
        assertEquals(AiWebSearch.status("unavailable"), result); assertFalse(result.contains(sentinel))
        assertEquals(1, calls); assertEquals(1L, ledger.snapshot().unknownSearch)
        assertEquals(0L, ledger.snapshot().searchNonempty); assertEquals(0L, ledger.snapshot().searchEmpty)
    }
}
