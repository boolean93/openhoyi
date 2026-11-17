package io.openhoyi.mobile

import ai.koog.http.client.KoogHttpClient
import kotlin.reflect.KClass
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class AiChatSessionTest {
    private class Data : AiReadOnlyData {
        var reads = 0
        override fun currentStatus(): String { reads++; return "{\"status\":\"unknown\",\"sentinel\":\"PRIVATE_TOOL_SENTINEL\"}" }
        override fun curves(query: String, offset: Int, limit: Int) = "{\"items\":[]}"
        override fun history(curveId: String?, offset: Int, limit: Int) = "{\"items\":[]}"
    }
    @Test fun selectedHistorySamplesReachModelAndInvalidArgumentsNeverRead() = runBlocking {
        val reads = mutableListOf<String>()
        val data = object : AiReadOnlyData {
            override fun currentStatus() = "{}"
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int): String {
                reads += "history"
                return "{\"items\":[{\"shotId\":\"shot-1\",\"status\":\"UNKNOWN\"}]}"
            }
            override fun samples(shotId: String, offset: Int, limit: Int): String {
                reads += "$shotId:$offset:$limit"
                return "{\"status\":\"available\",\"items\":[{\"elapsedMs\":1234,\"pressureBar\":9.1}],\"truncated\":false}"
            }
        }
        fun call(name: String, args: JsonObject, id: String) = tool.replace("current_status", name)
            .replace("call1", id).replace("\"arguments\":\"{}\"", "\"arguments\":" + JsonPrimitive(args.toString()))
        val calls = CopyOnWriteArrayList<FakeAiConnection>(); val budget = AiRequestBudget()
        val answers = listOf(call("extraction_history", buildJsonObject {}, "history1"),
            call("extraction_samples", buildJsonObject { put("shotId", "../bad") }, "invalid1"),
            call("extraction_samples", buildJsonObject { put("shotId", "shot-1") }, "samples1"), aiAnswer("Saved pressure was 9.1 bar."))
        val answer = AiChatSession(data, transport(answers, budget, calls), budget, Dispatchers.Unconfined).reply(emptyList(), "Analyze the selected shot")
        assertEquals("Saved pressure was 9.1 bar.", answer)
        assertEquals(listOf("history", "shot-1:0:100"), reads)
        val finalWire = calls.last().sent.toString("UTF-8")
        assertTrue(finalWire.contains("pressureBar")); assertTrue(finalWire.contains("invalid_arguments"))
    }
    private val tool = """{"id":"fake","system_fingerprint":"fake","object":"chat.completion","created":1,"model":"deepseek-flash","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call1","type":"function","function":{"name":"current_status","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
    private fun transport(answers: List<String>, budget: AiRequestBudget, calls: MutableList<FakeAiConnection>) =
        AiSafeTransport({ "fake-test-only-key" }, { true }, budget, {
            FakeAiConnection(answer = answers.getOrElse(calls.size) { tool }).also(calls::add)
        })
    private suspend fun <T> privateLogs(block: suspend () -> T): T {
        val bytes = ByteArrayOutputStream(); val stream = PrintStream(bytes)
        val out = System.out; val err = System.err
        try { System.setOut(stream); System.setErr(stream); return block() }
        finally {
            System.setOut(out); System.setErr(err); stream.close()
            val logs = bytes.toString("UTF-8")
            for (sentinel in listOf("PRIVATE_USER_SENTINEL", "PRIVATE_TOOL_SENTINEL", "fake-test-only-key", "PRIVATE_ERROR_SENTINEL"))
                assertFalse("Private sentinel escaped through SDK/backend", logs.contains(sentinel))
        }
    }
    @Test fun curveCardsRequireActualSelectionToolAfterReadingCandidates() = runBlocking {
        val document = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000001", "Synthetic", 93, 3600,
            CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 100)))
        val item = CurveLibraryItem(document.id, document.name, "", "", null, customDocument = document)
        val collector = AiCurveCardCollector { id -> item.takeIf { it.id == id } }
        val data = object : AiReadOnlyData {
            override fun currentStatus() = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
            override fun curves(query: String, offset: Int, limit: Int) =
                org.json.JSONObject().put("status", "available").put("dataOnly", true).put("items", org.json.JSONArray().put(
                    org.json.JSONObject().put("id", document.id).put("origin", "local"))).toString()
        }
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>()
        val selectionArgs = buildJsonObject { put("ids_json", org.json.JSONArray().put(document.id).toString()) }
        val selectionTool = tool.replace("current_status", "present_curve_cards").replace("call1", "selection-call")
            .replace("\"arguments\":\"{}\"", "\"arguments\":" + JsonPrimitive(selectionArgs.toString()).toString())
        val result = AiChatSession(data, transport(listOf(tool.replace("current_status", "curves"), selectionTool, aiAnswer("Recommended because it fits")), budget, calls),
            budget, Dispatchers.Unconfined, onCurveResult = collector::captureToolResult, presentCurveCards = collector::select).reply(emptyList(), "Synthetic query")
        assertEquals("Recommended because it fits", result); assertEquals(3, calls.size)
        assertEquals(document.id, collector.snapshot().single().id)
        val queriedOnly = AiCurveCardCollector { item }
        val queryBudget = AiRequestBudget(); val queryCalls = CopyOnWriteArrayList<FakeAiConnection>()
        AiChatSession(data, transport(listOf(tool.replace("current_status", "curves"), aiAnswer("Found data only")), queryBudget, queryCalls),
            queryBudget, Dispatchers.Unconfined, onCurveResult = queriedOnly::captureToolResult, presentCurveCards = queriedOnly::select).reply(emptyList(), "Synthetic query")
        assertTrue(queriedOnly.snapshot().isEmpty())
        val proseOnly = AiCurveCardCollector { item }
        val secondBudget = AiRequestBudget(); val secondCalls = CopyOnWriteArrayList<FakeAiConnection>()
        AiChatSession(data, transport(listOf(aiAnswer("[curve](curve://${document.id})")), secondBudget, secondCalls),
            secondBudget, Dispatchers.Unconfined, onCurveResult = proseOnly::captureToolResult).reply(emptyList(), "Synthetic query")
        assertTrue(proseOnly.snapshot().isEmpty())
    }
    @Test fun twoSearchesThenCurveSelectionCanReachFinalAnswer() = runBlocking {
        fun call(name: String, args: JsonObject, id: String): String = tool
            .replace("current_status", name).replace("call1", id)
            .replace("\"arguments\":\"{}\"", "\"arguments\":" + JsonPrimitive(args.toString()).toString())
        val calls = CopyOnWriteArrayList<FakeAiConnection>(); val budget = AiRequestBudget()
        val answers = listOf(
            call("web_search", buildJsonObject { put("query", "synthetic bean producer") }, "search1"),
            call("web_search", buildJsonObject { put("query", "synthetic bean roast") }, "search2"),
            call("curves", buildJsonObject {}, "curves1"),
            call("present_curve_cards", buildJsonObject { put("ids_json", "[]") }, "select1"),
            aiAnswer("No exact bean evidence; here is a cautious recommendation."))
        var searches = 0; var selections = 0
        val answer = AiChatSession(Data(), transport(answers, budget, calls), budget, Dispatchers.Unconfined,
            webSearch = { searches++; "{\"results\":[]}" },
            presentCurveCards = { selections++; "{\"status\":\"ok\",\"writes\":false}" })
            .reply(emptyList(), "Search a bean and recommend a curve")
        assertTrue(answer.contains("cautious recommendation"))
        assertEquals(5, calls.size); assertEquals(2, searches); assertEquals(1, selections)
    }
    @Test fun thinkingEffortIsSentForBothModelsAndEveryToolRound() = runBlocking { privateLogs {
        for (model in AiModelPolicy.models) for (effort in AiReasoningEffort.entries) {
            val calls = CopyOnWriteArrayList<FakeAiConnection>(); val budget = AiRequestBudget()
            AiChatSession(Data(), transport(listOf(tool, aiAnswer("done")), budget, calls), budget,
                Dispatchers.Unconfined, modelId = model.id, reasoningEffort = effort).reply(emptyList(), "status")
            assertEquals(2, calls.size)
            calls.forEach {
                val body = Json.parseToJsonElement(it.sent.toString("UTF-8")).jsonObject
                assertEquals(model.id, body["model"]?.jsonPrimitive?.content)
                assertEquals(effort.apiValue, body["reasoning_effort"]?.jsonPrimitive?.content)
                assertEquals("enabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
                assertEquals("high", body["safety_check"]?.jsonObject?.get("level")?.jsonPrimitive?.content)
            }
        }
        assertEquals(AiReasoningEffort.LOW, AiReasoningEffort.fromStored("unsupported"))
        AiReasoningEffort.entries.forEach { assertEquals(it, AiReasoningEffort.fromStored(it.apiValue)) }
    } }

    @Test fun actualKoogAgentDispatchesBoundedToolAndPreservesConversation() = runBlocking { privateLogs {
        val calls = CopyOnWriteArrayList<FakeAiConnection>(); val budget = AiRequestBudget(); val data = Data()
        val answer = AiChatSession(data, transport(listOf(tool, aiAnswer("unknown, not connected")), budget, calls), budget, Dispatchers.Unconfined)
            .reply(listOf(AiChatLine(true, "prior question"), AiChatLine(false, "prior answer")), "PRIVATE_USER_SENTINEL")
        assertEquals("unknown, not connected", answer); assertEquals(2, calls.size); assertEquals(1, data.reads)
        calls.forEach {
            val body = Json.parseToJsonElement(it.sent.toString("UTF-8")).jsonObject
            assertEquals("deepseek-flash", body["model"]?.jsonPrimitive?.content)
            assertEquals("enabled", body["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
            assertEquals("high", body["safety_check"]?.jsonObject?.get("level")?.jsonPrimitive?.content)
            assertFalse(body.toString().contains("fake-test-only-key"))
            assertEquals("Bearer fake-test-only-key", it.requestHeaders["Authorization"])
            assertTrue(body["messages"].toString().contains("prior answer"))
            val names = body["tools"]!!.jsonArray.map { t -> t.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
            val selectionDescription = body["tools"]!!.jsonArray.single {
                it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content == "present_curve_cards"
            }.jsonObject["function"]!!.jsonObject["description"]!!.jsonPrimitive.content
            listOf("Default to exactly one best-fit card unless the user explicitly asks for more",
                "including counts above three",
                "per-turn card resource limit is 10",
                "Card prose: brief reasons/necessary reminders only",
                "Do not duplicate displayed recipe data (temperature, weight, stage pressure/flow/water)",
                "parameter lists, generic closings or needless questions",
                "Explain parameters/reasons/comparisons when asked",
                "Retain uncertainty, safety limits and required confirmation").forEach { rule ->
                assertTrue("Missing selection rule: $rule", selectionDescription.contains(rule))
            }
            assertFalse(selectionDescription.contains("1–3 best fits"))
            assertEquals(setOf("current_status", "curves", "extraction_history", "extraction_samples", "web_search", "propose_custom_curve", "custom_curve_document", "present_curve_cards"), names)
        }
        assertTrue(calls.last().sent.toString("UTF-8").contains("PRIVATE_TOOL_SENTINEL"))
        val freshCalls = CopyOnWriteArrayList<FakeAiConnection>(); val freshBudget = AiRequestBudget()
        assertEquals("fresh", AiChatSession(data, transport(listOf(aiAnswer("fresh")), freshBudget, freshCalls), freshBudget, Dispatchers.Unconfined)
            .reply(emptyList(), "new conversation"))
        val freshBody = freshCalls.single().sent.toString("UTF-8")
        assertFalse(freshBody.contains("PRIVATE_TOOL_SENTINEL")); assertFalse(freshBody.contains("prior answer"))
        assertEquals(1, data.reads)
    }
    }
    @Test fun proposalToolDispatchesWithoutWritingAndNeverExposesApprovalCapability() = runBlocking {
        val disk = object : CustomCurveStore.Storage {
            var writes = 0
            override fun read(): String? = null
            override fun write(value: String) { writes++ }
        }
        val coordinator = AiCurveWriteCoordinator(CustomCurveStore(disk))
        val epoch = AiCurveWriteCoordinator.Epoch("test-session", 1)
        val proposal = """{"operation":"create","curve":{"schema":1,"kind":"openhoyi.local-curve","name":"Proposed","temperatureC":93,"targetHundredthsGram":3600,"controlMode":"pressure","stages":[{"target":30,"waterTenthsMl":100},{"target":90,"waterTenthsMl":400}]}}"""
        val padded = proposal + " ".repeat(700) // A proposal has its own bound, not the read tool's 512 bytes.
        val args = org.json.JSONObject().put("proposal_json", padded).toString()
        val response = org.json.JSONObject(tool).also {
            it.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls")
                .getJSONObject(0).getJSONObject("function").put("name", "propose_custom_curve").put("arguments", args)
        }.toString()
        val calls = CopyOnWriteArrayList<FakeAiConnection>(); val budget = AiRequestBudget()
        val answer = AiChatSession(Data(), transport(listOf(response, aiAnswer("Awaiting your review")), budget, calls), budget,
            Dispatchers.Unconfined, proposeCurve = { coordinator.propose(it, epoch); AiCurveProposalStatus.AWAITING_EDITOR })
            .reply(emptyList(), "Create a curve")
        assertEquals("Awaiting your review", answer); assertEquals(0, disk.writes)
        val pending = requireNotNull(coordinator.pending(epoch))
        val sent = calls.last().sent.toString("UTF-8")
        assertTrue(sent.contains("awaiting_editor")); assertFalse(sent.contains(pending.token))
        val tools = Json.parseToJsonElement(sent).jsonObject["tools"]!!.jsonArray.toString()
        assertFalse(tools.contains("confirm_custom")); assertFalse(tools.contains("approved"))
    }
    @Test fun proposalRejectsModelApprovalAndOversizeBeforeCallback() = runBlocking {
        for (arguments in listOf("""{"proposal_json":"{}","approved":true}""",
            org.json.JSONObject().put("proposal_json", "x".repeat(8193)).toString())) {
            val response = org.json.JSONObject(tool).also {
                it.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls")
                    .getJSONObject(0).getJSONObject("function").put("name", "propose_custom_curve").put("arguments", arguments)
            }.toString()
            val calls = CopyOnWriteArrayList<FakeAiConnection>(); val budget = AiRequestBudget(); var proposed = 0
            AiChatSession(Data(), transport(listOf(response, aiAnswer("Invalid proposal")), budget, calls), budget,
                Dispatchers.Unconfined, proposeCurve = { proposed++; AiCurveProposalStatus.AWAITING_EDITOR }).reply(emptyList(), "Create")
            assertEquals(0, proposed); assertTrue(calls.last().sent.toString("UTF-8").contains("invalid_arguments"))
        }
    }
    @Test fun officialSafetyCompletionBecomesRefusedNotAnswerOrNetworkError() = runBlocking {
        val refusal = """{"choices":[{"index":0,"message":{"role":"assistant","content":""},"finish_reason":"content_filter"}]}"""
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>(); val data = Data()
        val error = runCatching { AiChatSession(data, transport(listOf(refusal), budget, calls), budget, Dispatchers.Unconfined).reply(emptyList(), "hello") }.exceptionOrNull()
        assertTrue(error is AiFailure); assertEquals(AiProblem.REFUSED, (error as AiFailure).problem)
        assertNull(error.cause); assertEquals(0, data.reads); assertEquals(1, calls.size)
    }
    @Test fun normalPaginatedCurveResearchAboveTwelveThousandCharactersStillAnswers() = runBlocking {
        val factory = FactoryCurve("factory", "medium", "Synthetic", "", "icon", 100,
            360, 5, 3, 93, true, true, 4, listOf(30, 25, 20, 15), listOf(10, 30, 30, 30), 5)
        val rows = List(40) { index -> AiReadOnlyQuery.fromFactory(factory).copy(id = "factory-$index") }
        val pages = mutableListOf<String>()
        val data = object : AiReadOnlyData {
            override fun currentStatus() = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
            override fun curves(query: String, offset: Int, limit: Int) =
                AiReadOnlyQuery.curves(rows, query, offset, limit).toString().also(pages::add)
        }
        var offset = 0
        val responses = List(3) { index ->
            val page = AiReadOnlyQuery.curves(rows, "", offset, 10)
            val args = buildJsonObject { put("offset", offset); put("limit", 10) }
            offset = page.getInt("nextOffset")
            tool.replace("current_status", "curves").replace("call1", "page${index + 1}")
                .replace("\"arguments\":\"{}\"", "\"arguments\":" + JsonPrimitive(args.toString()).toString())
        } + aiAnswer("Compared all returned recipes without dropping fields.")
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>()
        val answer = AiChatSession(data, transport(responses, budget, calls), budget, Dispatchers.Unconfined)
            .reply(emptyList(), "Find the best matching saved curve")
        assertTrue(pages.sumOf { it.length } > 12000)
        pages.forEach { page ->
            assertTrue(page.length <= 6000)
            val json = org.json.JSONObject(page)
            assertEquals(4, json.getJSONArray("items").getJSONObject(0).getJSONObject("recipe").getJSONArray("stages").length())
        }
        assertEquals(4, calls.size)
        assertTrue(answer.contains("without dropping fields"))
    }
    @Test fun twelveSequentialToolsStillReachFinalAnswerWithinGraphBudget() = runBlocking {
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>(); val data = Data()
        val responses = List(12) { index -> tool.replace("call1", "call${index + 1}") } + aiAnswer("done")
        assertEquals("done", AiChatSession(data, transport(responses, budget, calls), budget, Dispatchers.Unconfined)
            .reply(emptyList(), "bounded synthetic tools"))
        assertEquals(12, data.reads); assertEquals(13, calls.size)
    }
    @Test fun graphBudgetFailureIsSpecificNotNetworkUnknown() = runBlocking {
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>()
        val failure = runCatching { AiChatSession(Data(), transport(emptyList(), budget, calls), budget,
            Dispatchers.Unconfined, maxAgentIterations = 1).reply(emptyList(), "synthetic") }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.TOOL_LIMIT, failure.problem)
        assertEquals(AiFailureKind.AGENT_ITERATION_BUDGET, failure.diagnostic?.kind)
        assertEquals(AiStage.BUDGET, failure.diagnostic?.stage)
    }
    @Test fun endlessToolLoopStillStopsAtIndependentToolBudget() = runBlocking {
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>()
        val error = runCatching { AiChatSession(Data(), transport(emptyList(), budget, calls), budget, Dispatchers.Unconfined).reply(emptyList(), "status") }.exceptionOrNull()
        assertEquals(AiProblem.TOOL_LIMIT, (error as AiFailure).problem); assertEquals(13, calls.size); assertEquals(AiFailureKind.TOOL_CALL_BUDGET, error.diagnostic?.kind); assertNull(error.cause)
    }
    @Test fun providerErrorBodyIsNotLoggedOrReturnedByActualAgent() = runBlocking { privateLogs {
        val budget = AiRequestBudget()
        val connection = FakeAiConnection(401, "PRIVATE_ERROR_SENTINEL fake-test-only-key PRIVATE_USER_SENTINEL")
        val safe = AiSafeTransport({ "fake-test-only-key" }, { true }, budget, { connection })
        val error = runCatching { AiChatSession(Data(), safe, budget, Dispatchers.Unconfined).reply(emptyList(), "PRIVATE_USER_SENTINEL") }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.AUTH, error.problem); assertNull(error.cause); assertFalse(connection.errorRead)
    } }
    @Test fun unavailableDataDoesNotBreakOrdinaryChatOrInventReadings() = runBlocking { privateLogs {
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>()
        val failingData = object : AiReadOnlyData {
            override fun currentStatus(): String = error("PRIVATE_TOOL_SENTINEL")
            override fun curves(query: String, offset: Int, limit: Int): String = error("not used")
            override fun history(curveId: String?, offset: Int, limit: Int): String = error("not used")
        }
        assertEquals("unknown", AiChatSession(failingData, transport(listOf(tool, aiAnswer("unknown")), budget, calls), budget, Dispatchers.Unconfined).reply(emptyList(), "hello"))
        val second = calls[1].sent.toString("UTF-8")
        assertTrue(second.contains("unavailable")); assertFalse(second.contains("PRIVATE_TOOL_SENTINEL"))
    } }
    @Test fun naturalTimeoutIsVisibleButExplicitStopRemainsCancellation() = runBlocking {
        val budget = AiRequestBudget(); val calls = CopyOnWriteArrayList<FakeAiConnection>()
        val base = transport(emptyList(), budget, calls)
        val began = AtomicBoolean()
        val waiting = object : KoogHttpClient by base {
            override suspend fun <T : Any, R : Any> post(path: String, requestBody: T, requestBodyType: KClass<T>,
                responseType: KClass<R>, parameters: Map<String, String>, headers: Map<String, String>): R {
                began.set(true)
                awaitCancellation()
            }
        }
        val error = runCatching { AiChatSession(Data(), waiting, budget, Dispatchers.Unconfined, 50).reply(emptyList(), "hello") }.exceptionOrNull()
        assertEquals(AiProblem.NETWORK, (error as AiFailure).problem)
        assertNull(error.cause)
        began.set(false)
        val job = async { AiChatSession(Data(), waiting, budget, Dispatchers.Unconfined).reply(emptyList(), "hello") }
        while (!began.get()) delay(1)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(calls.isEmpty())
    }
    @Test fun providerRefusalAndTruncatedOutputsNeverBecomeNormalAnswers() {
        for (message in listOf(
            """{"message":{"content":"not to show","refusal":"private refusal"},"finish_reason":"stop"}""",
            """{"message":{"content":"partial"},"finish_reason":"content_filter"}""")) {
            val error = runCatching { AiResponsePolicy.validate("{\"choices\":[$message]}") }.exceptionOrNull() as AiFailure
            assertEquals(AiProblem.REFUSED, error.problem); assertNull(error.cause)
        }
        AiResponsePolicy.validate("""{"choices":[{"message":{"content":"partial"},"finish_reason":"length"}]}""") // Adapter handles incomplete replies before SDK dispatch.
    }
}
