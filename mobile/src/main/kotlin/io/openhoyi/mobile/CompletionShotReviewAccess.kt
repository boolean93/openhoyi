package io.openhoyi.mobile

import android.content.Context

/** User-triggered review uses ordinary Chat consent, never the background review switch/ledger. */
internal class CompletionShotReviewAccess(
    private val context: Context,
    private val route: CompletionShotReview,
) {
    private val key = AiKeyStore(context)

    private fun savedRevision(): Long? {
        val before = key.revision()
        return before.takeIf { key.status() == AiKeyStatus.SAVED && key.revision() == before }
    }

    fun hasConfiguredKey(): Boolean = savedRevision() != null
    val changes get() = route.changes
    fun availability(feedback: BrewFeedbackResult) = route.availability(feedback)
    fun isReady(feedback: BrewFeedbackResult): Boolean = route.ready(feedback)
    fun issue(feedback: BrewFeedbackResult): String? = route.issue(feedback, savedRevision())
    fun abandon(token: String) = route.abandon(token)

    fun hasChatConsent(): Boolean = AiConsentPolicy.accepts(
        context.getSharedPreferences("ai_consent", Context.MODE_PRIVATE)
            .getInt(AiConsentPolicy.VERSION_KEY, 0)
    )

    fun consume(token: String): CompletionShotReview.Request? =
        route.consume(token, savedRevision(), hasChatConsent())

    suspend fun review(request: CompletionShotReview.Request): ShotReviewResult {
        // Production Mock never performs this network action. Tests inject the transport at the pure boundary.
        if (BuildConfig.MOCK_MODE) return ShotReviewResult(ShotReviewStatus.FAILED)
        fun current(): Boolean = request.keyRevision == key.revision() && key.hasSavedValue() && hasChatConsent()
        return runCompletionShotReview(request, ::current, send = {
            val transport = AiSafeTransport(
                key = { key.readForRequest(request.keyRevision) },
                keyStillCurrent = ::current,
                budget = AiRequestBudget(),
                maxResponseBytes = 32_768,
                sendPermit = ::current,
            )
            val stopObserving = key.observeMutation(transport::close)
            try { ShotReviewWire.send(transport, request.body) }
            finally { stopObserving(); transport.close() }
        })
    }
}
