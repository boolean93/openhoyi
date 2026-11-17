package io.openhoyi.mobile

/** Closed vocabulary only: never store Throwable, URLs, keys, payloads or provider text. */
internal enum class AiStage { BUDGET, PROTOCOL, SDK_REQUEST, TRANSPORT, CONNECT, WRITE, RESPONSE_HEADERS, RESPONSE_BODY, VALIDATE, SDK_RESPONSE }
internal enum class AiFailureKind { RESPONSE_SIZE_LIMIT, STREAM_WIRE_LIMIT, REQUEST_SIZE_LIMIT, MODEL_REQUEST_BUDGET, AGENT_ITERATION_BUDGET, SEARCH_BUDGET, TOOL_CALL_BUDGET, TOOL_RESULT_BUDGET, PROTOCOL_PREFIX, PROTOCOL_TOOLS, PROTOCOL_REASON, PROTOCOL_FINAL, PROTOCOL_FINAL_MISSING, PROTOCOL_FINAL_TEXT, PROTOCOL_FINAL_TOOLS, PROTOCOL_FORMAT, PROTOCOL_HISTORY, TRANSPORT_CONTRACT, DNS, TLS, CONNECT, CONNECT_TIMEOUT, READ_TIMEOUT, IO_TIMEOUT, IO, HTTP, PARSE, RESPONSE_POLICY, SDK, TOTAL_TIMEOUT }
internal data class AiDiagnostic(val stage: AiStage, val kind: AiFailureKind, val elapsedMillis: Long, val httpStatus: Int? = null, val provider: AiProviderDiagnostic? = null) {
    fun label(): String = "${kind.name}/${stage.name}/${elapsedMillis}ms" + (httpStatus?.let { "/HTTP$it" } ?: "") + (provider?.let { "/" + it.label() } ?: "")
}
internal class AiDiagnostics(private val nanoTime: () -> Long = System::nanoTime) {
    private val started = nanoTime()
    @Volatile var httpStatus: Int? = null
    @Volatile var stage = AiStage.SDK_REQUEST
    fun failure(kind: AiFailureKind, status: Int? = httpStatus, provider: AiProviderDiagnostic? = null): AiDiagnostic =
        AiDiagnostic(stage, kind, ((nanoTime() - started) / 1_000_000).coerceAtLeast(0), status?.takeIf { it in 100..599 }, provider)
}
