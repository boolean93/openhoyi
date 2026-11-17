package io.openhoyi.mobile

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.serialization.json.*

/** Ephemeral, untrusted plain text. Never log or persist these events. */
internal sealed class AiStreamDelta {
    class Reasoning(val text: String) : AiStreamDelta()
    class Content(val text: String) : AiStreamDelta()
    final override fun toString() = "AiStreamDelta"
}

/** Bounded SSE framing and OpenAI-compatible delta aggregation. Koog still owns the tool loop. */
internal object AiSseCompletion {
    fun request(raw: String): String = JsonObject(Json.parseToJsonElement(raw).jsonObject +
        ("stream" to JsonPrimitive(true))).toString()

    fun read(input: InputStream, maxBytes: Int, check: () -> Unit, emit: (AiStreamDelta) -> Unit, diagnostics: AiDiagnostics? = null, onUsage: (AiReportedUsage) -> Unit = {}): String {
        val line = ByteArrayOutputStream()
        val data = StringBuilder()
        var bytes = 0L
        fun limited(kind: AiFailureKind = AiFailureKind.RESPONSE_SIZE_LIMIT): Nothing =
            throw AiFailure(AiProblem.RESPONSE_LIMIT, diagnostics?.failure(kind)?.copy(stage = AiStage.RESPONSE_BODY))
        var payloadBytes = 0L
        fun account(text: String) {
            payloadBytes += text.toByteArray(Charsets.UTF_8).size.toLong()
            if (payloadBytes > maxBytes) limited()
        }
        var done = false
        var terminal: JsonElement? = null
        var metadata = emptyMap<String, JsonElement>()
        var usage: JsonElement? = null
        val message = mutableMapOf<String, JsonElement>("role" to JsonPrimitive("assistant"), "content" to JsonNull)
        val texts = mutableMapOf<String, StringBuilder>()
        val tools = sortedMapOf<Int, MutableMap<String, JsonElement>>()
        fun fail(): Nothing = throw AiFailure(AiProblem.PROTOCOL, diagnostics?.failure(AiFailureKind.PROTOCOL_FORMAT)?.copy(stage = AiStage.PROTOCOL))
        fun append(target: MutableMap<String, JsonElement>, key: String, value: JsonElement) {
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail()
            account(text)
            val previous = target[key]?.jsonPrimitive?.content.orEmpty()
            target[key] = JsonPrimitive(previous + text)
        }
        fun event() {
            if (data.isEmpty()) return
            val raw = data.toString().removeSuffix("\n")
            data.setLength(0)
            if (raw == "[DONE]") { if (terminal == null) fail(); done = true; return }
            if (done) fail()
            val root = try { Json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { fail() }
            val nextMetadata = root.filterKeys { it in setOf("id", "created", "model", "system_fingerprint") }
            nextMetadata.forEach { (key, value) -> if (metadata[key] != null && metadata[key] != value) fail() }
            metadata = metadata + nextMetadata
            root["usage"]?.takeIf { it != JsonNull }?.let {
                usage = it
                AiUsagePolicy.reported(JsonObject(metadata + ("usage" to it)).toString())?.let(onUsage)
            }
            if (root["error"] != null && root["error"] != JsonNull) fail()
            val choices = root["choices"] as? JsonArray ?: fail()
            if (choices.isEmpty()) return
            if (choices.size != 1) fail()
            val choice = choices.single() as? JsonObject ?: fail()
            if ((choice["index"] as? JsonPrimitive)?.intOrNull != 0) fail()
            if (terminal != null) fail()
            val delta = choice["delta"] as? JsonObject ?: fail()
            // Refusal must win before emitting content, even if the stream later loses [DONE].
            val refusal = delta["refusal"]
            if (choice["finish_reason"] == JsonPrimitive("content_filter") ||
                (refusal != null && refusal != JsonNull &&
                    ((refusal as? JsonPrimitive)?.takeIf { it.isString }?.content?.isNotBlank() != false))) {
                throw AiFailure(AiProblem.REFUSED, diagnostics?.failure(AiFailureKind.RESPONSE_POLICY))
            }
            if (delta.keys.any { it !in setOf("role", "content", "reasoning_content", "refusal", "tool_calls") }) fail()
            delta["role"]?.let { if (it != JsonPrimitive("assistant")) fail() }
            for (key in listOf("content", "reasoning_content", "refusal")) delta[key]?.let { value ->
                if (key == "refusal" && value != JsonNull && ((value as? JsonPrimitive)?.isString != true)) {
                    throw AiFailure(AiProblem.REFUSED, diagnostics?.failure(AiFailureKind.RESPONSE_POLICY))
                }
                if (value == JsonNull) { if (key !in message && key !in texts) message[key] = JsonNull }
                else {
                    val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: run {
                        if (key == "reasoning_content") throw AiFailure(AiProblem.PROTOCOL,
                            diagnostics?.failure(AiFailureKind.PROTOCOL_REASON)?.copy(stage = AiStage.PROTOCOL))
                        fail()
                    }
                    account(text)
                    texts.getOrPut(key) { StringBuilder() }.append(text)
                    if (text.isNotEmpty() && key != "refusal") { check(); emit(if (key == "content") AiStreamDelta.Content(text) else AiStreamDelta.Reasoning(text)) }
                }
            }
            delta["tool_calls"]?.let { value ->
                (value as? JsonArray ?: fail()).forEach { item ->
                    val part = item as? JsonObject ?: fail()
                    val index = (part["index"] as? JsonPrimitive)?.intOrNull ?: fail()
                    if (index !in 0 until AiChatPolicy.TOOL_CALLS) fail()
                    val tool = tools.getOrPut(index) { mutableMapOf() }
                    for (key in listOf("id", "type")) part[key]?.let { field ->
                        if ((field as? JsonPrimitive)?.isString != true || field.content.isEmpty()) fail()
                        if (key == "type" && field != JsonPrimitive("function")) fail()
                        if (key in tool && tool[key] != field) fail()
                        tool[key] = field
                    }
                    part["function"]?.let { function ->
                        val fields = (function as? JsonObject) ?: fail()
                        val merged = (tool["function"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                        fields["name"]?.let { append(merged, "name", it) }
                        fields["arguments"]?.let { append(merged, "arguments", it) }
                        tool["function"] = JsonObject(merged)
                    }
                }
            }
            choice["finish_reason"]?.takeIf { it != JsonNull }?.let { terminal = it }
        }
        fun endLine() {
            val raw = line.toByteArray(); line.reset()
            val text = try { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString() }
                catch (_: Exception) { fail() }
            if (text.isEmpty()) event()
            else if (text.startsWith("data:")) {
                data.append(text.substring(5).removePrefix(" ")).append('\n')
                if (data.length > maxBytes) limited()
            }
        }
        var wasCr = false
        while (!done) {
            check()
            val byte = input.read()
            check()
            if (byte < 0) fail() // A partial frame or missing [DONE] is never a completed response.
            // Per-token envelopes can dwarf the final JSON. Keep a separate finite wire budget;
            // payload and final assembled JSON retain a separate bounded memory ceiling.
            if (++bytes > minOf(AiContextPolicy.STREAM_WIRE_BYTES, maxBytes.toLong() * 128)) limited(AiFailureKind.STREAM_WIRE_LIMIT)
            if (wasCr) { wasCr = false; if (byte == 10) continue }
            when (byte) {
                13 -> { endLine(); wasCr = true }
                10 -> endLine()
                else -> {
                    if (line.size() >= maxBytes || data.length >= maxBytes) limited()
                    line.write(byte)
                }
            }
        }
        check()
        texts.forEach { (key, value) -> message[key] = JsonPrimitive(value.toString()) }
        if (tools.isNotEmpty()) {
            if (tools.keys.toList() != (0 until tools.size).toList()) fail()
            val ids = mutableSetOf<String>()
            if (terminal in listOf(JsonPrimitive("stop"), JsonPrimitive("tool_calls"))) tools.values.forEach { tool ->
                val id = (tool["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail()
                if (!ids.add(id) || tool["type"] != JsonPrimitive("function")) fail()
                val function = tool["function"] as? JsonObject ?: fail()
                for (key in listOf("name", "arguments")) {
                    val value = (function[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail()
                    if (value.isEmpty()) fail()
                    if (key == "arguments") {
                        val parsed = try { Json.parseToJsonElement(value) } catch (_: Exception) { fail() }
                        if (parsed !is JsonObject) fail()
                    }
                }
            }
            message["tool_calls"] = JsonArray(tools.map { (index, tool) -> JsonObject(tool + ("index" to JsonPrimitive(index))) })
        }
        val result = JsonObject(metadata + mapOf("object" to JsonPrimitive("chat.completion"),
            "choices" to JsonArray(listOf(buildJsonObject {
                put("index", 0); put("message", JsonObject(message)); put("finish_reason", terminal!!)
            }))) + (usage?.let { mapOf("usage" to it) } ?: emptyMap())).toString()
        if (result.toByteArray(Charsets.UTF_8).size > maxBytes) limited()
        return result
    }
}
