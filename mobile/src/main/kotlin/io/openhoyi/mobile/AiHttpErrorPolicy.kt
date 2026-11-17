package io.openhoyi.mobile

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.*

internal enum class AiProviderReason { CONTENT_RISK, TOOLS_FEATURE_CONFLICT, INVALID_REQUEST_ERROR, UNKNOWN, TOO_LARGE, MALFORMED, UNAVAILABLE }
internal enum class AiProviderParameter { MODEL, MESSAGES, TOOLS, TOOL_CHOICE, THINKING, SAFETY_CHECK, STREAM, STREAM_OPTIONS, MAX_TOKENS }
internal data class AiProviderDiagnostic(val reason: AiProviderReason, val parameter: AiProviderParameter? = null) {
    fun label(): String = reason.name + (parameter?.let { "/P_${it.name}" } ?: "")
}

/** HTTP400 only. The caller owns cancellation and a short read deadline. No raw field escapes. */
internal object AiHttpErrorPolicy {
    const val MAX_BYTES = 4096
    // Complete fixed literal from the official Web Tools Errors table, not a keyword guess.
    private val officialMessages = mapOf(
        "tools: other tools cannot be used together with `deepseek_web_search`, `deepseek_read_url`, `safety_check` or `title` yet" to AiProviderReason.TOOLS_FEATURE_CONFLICT,
    )
    fun read(input: InputStream, check: () -> Unit): AiProviderDiagnostic {
        val bytes = ByteArray(MAX_BYTES + 1)
        try {
            var count = 0
            while (count < bytes.size) {
                check()
                val next = input.read(bytes, count, bytes.size - count)
                if (next < 0) break
                if (next == 0) return AiProviderDiagnostic(AiProviderReason.UNAVAILABLE)
                count += next
            }
            check()
            if (count > MAX_BYTES) return AiProviderDiagnostic(AiProviderReason.TOO_LARGE)
            return classify(bytes, count)
        } finally { bytes.fill(0) }
    }
    private fun classify(bytes: ByteArray, count: Int): AiProviderDiagnostic {
        val malformed = AiProviderDiagnostic(AiProviderReason.MALFORMED)
        val raw = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, count)).toString() }
        catch (_: Exception) { return malformed }
        // Bound recursive JSON parsing before constructing a tree, honoring quoted/escaped braces.
        var depth = 0; var quoted = false; var escaped = false
        for (character in raw) {
            if (quoted) {
                if (escaped) escaped = false else if (character == '\\') escaped = true else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; if (depth > 16) return malformed }
                '}', ']' -> { depth--; if (depth < 0) return malformed }
            }
        }
        if (depth != 0 || quoted) return malformed
        val root = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: Exception) { return malformed }
            ?: return malformed
        val error = root["error"] as? JsonObject ?: return AiProviderDiagnostic(AiProviderReason.UNKNOWN)
        fun field(name: String): String? = (error[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        // Exact official fixed safety phrase only. Never contains/regex-match arbitrary provider prose.
        val reason = officialMessages[field("message")] ?: when {
            field("message") == "Content Exists Risk" -> AiProviderReason.CONTENT_RISK
            field("type") == "invalid_request_error" || field("code") == "invalid_request_error" -> AiProviderReason.INVALID_REQUEST_ERROR
            else -> AiProviderReason.UNKNOWN
        }
        val parameter = when (field("param")) {
            "model" -> AiProviderParameter.MODEL
            "messages" -> AiProviderParameter.MESSAGES
            "tools" -> AiProviderParameter.TOOLS
            "tool_choice" -> AiProviderParameter.TOOL_CHOICE
            "thinking" -> AiProviderParameter.THINKING
            "safety_check" -> AiProviderParameter.SAFETY_CHECK
            "stream" -> AiProviderParameter.STREAM
            "stream_options" -> AiProviderParameter.STREAM_OPTIONS
            "max_tokens" -> AiProviderParameter.MAX_TOKENS
            else -> null
        }
        return AiProviderDiagnostic(reason, parameter)
    }
}
