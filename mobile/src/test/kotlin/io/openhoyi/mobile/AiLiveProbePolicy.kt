package io.openhoyi.mobile

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Test-APK-only vocabulary. Every returned word is an enum constant, never a provider substring. */
internal enum class AiProbeWord {
    INVALID, NOT, SUPPORTED, UNSUPPORTED, CANNOT, MUST, REQUIRED, MISSING, UNKNOWN,
    UNRECOGNIZED, UNEXPECTED, FIELD, FORMAT, JSON, SCHEMA, TYPE, OBJECT, ARRAY,
    STRING, INTEGER, MODEL, MAX_TOKENS, TOOLS, FUNCTION, PARAMETERS, THINKING,
    SAFETY_CHECK, TOOL_CHOICE, STREAM, STREAM_OPTIONS, MESSAGES, CONTENT,
    PROPERTIES, ADDITIONAL_PROPERTIES, EXISTS, FOUND, AVAILABLE, UNAVAILABLE,
    ENABLED, DISABLED, TRUE, FALSE, STRICT, BETA, ONLY, TOGETHER, WITH, CAN, USED, YET
}
internal data class AiProbeBody(
    val reason: String,
    val words: List<AiProbeWord> = emptyList(),
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
) {
    fun wordLabel() = words.joinToString("|") { it.name }
}
internal object AiLiveProbePolicy {
    const val MAX_REQUEST_BYTES = 8192
    const val MAX_RESPONSE_BYTES = 4096
    const val PROMPT = "Reply OK only. Do not use tools."
    private val tokens = Regex("(?<![A-Za-z0-9_])[A-Za-z_]+(?![A-Za-z0-9_])")
    private val vocabulary = AiProbeWord.entries.associateBy { it.name.lowercase() } + mapOf(
        "tool" to AiProbeWord.TOOLS, "parameter" to AiProbeWord.PARAMETERS,
        "message" to AiProbeWord.MESSAGES, "exist" to AiProbeWord.EXISTS,
        "additionalproperties" to AiProbeWord.ADDITIONAL_PROPERTIES,
    )
    fun boundedWords(message: String, credential: String): List<AiProbeWord> {
        if (credential.isEmpty()) return emptyList()
        val redacted = message.replace(credential, " ", ignoreCase = true)
        val result = mutableListOf<AiProbeWord>()
        var length = 0
        for (match in tokens.findAll(redacted)) {
            val word = vocabulary[match.value.lowercase()] ?: continue
            if (word.name.equals(credential, ignoreCase = true)) continue
            if (result.lastOrNull() == word) continue
            val extra = word.name.length + if (result.isEmpty()) 0 else 1
            if (result.size == 16 || length + extra > 144) break
            result += word; length += extra
        }
        return result
    }
    fun parse(bytes: ByteArray, status: Int, credential: String): AiProbeBody {
        if (bytes.size > MAX_RESPONSE_BYTES) return AiProbeBody("TOO_LARGE")
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: Exception) { return AiProbeBody("MALFORMED") }
        var depth = 0; var quoted = false; var escaped = false
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; if (depth > 16) return AiProbeBody("MALFORMED") }
                '}', ']' -> { depth--; if (depth < 0) return AiProbeBody("MALFORMED") }
            }
        }
        if (depth != 0 || quoted) return AiProbeBody("MALFORMED")
        val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (_: Exception) { null }
            ?: return AiProbeBody("MALFORMED")
        fun count(element: JsonElement?): Int? = (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 0..1_000_000 }
        val usage = root["usage"] as? JsonObject
        val input = count(usage?.get("prompt_tokens")); val output = count(usage?.get("completion_tokens"))
        if (status in 200..299) return AiProbeBody("HTTP_SUCCESS", inputTokens = input, outputTokens = output)
        val error = root["error"] as? JsonObject ?: return AiProbeBody("ERROR_SHAPE")
        val message = (error["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return AiProbeBody("MESSAGE_SHAPE")
        val reason = if ((error["type"] as? JsonPrimitive)?.contentOrNull == "invalid_request_error" ||
            (error["code"] as? JsonPrimitive)?.contentOrNull == "invalid_request_error") "INVALID_REQUEST" else "HTTP_ERROR"
        return AiProbeBody(reason, boundedWords(message, credential), input, output)
    }
    const val TOOL_PROMPT = "Call current_status exactly once. After its result reply OK only. Do not call any other tool."
    const val FOLLOW_PROMPT = "Using only the previous result, reply OK only. Do not call tools."
    const val FIXTURE = "{\"status\":\"unknown\"}"
    fun scenarioRequest(raw: String): ByteArray {
        val root = Json.parseToJsonElement(raw).jsonObject
        require(root.keys == setOf("messages", "model", "stream", "tools", "max_tokens", "thinking", "safety_check", "reasoning_effort"))
        require(root["model"] == JsonPrimitive("deepseek-flash") && root["stream"] == JsonPrimitive(true))
        require(root["thinking"] == buildJsonObject { put("type", "enabled") })
        require(root["safety_check"] == buildJsonObject { put("level", "high") } && root["reasoning_effort"] == JsonPrimitive("low"))
        val messages = root.getValue("messages").jsonArray.map { it.jsonObject }
        require(messages.size in 2..16)
        require(messages.first() == buildJsonObject { put("role", "system"); put("content", AiChatPolicy.SYSTEM) })
        messages.drop(1).forEach {
            when (it["role"]?.jsonPrimitive?.content) {
                "user" -> require(it["content"] in setOf(JsonPrimitive(TOOL_PROMPT), JsonPrimitive(FOLLOW_PROMPT)))
                "assistant" -> require((it["reasoning_content"] as? JsonPrimitive)?.isString == true)
                "tool" -> require(it["content"] in setOf(JsonPrimitive(FIXTURE), JsonPrimitive(JsonPrimitive(FIXTURE).toString())))
                else -> error("Unexpected synthetic role")
            }
        }
        val localTools = localOnlyTools(root)
        return AiRequestContract.forDeepSeek(JsonObject(root + ("tools" to JsonArray(localTools)) + ("max_tokens" to JsonPrimitive(256))).toString())
            .toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_REQUEST_BYTES) { "Probe request bytes=${it.size}, limit=$MAX_REQUEST_BYTES" } }
    }
    fun request(raw: String, maxTokens: Int = 32): ByteArray {
        require(maxTokens in setOf(32, 128))
        val root = Json.parseToJsonElement(raw).jsonObject
        require(root.keys == setOf("messages", "model", "stream", "tools", "max_tokens", "thinking", "safety_check", "reasoning_effort"))
        require(root["model"] == JsonPrimitive("deepseek-flash") && root["stream"] == JsonPrimitive(true))
        require(root["thinking"] == buildJsonObject { put("type", "enabled") })
        require(root["reasoning_effort"] == JsonPrimitive("low"))
        require(root["safety_check"] == buildJsonObject { put("level", "high") })
        val messages = root.getValue("messages").jsonArray
        require(messages.size == 2)
        require(messages[0] == buildJsonObject { put("role", "system"); put("content", AiChatPolicy.SYSTEM) })
        require(messages[1] == buildJsonObject { put("role", "user"); put("content", PROMPT) })
        val localTools = localOnlyTools(root)
        val limited = AiRequestContract.forDeepSeek(JsonObject(root + ("tools" to JsonArray(localTools)) + ("max_tokens" to JsonPrimitive(maxTokens))).toString())
        return limited.toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_REQUEST_BYTES) { "Probe request bytes=${it.size}, limit=$MAX_REQUEST_BYTES" } }
    }
    private fun localOnlyTools(root: JsonObject): List<JsonElement> {
        // This existing live probe is approved for local reads only, never paid web search.
        val allTools = root.getValue("tools").jsonArray
        val knownNames = setOf("current_status", "curves", "extraction_history", "extraction_samples", "web_search", "propose_custom_curve", "custom_curve_document", "present_curve_cards")
        val allNames = allTools.map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }
        require(allNames.size in 4..8 && allNames.distinct().size == allNames.size && allNames.all { it in knownNames })
        val webTools = allTools.filter { it.jsonObject.getValue("function").jsonObject.getValue("name") == JsonPrimitive("web_search") }
        require(webTools.size <= 1)
        val proposals = allTools.filter { it.jsonObject.getValue("function").jsonObject.getValue("name") == JsonPrimitive("propose_custom_curve") }
        require(proposals.size <= 1)
        webTools.singleOrNull()?.let {
            val function = it.jsonObject.getValue("function").jsonObject
            val properties = function.getValue("parameters").jsonObject.getValue("properties").jsonObject
            require(properties.keys == setOf("query") && properties.getValue("query").jsonObject["type"] == JsonPrimitive("string"))
        }
        val localTools = allTools.filterNot {
            it.jsonObject.getValue("function").jsonObject.getValue("name") in setOf(JsonPrimitive("web_search"), JsonPrimitive("propose_custom_curve"), JsonPrimitive("custom_curve_document"), JsonPrimitive("present_curve_cards"))
        }
        val names = localTools.map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }
        require(names.size == 4 && names.toSet() == setOf("current_status", "curves", "extraction_history", "extraction_samples"))
        return localTools
    }

}
