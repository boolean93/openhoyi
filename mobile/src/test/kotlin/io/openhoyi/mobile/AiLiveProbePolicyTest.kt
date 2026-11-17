package io.openhoyi.mobile

import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiLiveProbePolicyTest {
    private val key = "PRIVATE_KEY_SENTINEL_123"
    private fun error(message: JsonElement) = buildJsonObject { put("error", buildJsonObject {
        put("message", message); put("code", "invalid_request_error"); put("request_id", key); put("param", key)
    }) }.toString().toByteArray()
    @Test fun outputIsMappedVocabularyNotProviderSubstringsOrCredentials() {
        val result = AiLiveProbePolicy.parse(error(JsonPrimitive("Invalid model not supported $key private-value-314")), 400, key)
        assertEquals(listOf(AiProbeWord.INVALID, AiProbeWord.MODEL, AiProbeWord.NOT, AiProbeWord.SUPPORTED), result.words)
        assertFalse(result.toString().contains(key)); assertFalse(result.toString().contains("private-value"))
        assertEquals(emptyList<AiProbeWord>(), AiLiveProbePolicy.parse(error(buildJsonArray { add(key) }), 400, key).words)
        assertEquals(emptyList<AiProbeWord>(), AiLiveProbePolicy.boundedWords("safety_check", "safety_check"))
        assertEquals(emptyList<AiProbeWord>(), AiLiveProbePolicy.boundedWords("MODEL", "model"))
    }
    @Test fun vocabularyHandlesUnknownUnicodeNumbersLongAndRepeatedInputsWithinCap() {
        assertEquals(emptyList<AiProbeWord>(), AiLiveProbePolicy.boundedWords("私人内容 123 model123 123tools unknown_id_abc", key))
        val result = AiLiveProbePolicy.boundedWords("invalid ".repeat(1000) + AiProbeWord.entries.joinToString(" ") { it.name }, key)
        assertTrue(result.size <= 16)
        assertTrue(result.joinToString("|") { it.name }.length <= 144)
        assertEquals(AiProbeWord.INVALID, result.first())
        assertEquals("TOO_LARGE", AiLiveProbePolicy.parse(ByteArray(4097), 400, key).reason)
        assertEquals("MALFORMED", AiLiveProbePolicy.parse(("[".repeat(17) + "]".repeat(17)).toByteArray(), 400, key).reason)
    }
    @Test fun fixedRequestCapPreservesSafetyAndThinkingAndRejectsUnknownExtras() = runBlocking {
        val noData = object : AiReadOnlyData {
            override fun currentStatus() = "{}"
            override fun curves(query: String, offset: Int, limit: Int) = "{}"
            override fun history(curveId: String?, offset: Int, limit: Int) = "{}"
        }
        val call = FakeAiConnection(answer = aiAnswer("OK")); val budget = AiRequestBudget()
        val transport = AiSafeTransport({ "fake-probe-only-key" }, { true }, budget, { call })
        AiChatSession(noData, transport, budget, Dispatchers.Unconfined).reply(emptyList(), AiLiveProbePolicy.PROMPT)
        val raw = call.sent.toString("UTF-8")
        val bytes = AiLiveProbePolicy.request(raw, 128)
        val value = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        assertTrue(bytes.size <= 8192); assertEquals(128, value["max_tokens"]?.jsonPrimitive?.int)
        assertEquals("enabled", value.getValue("thinking").jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("low", value["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("high", value.getValue("safety_check").jsonObject["level"]?.jsonPrimitive?.content)
        assertTrue(runCatching { AiLiveProbePolicy.request(JsonObject(value + ("unknown" to JsonPrimitive(key))).toString(), 128) }.isFailure)
    }
    @Test fun successNeverOutputsAnswerAndUsageIsNumericAndBounded() {
        val raw = """{"choices":[{"message":{"content":"$key PRIVATE_USER"}}],"usage":{"prompt_tokens":99,"completion_tokens":2}}"""
        val result = AiLiveProbePolicy.parse(raw.toByteArray(), 200, key)
        assertEquals("HTTP_SUCCESS", result.reason); assertEquals(99, result.inputTokens); assertEquals(2, result.outputTokens)
        assertTrue(result.words.isEmpty()); assertFalse(result.toString().contains(key))
        val invalid = """{"usage":{"prompt_tokens":-1,"completion_tokens":"$key"}}"""
        val rejected = AiLiveProbePolicy.parse(invalid.toByteArray(), 200, key)
        assertNull(rejected.inputTokens); assertNull(rejected.outputTokens)
    }
}
