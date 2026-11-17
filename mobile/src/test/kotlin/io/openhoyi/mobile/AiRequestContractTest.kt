package io.openhoyi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import ai.koog.http.client.KoogHttpClient
import kotlin.reflect.KClass

/** Real SDK, synthetic input/fake key, local connection only. Never dumps payloads or calls the API. */
class AiRequestContractTest {
    @Test fun emptyRequiredNormalizationIsRootFunctionOnlyAndPreservesOtherFields() {
        fun request(parameters: JsonElement, type: String = "function") = buildJsonObject {
            put("model", "deepseek-flash")
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "你好 required:[]") }) })
            put("safety_check", buildJsonObject { put("level", "high") })
            put("tools", buildJsonArray { add(buildJsonObject {
                put("type", type)
                put("function", buildJsonObject { put("name", "test"); put("description", "untouched"); put("parameters", parameters) })
            }) })
        }
        val nested = buildJsonObject { put("type", "object"); put("required", JsonArray(emptyList())) }
        val parameters = buildJsonObject {
            put("type", "object"); put("required", JsonArray(emptyList()))
            put("properties", buildJsonObject { put("nested", nested) })
            put("additionalProperties", false)
        }
        val raw = request(parameters)
        assertEquals(request(JsonObject(parameters - "required")), Json.parseToJsonElement(AiRequestContract.withoutEmptyRequired(raw.toString())))
        val cases = listOf(
            JsonObject(parameters + ("required" to buildJsonArray { add("nested") })),
            JsonObject(parameters + ("required" to JsonNull)),
            JsonObject(parameters + ("required" to JsonPrimitive("not-an-array"))),
            JsonObject(parameters - "required"),
        )
        for (candidate in cases) {
            val text = request(candidate).toString()
            assertEquals(text, AiRequestContract.withoutEmptyRequired(text))
        }
        val nonFunction = request(parameters, "other").toString()
        assertEquals(nonFunction, AiRequestContract.withoutEmptyRequired(nonFunction))
        assertEquals("{}", AiRequestContract.forDeepSeek("{}"))
    }
    @Test fun chineseFirstRequestUsesFinalUtf8ByteLengthAndFixedJsonPost() = runBlocking {
        val data = object : AiReadOnlyData {
            override fun currentStatus() = "{}"
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        var declaredLength = -1
        var connected = false
        val call = object : FakeAiConnection(answer = aiAnswer("synthetic answer")) {
            override fun setFixedLengthStreamingMode(length: Int) { declaredLength = length; super.setFixedLengthStreamingMode(length) }
            override fun connect() {
                connected = true
                assertEquals("POST", requestMethod)
                assertEquals("application/json", requestHeaders["Content-Type"])
                assertEquals("application/json", requestHeaders["Accept"])
                assertEquals("Bearer fake-synthetic-key", requestHeaders["Authorization"])
                assertFalse(instanceFollowRedirects)
                assertEquals(15000, connectTimeout); assertEquals(30000, readTimeout)
            }
        }
        val budget = AiRequestBudget()
        val transport = AiSafeTransport({ "fake-synthetic-key" }, { true }, budget, { call })
        assertEquals("synthetic answer", AiChatSession(data, transport, budget, Dispatchers.Unconfined).reply(emptyList(), "你好"))
        assertTrue(connected)
        val bytes = call.sent.toByteArray()
        val text = bytes.toString(Charsets.UTF_8)
        assertEquals(bytes.size, declaredLength)
        assertTrue(bytes.size > text.length)
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), bytes)
        val body = Json.parseToJsonElement(text).jsonObject
        assertEquals("你好", body.getValue("messages").jsonArray.last().jsonObject["content"]?.jsonPrimitive?.content)
        assertEquals(setOf("messages", "model", "stream", "tools", "max_tokens", "thinking", "safety_check", "reasoning_effort"), body.keys)
    }
    @Test fun realKoogNonStreamingRequestMatchesCurrentDeepSeekContract() = runBlocking {
        val data = object : AiReadOnlyData {
            override fun currentStatus() = "{\"status\":\"unknown\"}"
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        val tool = """{"id":"fake","system_fingerprint":"fake","object":"chat.completion","created":1,"model":"deepseek-flash","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"synthetic-call","type":"function","function":{"name":"current_status","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
        val calls = mutableListOf<FakeAiConnection>(); val budget = AiRequestBudget()
        val answers = listOf(tool, aiAnswer("synthetic answer"))
        val original = mutableListOf<String>()
        val safe = AiSafeTransport({ "fake-synthetic-test-key" }, { true }, budget, {
            FakeAiConnection(answer = answers[calls.size]).also(calls::add)
        })
        val transport = object : KoogHttpClient by safe {
            override suspend fun <T : Any, R : Any> post(path: String, requestBody: T, requestBodyType: KClass<T>,
                responseType: KClass<R>, parameters: Map<String, String>, headers: Map<String, String>): R {
                original += requestBody as String
                return safe.post(path, requestBody, requestBodyType, responseType, parameters, headers)
            }
        }
        assertEquals("synthetic answer", AiChatSession(data, transport, budget, Dispatchers.Unconfined)
            .reply(emptyList(), "synthetic question"))
        assertEquals(2, calls.size)
        for (call in calls) {
            val body = Json.parseToJsonElement(call.sent.toString("UTF-8")).jsonObject
            assertEquals("deepseek-flash", body["model"]?.jsonPrimitive?.content)
            assertEquals(false, body["stream"]?.jsonPrimitive?.boolean)
            // Official API rejects stream_options with non-streaming requests.
            assertFalse(body.containsKey("stream_options"))
            assertEquals(AiContextPolicy.OUTPUT_TOKENS, body["max_tokens"]?.jsonPrimitive?.int)
            assertEquals(buildJsonObject { put("type", "enabled") }, body["thinking"])
            assertEquals(buildJsonObject { put("level", "high") }, body["safety_check"])
            // With tools, omitted tool_choice defaults to auto. Safety permits auto or none.
            assertTrue(body["tool_choice"] == null || body["tool_choice"]?.jsonPrimitive?.content in setOf("auto", "none"))
            val tools = body.getValue("tools").jsonArray
            assertEquals(setOf("current_status", "curves", "extraction_history", "web_search", "custom_curve_document", "propose_custom_curve", "present_curve_cards"),
                tools.map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }.toSet())
            tools.forEach {
                val entry = it.jsonObject
                assertEquals("function", entry["type"]?.jsonPrimitive?.content)
                val function = entry.getValue("function").jsonObject
                // Strict mode requires /beta; fixed ordinary endpoint must not opt in.
                assertTrue(function["strict"] == null || function["strict"]?.jsonPrimitive?.boolean == false)
                val parameters = function.getValue("parameters").jsonObject
                assertEquals("object", parameters["type"]?.jsonPrimitive?.content)
                val properties = parameters.getValue("properties").jsonObject
                if (function.getValue("name").jsonPrimitive.content == "web_search") {
                    assertEquals(setOf("query"), properties.keys)
                    assertEquals("string", properties.getValue("query").jsonObject["type"]?.jsonPrimitive?.content)
                    assertEquals(buildJsonArray { add("query") }, parameters["required"])
                } else if (function.getValue("name").jsonPrimitive.content == "propose_custom_curve") {
                    assertEquals(buildJsonArray { add("proposal_json") }, parameters["required"])
                } else if (function.getValue("name").jsonPrimitive.content == "custom_curve_document") {
                    assertEquals(setOf("id"), properties.keys)
                    assertEquals(buildJsonArray { add("id") }, parameters["required"])
                } else if (function.getValue("name").jsonPrimitive.content == "present_curve_cards") {
                    assertEquals(setOf("ids_json"), properties.keys)
                    assertEquals("string", properties.getValue("ids_json").jsonObject["type"]?.jsonPrimitive?.content)
                    assertEquals(buildJsonArray { add("ids_json") }, parameters["required"])
                } else {
                    assertFalse(parameters.containsKey("required"))
                }
                assertTrue((parameters["required"] as? JsonArray).orEmpty().all { name -> name.jsonPrimitive.content in properties })
                properties.values.forEach { parameter ->
                    assertTrue(parameter.jsonObject["type"]?.jsonPrimitive?.content in setOf("string", "integer"))
                }
            }
            val messages = body.getValue("messages").jsonArray.map { it.jsonObject }
            assertEquals("system", messages.first()["role"]?.jsonPrimitive?.content)
            assertTrue(messages.any { it["role"]?.jsonPrimitive?.content == "user" && it["content"]?.jsonPrimitive?.content == "synthetic question" })
            assertTrue(messages.all { "content" in it })
            assertFalse(body.toString().contains("fake-synthetic-test-key"))
        }
        val originalSecond = Json.parseToJsonElement(original.last()).jsonObject
        val originalMessages = originalSecond.getValue("messages").jsonArray
        assertTrue(originalMessages.any { it.jsonObject["role"] == JsonPrimitive("assistant") && "content" !in it.jsonObject })
        val expected = JsonObject(originalSecond + ("messages" to JsonArray(originalMessages.map {
            val message = it.jsonObject
            if (message["role"] == JsonPrimitive("assistant") && "content" !in message &&
                (message["tool_calls"] as? JsonArray)?.isNotEmpty() == true) JsonObject(message + ("content" to JsonNull)) else it
        })))
        fun expectedNoEmptyRequired(root: JsonObject) = JsonObject(root + ("tools" to JsonArray(root.getValue("tools").jsonArray.map {
            val tool = it.jsonObject
            val function = tool.getValue("function").jsonObject
            val parameters = function.getValue("parameters").jsonObject
            if (function.getValue("name").jsonPrimitive.content == "web_search") {
                assertEquals(buildJsonArray { add("query") }, parameters["required"])
                tool
            } else if (function.getValue("name").jsonPrimitive.content == "propose_custom_curve") {
                assertEquals(buildJsonArray { add("proposal_json") }, parameters["required"])
                tool
            } else if (function.getValue("name").jsonPrimitive.content == "custom_curve_document") {
                assertEquals(buildJsonArray { add("id") }, parameters["required"])
                assertEquals("string", parameters.getValue("properties").jsonObject.getValue("id").jsonObject.getValue("type").jsonPrimitive.content)
                tool
            } else if (function.getValue("name").jsonPrimitive.content == "present_curve_cards") {
                assertEquals(buildJsonArray { add("ids_json") }, parameters["required"])
                assertEquals("string", parameters.getValue("properties").jsonObject.getValue("ids_json").jsonObject.getValue("type").jsonPrimitive.content)
                tool
            } else {
                assertEquals(JsonArray(emptyList()), parameters["required"])
                JsonObject(tool + ("function" to JsonObject(function + ("parameters" to JsonObject(parameters - "required")))))
            }
        })))
        assertEquals(expectedNoEmptyRequired(expected), Json.parseToJsonElement(calls.last().sent.toString("UTF-8")))
        val first = Json.parseToJsonElement(original.first()).jsonObject
        assertEquals(expectedNoEmptyRequired(first), Json.parseToJsonElement(calls.first().sent.toString("UTF-8")))
        assertEquals(7, first.getValue("tools").jsonArray.size)
        val second = Json.parseToJsonElement(calls.last().sent.toString("UTF-8")).jsonObject.getValue("messages").jsonArray
        assertTrue(second.any { it.jsonObject["role"]?.jsonPrimitive?.content == "tool" && it.jsonObject["tool_call_id"]?.jsonPrimitive?.content == "synthetic-call" })
    }
}
