package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class AiSseCompletionTest {
    private fun frame(delta: String, finish: String = "null", usage: String = "") =
        "data: {\"id\":\"a\",\"model\":\"deepseek-flash\",\"choices\":[{\"index\":0,\"delta\":$delta,\"finish_reason\":$finish}]$usage}\r\n\r\n"
    private fun read(raw: String, max: Int = 131072, emit: (AiStreamDelta) -> Unit = {}) =
        AiSseCompletion.read(ByteArrayInputStream(raw.toByteArray()), max, {}, emit)
    @Test fun reasoningUtf8CommentsCrLfUsageAndDone() {
        val events = mutableListOf<AiStreamDelta>()
        val raw = ": keep-alive\r\n\r\n" + frame("""{"reasoning_content":"思考"}""") +
            frame("""{"content":"你好"}""", "\"stop\"", ",\"usage\":{\"completion_tokens\":2}") + "data: [DONE]\r\n\r\n"
        val root = Json.parseToJsonElement(read(raw, emit = events::add)).jsonObject
        val message = root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
        assertEquals("思考", message["reasoning_content"]!!.jsonPrimitive.content)
        assertEquals("你好", message["content"]!!.jsonPrimitive.content)
        assertEquals(2, events.size)
        assertTrue(events[0] is AiStreamDelta.Reasoning)
        assertEquals(2, root["usage"]!!.jsonObject["completion_tokens"]!!.jsonPrimitive.int)
        assertEquals("AiStreamDelta", events[0].toString())
    }
    @Test fun toolsAreAssembledBeforeKoogAndReasoningPreserved() {
        val raw = frame("""{"reasoning_content":"r","tool_calls":[{"index":0,"id":"t","type":"function","function":{"name":"read","arguments":"{\"x\":"}}]}""") +
            frame("""{"tool_calls":[{"index":0,"function":{"arguments":"1}"}}]}""", "\"tool_calls\"") + "data: [DONE]\n\n"
        val message = Json.parseToJsonElement(read(raw)).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
        assertEquals("r", message["reasoning_content"]!!.jsonPrimitive.content)
        assertEquals("{\"x\":1}", message["tool_calls"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
    }
    @Test fun refusalAndFilterStopBeforeContentEvenWithoutDone() {
        for (raw in listOf(
            frame("""{"refusal":"no","content":"must not emit"}"""),
            frame("""{"content":"must not emit"}""", "\"content_filter\""),
            frame("""{"content":"earlier body"}""") + frame("""{"refusal":"no"}"""))) {
            val events = mutableListOf<AiStreamDelta>()
            val error = runCatching { read(raw, emit = events::add) }.exceptionOrNull() as AiFailure
            assertEquals(AiProblem.REFUSED, error.problem)
            assertTrue(events.filterIsInstance<AiStreamDelta.Content>().none { it.text == "must not emit" })
            val preview = AiStreamPreview()
            events.forEach(preview::append)
            // Host refusal path clears, unlike recoverable interrupted previews.
            preview.clear()
            assertEquals("", preview.snapshot().content)
        }
    }
    @Test fun truncatedErrorMissingTerminalAndBoundsFailClosed() {
        for (raw in listOf(frame("{}"), "data: [DONE]\n\n", "data: {\"error\":{}}\n\n", frame("{}", "\"stop\"") + "data: {broken}\n\n"))
            assertTrue(runCatching { read(raw) }.exceptionOrNull() is AiFailure)
        assertEquals(AiProblem.RESPONSE_LIMIT, (runCatching { read("data: " + "x".repeat(100), 32) }.exceptionOrNull() as AiFailure).problem)
    }
    @Test fun differentCompletionUnknownDeltaAndIncompleteToolsFailClosed() {
        val first = frame("{}")
        val changed = frame("{}", "\"stop\"").replace("\"id\":\"a\"", "\"id\":\"b\"")
        val invalid = listOf(first + changed + "data: [DONE]\n\n",
            frame("""{"server_tool_calls":[]}""", "\"stop\"") + "data: [DONE]\n\n",
            frame("""{"tool_calls":[{"index":0,"id":"t","type":"function"}]}""", "\"tool_calls\"") + "data: [DONE]\n\n")
        invalid.forEach { assertTrue(runCatching { read(it) }.exceptionOrNull() is AiFailure) }
    }
    @Test fun toolOnlyDefaultsContentToNullAndUsageOnlyChunkIsAccepted() {
        val raw = frame("{}", "\"stop\"") + "data: {\"choices\":[],\"usage\":{\"completion_tokens\":1}}\n\ndata: [DONE]\n\n"
        val root = Json.parseToJsonElement(read(raw)).jsonObject
        assertEquals(JsonNull, root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"])
        assertNotNull(root["usage"])
    }
    @Test fun multilineDataAndLengthWithPartialToolRemainSafe() {
        val raw = frame("""{"content":"partial","tool_calls":[{"index":0,"function":{"arguments":"{"}}]}""", "\"length\"")
            .replace("\"choices\":", "\n data: \"choices\":")
        // SSE multiple data lines join with a newline, not by concatenating arbitrary bytes.
        val joined = raw.replace("\n data:", "\ndata:") + "data: [DONE]\n\n"
        val root = Json.parseToJsonElement(read(joined)).jsonObject
        assertEquals("length", root["choices"]!!.jsonArray[0].jsonObject["finish_reason"]!!.jsonPrimitive.content)
    }
    @Test fun wireOverheadIsSeparateFromPayloadButStillBounded() {
        val raw = (1..20).joinToString("") { ": keep-alive\n\n" } + frame("""{"content":"ok"}""", "\"stop\"") + "data: [DONE]\n\n"
        assertTrue(read(raw, 256).contains("ok"))
        val failure = runCatching { read(":x\n".repeat(200), 4) }.exceptionOrNull() as AiFailure
        assertEquals(AiProblem.RESPONSE_LIMIT, failure.problem)
    }
    @Test fun cancellationStopsBeforeAnyProgress() {
        var emitted = false
        val failure = runCatching { AiSseCompletion.read(ByteArrayInputStream(frame("""{"content":"x"}""").toByteArray()), 1024,
            { throw CancellationException() }, { emitted = true }) }.exceptionOrNull()
        assertTrue(failure is CancellationException); assertFalse(emitted)
    }

    @Test fun fullChineseAnswerAndReasoningNoLongerShareSmall128KiBCap() {
        val answer = "中".repeat(64_000)
        val reasoning = "r".repeat(100_000)
        val raw = frame(buildJsonObject { put("reasoning_content", reasoning) }.toString()) +
            frame(buildJsonObject { put("content", answer) }.toString(), "\"stop\"") + "data: [DONE]\n\n"
        val message = Json.parseToJsonElement(read(raw, AiContextPolicy.RESPONSE_BYTES)).jsonObject[
            "choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
        assertEquals(answer, message["content"]!!.jsonPrimitive.content)
        assertEquals(reasoning, message["reasoning_content"]!!.jsonPrimitive.content)
    }
    @Test fun escapedBodyAndLargeToolArgumentsRemainExactNotTruncated() {
        val text = "\u0001".repeat(64_000)
        val raw = frame(buildJsonObject { put("content", text) }.toString(), "\"stop\"") + "data: [DONE]\n\n"
        val response = read(raw, AiContextPolicy.RESPONSE_BYTES)
        assertEquals(text, Json.parseToJsonElement(response).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        val arguments = buildJsonObject { put("value", "x".repeat(160_000)) }.toString()
        val delta = buildJsonObject { put("tool_calls", buildJsonArray { add(buildJsonObject {
            put("index", 0); put("id", "t"); put("type", "function")
            put("function", buildJsonObject { put("name", "read"); put("arguments", arguments) })
        }) }) }
        val result = read(frame(delta.toString(), "\"tool_calls\"") + "data: [DONE]\n\n", AiContextPolicy.RESPONSE_BYTES)
        val tool = Json.parseToJsonElement(result).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals(arguments, tool["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
    }
    @Test fun brokenStreamKeepsOnlyVisibleTextInInterruptedPreview() {
        val preview = AiStreamPreview()
        val raw = frame("""{"reasoning_content":"private reasoning","content":"received answer"}""")
        assertTrue(runCatching { read(raw, emit = preview::append) }.exceptionOrNull() is AiFailure)
        preview.interrupt()
        assertEquals("received answer", preview.snapshot().content)
        assertEquals("", preview.snapshot().reasoning)
        assertEquals(AiStreamPreview.Phase.INTERRUPTED, preview.snapshot().phase)
        assertFalse(preview.snapshot().toString().contains("received answer"))
    }
    @Test fun memoryAndWireLimitsHaveDistinctClosedDiagnostics() {
        fun failure(raw: String, max: Int): AiFailure = runCatching {
            AiSseCompletion.read(ByteArrayInputStream(raw.toByteArray()), max, {}, {}, AiDiagnostics())
        }.exceptionOrNull() as AiFailure
        assertEquals(AiFailureKind.RESPONSE_SIZE_LIMIT,
            failure("data: " + "x".repeat(AiContextPolicy.RESPONSE_BYTES + 1), AiContextPolicy.RESPONSE_BYTES).diagnostic?.kind)
        assertEquals(AiFailureKind.STREAM_WIRE_LIMIT, failure(":x\n".repeat(200), 4).diagnostic?.kind)
        // Raising the memory ceiling must not multiply the old wire cap to 128 MiB.
        assertEquals(16L * 1024 * 1024, AiContextPolicy.STREAM_WIRE_BYTES)
    }
}
