package io.openhoyi.mobile

import java.util.UUID

/** Bridges a local feedback dialog to the *same* durably sampled cup. No latest-shot lookup. */
internal class CompletionShotReview(private val token: () -> String = { UUID.randomUUID().toString() }) {
    class Capture internal constructor(val feedback: BrewFeedbackResult, val historyId: String)
    class Request internal constructor(val historyId: String, val keyRevision: Long) {
        fun line(question: String) = AiChatLine(true, question, completionShotId = historyId)
    }
    private data class Handoff(val token: String, val request: Request)
    private var capture: Capture? = null
    private var samples = Availability.UNAVAILABLE
    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision
    enum class Availability { SAVING, READY, UNAVAILABLE }
    private fun signal() { revision.value++ }
    private var clicked = false
    private var handoff: Handoff? = null

    @Synchronized fun begin(feedback: BrewFeedbackResult, historyId: String): Capture? {
        if (!AiExtractionSamples.validId(historyId)) return null
        return Capture(feedback, historyId).also {
            capture = it
            samples = Availability.SAVING
            clicked = false
            signal()
        }
    }

    @Synchronized fun invalidate() {
        capture = null
        samples = Availability.UNAVAILABLE
        clicked = false
        signal()
    }

    @Synchronized fun persisted(owner: Capture, hasSamples: Boolean = true) {
        if (capture !== owner || clicked) return
        samples = if (hasSamples) Availability.READY else Availability.UNAVAILABLE
        signal()
    }

    @Synchronized fun availability(feedback: BrewFeedbackResult): Availability =
        if (capture?.feedback === feedback && !clicked) samples else Availability.UNAVAILABLE

    @Synchronized fun ready(feedback: BrewFeedbackResult): Boolean = availability(feedback) == Availability.READY

    /** Called only for the explicit dialog action, after checking saved Key status. */
    @Synchronized fun issue(feedback: BrewFeedbackResult, keyRevision: Long?): String? {
        val owner = capture ?: return null
        if (keyRevision == null || handoff != null || !ready(feedback)) return null
        clicked = true
        return token().also {
            handoff = Handoff(it, Request(owner.historyId, keyRevision))
            signal()
        }
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
