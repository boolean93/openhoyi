package io.openhoyi.mobile

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Bridges a local feedback dialog to the *same* durably sampled cup. No latest-shot lookup. */
internal class CompletionShotReview(private val token: () -> String = { UUID.randomUUID().toString() }) {
    class Capture internal constructor(val feedback: BrewFeedbackResult, val historyId: String)
    class Request internal constructor(val historyId: String, val body: String, val keyRevision: Long) {
        private val dispatched = java.util.concurrent.atomic.AtomicBoolean()
        internal fun markDispatched(): Boolean = dispatched.compareAndSet(false, true)
    }
    private data class Handoff(val token: String, val request: Request)
    private var capture: Capture? = null
    private var body: String? = null
    private var persisted = false
    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision
    enum class Availability { SAVING, READY, UNAVAILABLE }
    private fun signal() { revision.value++ }
    private var clicked = false
    private var handoff: Handoff? = null

    @Synchronized fun begin(feedback: BrewFeedbackResult, historyId: String): Capture? {
        if (!AiExtractionSamples.validId(historyId)) return null
        return Capture(feedback, historyId).also { capture = it; body = null; clicked = false; persisted = false; signal() }
    }

    @Synchronized fun invalidate() { capture = null; body = null; clicked = false; persisted = false; signal() }

    @Synchronized fun persisted(owner: Capture, request: String?) {
        if (capture === owner && !clicked) { body = request; persisted = true; signal() }
    }

    @Synchronized fun availability(feedback: BrewFeedbackResult): Availability = when {
        capture?.feedback !== feedback || clicked -> Availability.UNAVAILABLE
        body != null -> Availability.READY
        persisted -> Availability.UNAVAILABLE
        else -> Availability.SAVING
    }

    @Synchronized fun ready(feedback: BrewFeedbackResult): Boolean =
        capture?.feedback === feedback && body != null && !clicked

    /** Called only for the explicit dialog action, after checking saved Key status. */
    @Synchronized fun issue(feedback: BrewFeedbackResult, keyRevision: Long?): String? {
        val owner = capture ?: return null
        if (keyRevision == null || handoff != null || !ready(feedback)) return null
        val value = body ?: return null
        clicked = true
        body = null
        signal()
        return token().also { handoff = Handoff(it, Request(owner.historyId, value, keyRevision)) }
    }

    @Synchronized fun abandon(token: String) {
        if (handoff?.token == token) handoff = null
    }

    /** Consume only after disclosure; a stale key or duplicate callback fails closed. */
    @Synchronized fun consume(token: String, keyRevision: Long?, consent: Boolean): Request? {
        val pending = handoff?.takeIf { it.token == token } ?: return null
        if (keyRevision == null || pending.request.keyRevision != keyRevision) {
            handoff = null; return null
        }
        if (!consent) return null
        handoff = null
        return pending.request
    }
}

/** A one-shot, no-tool/no-retry chat action. The regular session and its attachments are not input. */
internal suspend fun runCompletionShotReview(
    request: CompletionShotReview.Request,
    stillAuthorized: () -> Boolean,
    send: suspend (CompletionShotReview.Request) -> String,
    timeoutMs: Long = 45_000,
): ShotReviewResult {
    if (!stillAuthorized() || !request.markDispatched()) return ShotReviewResult(ShotReviewStatus.FAILED)
    return try {
        val text = withTimeoutOrNull(timeoutMs) { ShotReviewWire.answer(send(request)) }
        when {
            !stillAuthorized() -> ShotReviewResult(ShotReviewStatus.UNKNOWN)
            text == null -> ShotReviewResult(ShotReviewStatus.UNKNOWN)
            else -> ShotReviewResult(ShotReviewStatus.READY, text)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Dispatch boundary may be ambiguous. Never auto-replay.
        ShotReviewResult(ShotReviewStatus.UNKNOWN)
    }
}
