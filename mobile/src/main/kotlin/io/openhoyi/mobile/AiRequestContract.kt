package io.openhoyi.mobile

import kotlinx.serialization.json.*

/** Narrow wire compatibility only; never rewrites user text, nested schemas or security parameters. */
internal object AiRequestContract {
    fun forDeepSeek(raw: String): String = withoutEmptyRequired(withAssistantContent(raw))

    /** Empty required is redundant for optional arguments, but invalid in JSON Schema draft-04. */
    internal fun withoutEmptyRequired(raw: String): String {
        if (!raw.contains("\"tools\"")) return raw
        val root = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: Exception) { null } ?: return raw
        val tools = root["tools"] as? JsonArray ?: return raw
        var changed = false
        val corrected = tools.map { value ->
            val tool = value as? JsonObject
            val function = tool?.get("function") as? JsonObject
            val parameters = function?.get("parameters") as? JsonObject
            val required = parameters?.get("required") as? JsonArray
            if (tool != null && tool["type"] == JsonPrimitive("function") && function != null && parameters != null && required?.isEmpty() == true) {
                changed = true
                JsonObject(tool + ("function" to JsonObject(function + ("parameters" to JsonObject(parameters - "required")))))
            } else value
        }
        return if (changed) JsonObject(root + ("tools" to JsonArray(corrected))).toString() else raw
    }

    /** DeepSeek declares assistant content required but nullable, including tool-call messages. */
    internal fun withAssistantContent(raw: String): String {
        if (!raw.contains("\"tool_calls\"")) return raw
        val root = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: Exception) { null } ?: return raw
        val messages = root["messages"] as? JsonArray ?: return raw
        var changed = false
        val corrected = messages.map { value ->
            val message = value as? JsonObject
            if (message?.get("role") == JsonPrimitive("assistant") && "content" !in message &&
                (message["tool_calls"] as? JsonArray)?.isNotEmpty() == true) {
                changed = true
                JsonObject(message + ("content" to JsonNull))
            } else value
        }
        return if (changed) JsonObject(root + ("messages" to JsonArray(corrected))).toString() else raw
    }
}
