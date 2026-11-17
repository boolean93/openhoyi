package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiContextPolicyTest {
    private fun msg(role: String, text: String) = buildJsonObject { put("role", role); put("content", text) }
    private val lines = listOf(AiChatLine(true, "old question"), AiChatLine(false, "old answer"),
        AiChatLine(true, "recent question"), AiChatLine(false, "recent answer"))
    private fun history(usage: Long? = null) = AiProtocolHistory.checked(AiContextPolicy.lines(lines), reportedInput = usage)
    private val data = object : AiReadOnlyData {
        override fun currentStatus(): String = error("Truncated tools must never execute")
        override fun curves(query: String, offset: Int, limit: Int) = "{}"
        override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
    }
    @Test fun supportsLongInputsAndMoreThanTwelveTurnsWithExplicitStorageBounds() {
        assertTrue(AiChatPolicy.accepts(List(100) { AiChatLine(it % 2 == 0, "coffee") }, "中".repeat(10000)))
        assertFalse(AiChatPolicy.accepts(emptyList(), "x".repeat(AiChatPolicy.INPUT_CHARS + 1)))
        assertFalse(AiChatPolicy.accepts(listOf(AiChatLine(false, "x".repeat(AiContextPolicy.TRANSCRIPT_CHARS))), "next"))
        assertEquals(262144, AiContextPolicy.WORKING_TOKENS)
        assertEquals(200000, AiContextPolicy.COMPACT_AT)
        val offset = AiContextPolicy.projected(history(150000), "next") - 150000
        assertFalse(AiContextPolicy.needsCompact(history(200000 - offset - 1), "next"))
        assertTrue(AiContextPolicy.needsCompact(history(200000 - offset), "next"))
        assertTrue(AiContextPolicy.estimate("中".repeat(100)) > AiContextPolicy.estimate("a".repeat(100)))
    }
    @Test fun compactionIsOneNoToolsCallPreservesVisibleHistoryAndRecentProtocol() = runBlocking {
        val calls = mutableListOf<FakeAiConnection>()
        val transport = AiSafeTransport({ "fake-summary-key" }, { true }, AiRequestBudget(), {
            FakeAiConnection(answer = aiAnswer("Untrusted earlier facts; pending actions remain unconfirmed.")).also(calls::add)
        })
        val original = history(200000)
        val result = AiContextCompactor.compact(original, lines, "next", transport)
        assertEquals(1, calls.size); assertEquals(2, result.coveredLines)
        assertEquals(original.messages.drop(2), result.messages)
        assertEquals(4, original.messages.size); assertEquals(4, lines.size)
        assertTrue(AiContextPolicy.matches(result, lines)); assertNull(result.reportedInput)
        val body = Json.parseToJsonElement(calls.single().sent.toString("UTF-8")).jsonObject
        assertFalse(body.containsKey("tools")); assertFalse(body.containsKey("tool_choice"))
        assertEquals("disabled", body.getValue("thinking").jsonObject["type"]?.jsonPrimitive?.content)
        val again = AiContextCompactor.compact(result, lines, "next", transport)
        assertSame(result, again); assertEquals(1, calls.size)
    }
    @Test fun failedSummaryKeepsOriginalAndNeverRetries() = runBlocking {
        for (response in listOf(aiAnswer("").replace("\"stop\"", "\"length\""), "{}", aiAnswer("partial").replace("\"stop\"", "\"length\""))) {
            var calls = 0
            val transport = AiSafeTransport({ "fake-summary-key" }, { true }, AiRequestBudget(), {
                calls++; FakeAiConnection(answer = response)
            })
            val original = history(200000)
            val failure = runCatching { AiContextCompactor.compact(original, lines, "next", transport) }.exceptionOrNull() as AiFailure
            assertEquals(AiProblem.COMPACT_FAILED, failure.problem)
            assertEquals(1, calls); assertNull(original.summary); assertEquals(4, original.messages.size)
        }
    }
    @Test fun recentToolTransactionIsRetainedExactly() = runBlocking {
        val call = buildJsonObject { put("role", "assistant"); put("content", JsonNull); put("reasoning_content", "PRIVATE_REASON")
            put("tool_calls", buildJsonArray { add(buildJsonObject { put("id", "c"); put("type", "function"); put("function", buildJsonObject { put("name", "current_status"); put("arguments", "{}") }) }) }) }
        val result = buildJsonObject { put("role", "tool"); put("content", "{}") ; put("tool_call_id", "c") }
        val messages = AiContextPolicy.lines(lines).let { it.take(3) + call + result + it.last() }
        val original = AiProtocolHistory.checked(messages, reportedInput = 200000)
        val transport = AiSafeTransport({ "fake-summary-key" }, { true }, AiRequestBudget(), { FakeAiConnection(answer = aiAnswer("old facts")) })
        val compacted = AiContextCompactor.compact(original, lines, "next", transport)
        assertEquals(messages.drop(2), compacted.messages)
        assertEquals("PRIVATE_REASON", compacted.messages[1]["reasoning_content"]?.jsonPrimitive?.content)
    }
    @Test fun truncatedTextAndToolsStopBeforeDispatchAndSurvivePersistence() = runBlocking {
        val root = Json.parseToJsonElement(aiAnswer("safe partial")).jsonObject
        val choice = root.getValue("choices").jsonArray.first().jsonObject
        val message = JsonObject(choice.getValue("message").jsonObject + ("reasoning_content" to JsonPrimitive("PRIVATE_REASON")) +
            ("tool_calls" to buildJsonArray { add(buildJsonObject { put("id", "unfinished"); put("type", "function"); put("function", buildJsonObject { put("name", "current_status"); put("arguments", "{") }) }) }))
        val response = JsonObject(root + ("choices" to JsonArray(listOf(JsonObject(choice + ("message" to message) + ("finish_reason" to JsonPrimitive("length"))))))).toString()
        var calls = 0
        val budget = AiRequestBudget()
        val safe = AiSafeTransport({ "fake-length-key" }, { true }, budget, { calls++; FakeAiConnection(answer = response) })
        val turn = AiProtocolTurn(AiProtocolHistory.empty(), emptyList(), "question", safe)
        val failure = runCatching { AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(emptyList(), "question") }.exceptionOrNull() as AiPartialCompletion
        assertEquals(1, calls); assertTrue(failure.reply.text.startsWith("safe partial")); assertTrue(failure.reply.text.contains("Incomplete response"))
        assertFalse(failure.reply.text.contains("PRIVATE_REASON")); assertFalse(failure.toString().contains("safe partial"))
        assertFalse(failure.reply.history.messages.last().containsKey("tool_calls"))
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(AiSession("00000000-0000-0000-0000-000000000000",
            listOf(AiChatLine(true, "question"), AiChatLine(false, failure.reply.text)), failure.reply.history, "draft", AiUsageSnapshot())))
        assertEquals(failure.reply.text, restored.lines.last().text); assertEquals("draft", restored.draft)
        val nextCalls = mutableListOf<FakeAiConnection>()
        val nextBudget = AiRequestBudget()
        val nextTransport = AiSafeTransport({ "fake-next-key" }, { true }, nextBudget, { FakeAiConnection(answer = aiAnswer("done")).also(nextCalls::add) })
        val nextTurn = AiProtocolTurn(restored.protocol, restored.lines, "continue", nextTransport)
        val answer = AiChatSession(data, nextTurn, nextBudget, Dispatchers.Unconfined).reply(restored.lines, "continue")
        assertEquals("done", nextTurn.complete(answer).text)
        val replay = Json.parseToJsonElement(nextCalls.single().sent.toString("UTF-8")).jsonObject.getValue("messages").jsonArray[2].jsonObject
        assertEquals("PRIVATE_REASON", replay["reasoning_content"]?.jsonPrimitive?.content)
        assertFalse(replay.containsKey("tool_calls"))
    }
    @Test fun compactedSessionRoundTripsAndLegacySchemaStillLoads() {
        val compacted = AiProtocolHistory.checked(AiContextPolicy.lines(lines.drop(2)), summary = "old facts", coveredLines = 2)
        val state = AiSession("00000000-0000-0000-0000-000000000000", lines, compacted, "draft", AiUsageSnapshot())
        val restored = AiSessionCodec.decode(AiSessionCodec.encode(state))
        assertEquals(lines, restored.lines); assertEquals("old facts", restored.protocol.summary); assertEquals(2, restored.protocol.coveredLines)
        val original = AiSession(state.epoch, lines, history(), "draft", AiUsageSnapshot())
        val json = Json.parseToJsonElement(AiSessionCodec.encode(original).toString(Charsets.UTF_8)).jsonObject
        val legacy = JsonObject((json - "context" - "draftAttachments" - "model" - "inactive" - "ocr" - "hoyiScreenshotSourceId") + ("schema" to JsonPrimitive(1)) + ("lines" to JsonArray(json.getValue("lines").jsonArray.map { JsonObject(it.jsonObject - "attachments" - "curveCards") }))).toString().toByteArray()
        assertEquals(lines, AiSessionCodec.decode(legacy).lines)
        val mismatch = JsonObject(json + ("context" to buildJsonObject { put("summary", "bad"); put("coveredLines", 2); put("reportedInput", JsonNull); put("reportedOutput", 0) }))
        assertTrue(runCatching { AiSessionCodec.decode(mismatch.toString().toByteArray()) }.isFailure)
    }
    @Test fun oversizedNewInputNeverPaysForFutileCompaction() = runBlocking {
        var calls = 0
        val transport = AiSafeTransport({ "fake-summary-key" }, { true }, AiRequestBudget(), { calls++; FakeAiConnection(answer = aiAnswer("summary")) })
        val large = "中".repeat(70000)
        val h = history()
        assertSame(h, AiContextCompactor.compact(h, lines, large, transport))
        assertEquals(0, calls)
        val error = runCatching { AiContextCompactor.compact(h, lines, "中".repeat(90000), transport) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.CONTEXT_LIMIT, error.problem); assertEquals(0, calls)
        val bigRecentLines = lines.take(2) + AiChatLine(true, "中".repeat(35000)) + lines.last()
        val bigRecent = AiProtocolHistory.checked(AiContextPolicy.lines(bigRecentLines))
        assertSame(bigRecent, AiContextCompactor.compact(bigRecent, bigRecentLines, "中".repeat(35000), transport))
        assertEquals(0, calls)
    }

    @Test fun checkpointSurvivesFailedAnswerAndRestoreDoesNotRepeatPaidSummary() = runBlocking {
        var calls = 0
        var saved: ByteArray? = null
        val transport = AiSafeTransport({ "fake-summary-key" }, { true }, AiRequestBudget(), { calls++; FakeAiConnection(answer = aiAnswer("old facts")) })
        val h = AiContextCompactor.prepare(history(200000), lines, "next", transport) { compacted ->
            saved = AiSessionCodec.encode(AiSession("00000000-0000-0000-0000-000000000000", lines, compacted, "next", AiUsageSnapshot()))
            true
        }
        val budget = AiRequestBudget()
        val failed = AiSafeTransport({ "fake-answer-key" }, { true }, budget, { FakeAiConnection(401, "private") })
        val turn = AiProtocolTurn(h, lines, "next", failed)
        assertTrue(runCatching { AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(lines.drop(h.coveredLines), "next") }.isFailure)
        val recovered = AiSessionCodec.decode(saved!!)
        assertEquals(lines, recovered.lines); assertEquals("next", recovered.draft)
        AiContextCompactor.prepare(recovered.protocol, recovered.lines, recovered.draft, transport) { error("Must not checkpoint again") }
        assertEquals(1, calls)
        val error = runCatching { AiContextCompactor.prepare(history(200000), lines, "next", transport) { false } }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.STORAGE_LIMIT, error.problem)
    }

    @Test fun providerUsageNeverLowersFullLocalProtocolBudget() {
        val large = listOf(msg("user", "q"), JsonObject(msg("assistant", "answer") +
            ("reasoning_content" to JsonPrimitive("r".repeat(195000)))))
        val local = AiProtocolHistory.checked(large)
        val reportedSmall = AiProtocolHistory.checked(large, reportedInput = 1, reportedOutput = 1)
        assertEquals(AiContextPolicy.projected(local, "next"), AiContextPolicy.projected(reportedSmall, "next"))
        assertTrue(AiContextPolicy.needsCompact(reportedSmall, "next"))
        val high = AiProtocolHistory.checked(large, reportedInput = 250000)
        assertTrue(AiContextPolicy.projected(high, "next") > AiContextPolicy.projected(local, "next"))
        assertEquals(262144, AiContextPolicy.INPUT_TOKENS + AiContextPolicy.OUTPUT_TOKENS + AiContextPolicy.TOOL_RESERVE_TOKENS)
    }

    @Test fun schemaCrossingEstimatedThresholdTriggersOneSafeCompaction() = runBlocking {
        val visible = listOf(AiChatLine(true, "x".repeat(180000)), AiChatLine(false, "old")) + lines.takeLast(2)
        val original = AiProtocolHistory.checked(AiContextPolicy.lines(visible), reportedInput = 1)
        val schema = AiContextPolicy.COMPACT_AT - AiContextPolicy.projected(original, "next", schemaTokens = 0)
        assertTrue(schema > 0)
        assertFalse(AiContextPolicy.needsCompact(original, "next", schemaTokens = schema - 1))
        assertTrue(AiContextPolicy.needsCompact(original, "next", schemaTokens = schema))
        var calls = 0
        val safe = AiSafeTransport({ "fake-schema-key" }, { true }, AiRequestBudget(), {
            calls++; FakeAiConnection(answer = aiAnswer("Old facts; pending writes remain unconfirmed."))
        })
        val compacted = AiContextCompactor.compact(original, visible, "next", safe, schemaTokens = schema)
        assertEquals(1, calls); assertEquals(original.messages.drop(2), compacted.messages)
        assertEquals(4, visible.size); assertTrue(AiContextPolicy.matches(compacted, visible))
    }

    @Test fun registrySchemaBudgetCoversActualSdkToolsWithoutExecutingThem() = runBlocking {
        val calls = mutableListOf<FakeAiConnection>(); val budget = AiRequestBudget()
        val transport = AiSafeTransport({ "fake-schema-key" }, { true }, budget, { FakeAiConnection(answer = aiAnswer("answer")).also(calls::add) })
        val session = AiChatSession(data, transport, budget, Dispatchers.Unconfined)
        val schema = session.toolSchemaTokens()
        assertTrue(calls.isEmpty()); assertTrue(schema > 2048); assertTrue(schema <= AiContextPolicy.DEFAULT_SCHEMA_TOKENS)
        session.reply(emptyList(), "question")
        val root = Json.parseToJsonElement(calls.single().sent.toString("UTF-8")).jsonObject
        val actual = AiContextPolicy.estimateJson(root.getValue("tools"))
        assertTrue(schema >= actual)
        assertEquals(8, root.getValue("tools").jsonArray.size)
    }

}
