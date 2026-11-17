package io.openhoyi.mobile

/** UI vocabulary only. Resource mapping belongs to the Android host, never provider error text. */
internal object AiErrorPresentation {
    enum class Semantic { KEY, ACCESS, RATE, REFUSED, INPUT, CONTEXT, RESPONSE, TOOL, MODEL_BUDGET, SEARCH_BUDGET, TOOL_BUDGET, TOOL_RESULT_BUDGET, AGENT_BUDGET, REPLY_TIMEOUT, REQUEST, STORAGE, MODEL, ATTACHMENT, COMPACT, PROTOCOL, EMPTY, CONNECTION, CONNECT_TIMEOUT, READ_TIMEOUT, TIMEOUT, SERVICE, LOCAL_PROCESSING, UNKNOWN }

    fun semantic(problem: AiProblem, diagnostic: AiDiagnostic?): Semantic = when (problem) {
        AiProblem.KEY -> Semantic.KEY
        AiProblem.AUTH -> Semantic.ACCESS
        AiProblem.RATE_LIMIT -> Semantic.RATE
        AiProblem.REFUSED -> Semantic.REFUSED
        AiProblem.LIMIT, AiProblem.INPUT_LIMIT -> Semantic.INPUT
        AiProblem.CONTEXT_LIMIT -> Semantic.CONTEXT
        AiProblem.RESPONSE_LIMIT -> Semantic.RESPONSE
        AiProblem.TOOL_LIMIT -> when (diagnostic?.kind) {
            AiFailureKind.MODEL_REQUEST_BUDGET -> Semantic.MODEL_BUDGET
            AiFailureKind.SEARCH_BUDGET -> Semantic.SEARCH_BUDGET
            AiFailureKind.TOOL_CALL_BUDGET -> Semantic.TOOL_BUDGET
            AiFailureKind.TOOL_RESULT_BUDGET -> Semantic.TOOL_RESULT_BUDGET
            AiFailureKind.AGENT_ITERATION_BUDGET -> Semantic.AGENT_BUDGET
            else -> Semantic.TOOL
        }
        AiProblem.REQUEST_LIMIT -> Semantic.REQUEST
        AiProblem.STORAGE_LIMIT -> Semantic.STORAGE
        AiProblem.MODEL -> Semantic.MODEL
        AiProblem.ATTACHMENT -> Semantic.ATTACHMENT
        AiProblem.COMPACT_FAILED -> Semantic.COMPACT
        AiProblem.PROTOCOL -> Semantic.PROTOCOL
        AiProblem.EMPTY -> Semantic.EMPTY
        AiProblem.NETWORK -> network(diagnostic)
    }
    private fun network(d: AiDiagnostic?): Semantic = when {
        // The reply deadline outranks stale diagnostics from an earlier network/protocol stage.
        d?.kind == AiFailureKind.TOTAL_TIMEOUT -> Semantic.REPLY_TIMEOUT
        d?.stage == AiStage.PROTOCOL || d?.kind in protocolKinds -> Semantic.PROTOCOL
        d?.kind == AiFailureKind.SDK || d?.kind == AiFailureKind.TRANSPORT_CONTRACT -> Semantic.LOCAL_PROCESSING
        d?.kind == AiFailureKind.RESPONSE_POLICY -> Semantic.SERVICE
        d?.httpStatus == 401 || d?.httpStatus == 403 -> Semantic.ACCESS
        d?.httpStatus == 402 || d?.httpStatus == 429 -> Semantic.RATE
        d?.httpStatus in 500..599 -> Semantic.SERVICE
        d?.kind == AiFailureKind.CONNECT_TIMEOUT -> Semantic.CONNECT_TIMEOUT
        d?.kind == AiFailureKind.READ_TIMEOUT -> Semantic.READ_TIMEOUT
        d?.kind in timeoutKinds -> Semantic.TIMEOUT
        d?.kind in connectionKinds -> Semantic.CONNECTION
        else -> Semantic.UNKNOWN
    }
    private val protocolKinds = setOf(AiFailureKind.PROTOCOL_PREFIX, AiFailureKind.PROTOCOL_TOOLS,
        AiFailureKind.PROTOCOL_REASON, AiFailureKind.PROTOCOL_FINAL, AiFailureKind.PROTOCOL_FINAL_MISSING,
        AiFailureKind.PROTOCOL_FINAL_TEXT, AiFailureKind.PROTOCOL_FINAL_TOOLS, AiFailureKind.PROTOCOL_FORMAT,
        AiFailureKind.PROTOCOL_HISTORY, AiFailureKind.PARSE)
    private val timeoutKinds = setOf(AiFailureKind.CONNECT_TIMEOUT, AiFailureKind.READ_TIMEOUT, AiFailureKind.IO_TIMEOUT, AiFailureKind.TOTAL_TIMEOUT)
    private val connectionKinds = setOf(AiFailureKind.DNS, AiFailureKind.TLS, AiFailureKind.CONNECT, AiFailureKind.IO)
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** Mock-only host call. Each value is closed or bounded, never a Throwable or provider payload. */
    fun mockDetails(buildCode: Int, modelId: String, diagnostic: AiDiagnostic?, attemptId: String? = null,
        previousUnsuccessfulAttemptId: String? = null): String {
        fun id(value: String?) = value?.takeIf { it.length == 36 && uuid.matches(it) } ?: "unknown"
        val model = when (modelId) { AiModelPolicy.FLASH -> "flash"; AiModelPolicy.PRO -> "pro"; else -> "unknown" }
        return listOfNotNull("Build: ${buildCode.takeIf { it >= 0 } ?: "unknown"}", "Model: $model · reply",
            "Stage: ${diagnostic?.stage?.name ?: "unknown"} / ${diagnostic?.kind?.name ?: "unknown"}",
            "HTTP: ${diagnostic?.httpStatus?.takeIf { it in 100..599 } ?: "unknown"}",
            "Elapsed ms: ${diagnostic?.elapsedMillis?.coerceIn(0, 86_400_000) ?: "unknown"}",
            diagnostic?.provider?.let { "Provider: ${it.reason.name}" + (it.parameter?.let { p -> " / ${p.name}" } ?: "") },
            attemptId?.let { "Attempt: ${id(it)}" }, previousUnsuccessfulAttemptId?.let { "Previous unsuccessful attempt: ${id(it)}" }).joinToString("\n")
    }
}
