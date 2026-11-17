package io.openhoyi.mobile

/** Reply-level diagnostics, not HTTP request counts or a billing ledger. No payload API exists. */
internal class AiDiagnosticRecorder(
    private val record: (String, Map<String, String>) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    internal class Attempt internal constructor(val id: String, internal val started: Long, internal val model: String) {
        internal var finished = false
    }
    private var previousUnsuccessful: String? = null

    @Synchronized fun begin(modelId: String): Attempt {
        val model = when (modelId) { AiModelPolicy.FLASH -> "flash"; AiModelPolicy.PRO -> "pro"; else -> "unknown" }
        val attempt = Attempt(java.util.UUID.randomUUID().toString(), nanoTime(), model)
        emit("started", fields(attempt).apply { previousUnsuccessful?.let { put("previousUnsuccessfulAttemptId", it) } })
        return attempt
    }
    @Synchronized fun succeeded(attempt: Attempt) = finish(attempt, "succeeded")
    @Synchronized fun cancelled(attempt: Attempt) = finish(attempt, "cancelled")
    @Synchronized fun failed(attempt: Attempt, problem: AiProblem, diagnostic: AiDiagnostic?) = finish(attempt, "failed", problem, diagnostic)

    @Synchronized fun retry(attempt: Attempt, progress: AiRetryProgress) {
        if (attempt.finished || progress.index !in 1..AiRetryPolicy.MAX_RETRIES || progress.waitMillis != AiRetryPolicy.waitMillis(progress.index) ||
            progress.kind !in setOf(AiFailureKind.DNS, AiFailureKind.CONNECT, AiFailureKind.CONNECT_TIMEOUT)) return
        emit("retry", fields(attempt).apply {
            put("retryIndex", progress.index.toString()); put("retryLimit", AiRetryPolicy.MAX_RETRIES.toString())
            put("waitMs", progress.waitMillis.toString()); put("stage", AiStage.CONNECT.name); put("failureKind", progress.kind.name)
        })
    }
    private fun finish(attempt: Attempt, outcome: String, problem: AiProblem? = null, diagnostic: AiDiagnostic? = null) {
        if (attempt.finished) return
        attempt.finished = true
        previousUnsuccessful = if (outcome == "succeeded") null else attempt.id
        emit(outcome, fields(attempt).apply {
            put("durationMs", ((nanoTime() - attempt.started) / 1_000_000).coerceIn(0, 86_400_000).toString())
            problem?.let { put("problem", it.name) }
            diagnostic?.let {
                put("stage", it.stage.name); put("failureKind", it.kind.name)
                it.httpStatus?.takeIf { code -> code in 100..599 }?.let { code -> put("httpStatus", code.toString()) }
            }
        })
    }
    private fun fields(attempt: Attempt) = linkedMapOf("scope" to "reply", "attemptId" to attempt.id, "model" to attempt.model)
    private fun emit(outcome: String, fields: Map<String, String>) {
        // Full/closed diagnostic queues must never change request behavior or hide its original error.
        try { record("ai.reply.$outcome", fields) } catch (_: Exception) { }
    }
}
