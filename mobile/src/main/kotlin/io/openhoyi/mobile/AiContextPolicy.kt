package io.openhoyi.mobile

import kotlinx.serialization.json.*

/** App budget, deliberately smaller than the provider window. Estimates are NOT billed usage. */
internal object AiContextPolicy {
    const val MODEL_TOKENS = 1_000_000
    const val WORKING_TOKENS = 262144
    const val OUTPUT_TOKENS = 8192
    const val SUMMARY_TOKENS = 2048
    const val TOOL_RESERVE_TOKENS = 4096
    const val DEFAULT_SCHEMA_TOKENS = 16384L
    const val INPUT_TOKENS = WORKING_TOKENS - OUTPUT_TOKENS - TOOL_RESERVE_TOKENS
    const val COMPACT_AT = 200000
    const val SUMMARY_CHARS = 12000
    const val TRANSCRIPT_CHARS = 4_000_000
    const val REQUEST_BYTES = 16 * 1024 * 1024
    // Client memory safety ceiling for the whole response, including reasoning and tool JSON.
    // Separate from the model output-token budget and the visible answer-character bound.
    const val RESPONSE_BYTES = 1024 * 1024
    const val STREAM_WIRE_BYTES = 16L * 1024 * 1024
    const val INCOMPLETE = "\n\n[回答未完成 / Incomplete response — 已停止，未自动续发 / Stopped; no automatic continuation.]"
    fun incomplete(text: String): String {
        val prefix = text.take(AiChatPolicy.OUTPUT_CHARS - INCOMPLETE.length)
        val safe = if (prefix.isNotEmpty() && Character.isHighSurrogate(prefix.last())) prefix.dropLast(1) else prefix
        return safe + INCOMPLETE
    }
    fun estimate(text: String): Int {
        // Byte-level upper estimate, not a tokenizer. Deliberately conservative for unseen text.
        return 32 + text.toByteArray(Charsets.UTF_8).size
    }
    /** Semantic JSON text bytes plus structure; wire escaping is not model content. */
    fun estimateJson(value: JsonElement): Long = when (value) {
        is JsonObject -> 32L + value.entries.sumOf { (key, child) -> estimate(key).toLong() + estimateJson(child) }
        is JsonArray -> 32L + value.sumOf { estimateJson(it) }
        is JsonPrimitive -> if (value.isString) estimate(value.content).toLong() else estimate(value.toString()).toLong()
    }
    fun projected(history: AiProtocolHistory, input: String, extraTokens: Long = 0,
        schemaTokens: Long = DEFAULT_SCHEMA_TOKENS): Long {
        require(schemaTokens >= 0 && extraTokens >= 0)
        // Keep the full local protocol, even when a previous response reports smaller usage.
        val local = estimateJson(JsonArray(history.messages)) +
            estimate(history.summary.orEmpty()) + AiAttachmentConversation.tokens(history.messages)
        val historical = maxOf(local, history.reportedInput?.let { it + history.reportedOutput } ?: 0)
        // Structural allowance includes summary wrapper, request settings and role/message framing.
        return historical + estimate(input) + estimate(AiChatPolicy.SYSTEM) + schemaTokens + 2048 + extraTokens
    }
    fun needsCompact(history: AiProtocolHistory, input: String, extraTokens: Long = 0, imageBytes: Long = 0,
        schemaTokens: Long = DEFAULT_SCHEMA_TOKENS) = projected(history, input, extraTokens, schemaTokens) >= COMPACT_AT ||
        JsonArray(history.messages).toString().toByteArray(Charsets.UTF_8).size >= AiProtocolHistory.MAX_BYTES * 3 / 4 ||
        history.messages.size >= AiProtocolHistory.MAX_MESSAGES * 3 / 4 ||
        AiAttachmentConversation.imageBytes(history.messages) + imageBytes >= AiAttachmentConversation.COMPACT_IMAGE_BYTES
    fun checkTranscript(lines: List<AiChatLine>, input: String) {
        if (input.isBlank() || input.length > AiChatPolicy.INPUT_CHARS) throw AiFailure(AiProblem.INPUT_LIMIT)
        if (lines.size >= 20000 || lines.sumOf { it.text.length.toLong() } + input.length + AiChatPolicy.OUTPUT_CHARS > TRANSCRIPT_CHARS)
            throw AiFailure(AiProblem.STORAGE_LIMIT)
    }
    fun visible(messages: List<JsonObject>) = messages.filter { it["role"] == JsonPrimitive("user") ||
        (it["role"] == JsonPrimitive("assistant") && (it["tool_calls"] as? JsonArray).isNullOrEmpty()) }
        .map { JsonObject(it.filterKeys { k -> k == "role" || k == "content" || k == AiAttachmentConversation.FIELD }) }
    fun lines(lines: List<AiChatLine>) = lines.map {
        AiAttachmentConversation.withItems(buildJsonObject { put("role", if (it.user) "user" else "assistant"); put("content", it.wireText) }, it.attachments)
    }
    fun matches(history: AiProtocolHistory, lines: List<AiChatLine>) = history.coveredLines <= lines.size &&
        visible(history.messages) == lines(lines.drop(history.coveredLines))
}

/** A bounded result, never an exception message/log payload. */
internal class AiPartialCompletion(val reply: AiChatReply) : AiFailure(AiProblem.RESPONSE_LIMIT)
