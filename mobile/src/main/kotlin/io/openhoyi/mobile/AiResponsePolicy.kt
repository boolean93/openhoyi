package io.openhoyi.mobile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Inspect only bounded success bodies. Never expose provider error/refusal text as UI diagnostics. */
internal object AiResponsePolicy {
    fun validate(raw: String, diagnostics: AiDiagnostics? = null) {
        val value = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: Exception) { throw AiFailure(AiProblem.EMPTY, diagnostics?.failure(AiFailureKind.PARSE)) }
        if (value?.get("error")?.let { it != JsonNull } == true)
            throw AiFailure(AiProblem.NETWORK)
        val choice = (value?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject ?: throw AiFailure(AiProblem.EMPTY, diagnostics?.failure(AiFailureKind.PARSE))
        // Official DeepSeek Chat Completions finish reasons, not inferred provider error strings.
        val reason = (choice["finish_reason"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        when (reason) {
            "content_filter" -> throw AiFailure(AiProblem.REFUSED)
            "insufficient_system_resource", "aborted" -> throw AiFailure(AiProblem.NETWORK)
            "stop", "tool_calls", "length" -> Unit
            else -> throw AiFailure(AiProblem.EMPTY, diagnostics?.failure(AiFailureKind.PARSE))
        }
        val message = choice["message"] as? JsonObject ?: throw AiFailure(AiProblem.EMPTY, diagnostics?.failure(AiFailureKind.PARSE))
        val refusal = message["refusal"]
        if (refusal != null && refusal != JsonNull &&
            (refusal !is JsonPrimitive || !refusal.isString || refusal.content.isNotBlank()))
            throw AiFailure(AiProblem.REFUSED)
        val text = (message["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val tools = (message["tool_calls"] as? JsonArray)?.isNotEmpty() == true
        if (choice["finish_reason"] == JsonPrimitive("length")) return // Protocol adapter terminates without dispatching tools.
        if (text.isBlank() && !tools) throw AiFailure(AiProblem.EMPTY)
    }
}
