package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiSseSessionIntegrationTest {
    @Test fun actualKoogToolRoundTripsFullReasoningThroughStreamingProtocolAdapter() = runBlocking {
        val reasoning = "full-reasoning-".repeat(1000)
        val tool = buildJsonObject {
            put("id", "one"); put("created", 1); put("model", AiModelPolicy.FLASH)
            put("system_fingerprint", "fake")
            put("usage", buildJsonObject { put("prompt_tokens", 10); put("completion_tokens", 5); put("total_tokens", 15) })
            put("choices", JsonArray(listOf(buildJsonObject {
                put("index", 0); put("finish_reason", "tool_calls")
                put("message", buildJsonObject {
                    put("role", "assistant"); put("content", JsonNull); put("reasoning_content", reasoning)
                    put("tool_calls", JsonArray(listOf(buildJsonObject {
                        put("id", "call1"); put("type", "function"); put("function", buildJsonObject {
                            put("name", "current_status"); put("arguments", "{}")
                        })
                    })))
                })
            })))
        }.toString()
        val calls = mutableListOf<FakeAiConnection>()
        val events = mutableListOf<AiStreamDelta>()
        val budget = AiRequestBudget()
        val safe = AiSafeTransport({ "fake-test-only-key" }, { true }, budget,
            { FakeAiConnection(answer = if (calls.isEmpty()) tool else aiAnswer("done")).also(calls::add) }, onProgress = events::add)
        var reads = 0
        val data = object : AiReadOnlyData {
            override fun currentStatus(): String { reads++; return "{}" }
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        val turn = AiProtocolTurn(AiProtocolHistory.empty(), emptyList(), "status", safe)
        val answer = AiChatSession(data, turn, budget, Dispatchers.Unconfined).reply(emptyList(), "status")
        assertEquals("done", turn.complete(answer).text)
        assertEquals(1, reads); assertEquals(2, calls.size)
        assertEquals(reasoning, (events.first() as AiStreamDelta.Reasoning).text)
        val messages = Json.parseToJsonElement(calls.last().sent.toString("UTF-8")).jsonObject["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(reasoning, messages.first { it["tool_calls"] != null }["reasoning_content"]!!.jsonPrimitive.content)
        assertEquals("call1", messages.first { it["role"] == JsonPrimitive("tool") }["tool_call_id"]!!.jsonPrimitive.content)
        assertTrue(calls.all { Json.parseToJsonElement(it.sent.toString("UTF-8")).jsonObject["stream"] == JsonPrimitive(true) })
    }
}
