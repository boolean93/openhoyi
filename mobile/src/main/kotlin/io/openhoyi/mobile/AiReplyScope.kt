package io.openhoyi.mobile

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** One visible send: preparation, optional compaction and answer share the deadline/request budget. */
internal class AiReplyScope(
    val budget: AiRequestBudget = AiRequestBudget(),
    val diagnostics: AiDiagnostics = AiDiagnostics(),
    private val timeoutMillis: Long = AiChatPolicy.REPLY_TIMEOUT_MS,
) {
    suspend fun <T> run(block: suspend () -> T): T = try {
        withTimeout(timeoutMillis) { block() }
    } catch (_: TimeoutCancellationException) {
        // An enclosing cancellation is not this reply's timeout and must not become an error notice.
        currentCoroutineContext().ensureActive()
        throw AiFailure(AiProblem.NETWORK, diagnostics.failure(AiFailureKind.TOTAL_TIMEOUT, status = null))
    }
}
