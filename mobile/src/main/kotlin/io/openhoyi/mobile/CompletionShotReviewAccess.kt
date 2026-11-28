package io.openhoyi.mobile

import android.content.Context

/** User-triggered review uses ordinary Chat consent, never the background review switch/ledger. */
internal class CompletionShotReviewAccess(
    context: Context,
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
    fun issueHistory(historyId: String): String? = route.issueHistory(historyId, savedRevision())
    fun abandon(token: String) = route.abandon(token)

    fun consume(token: String, consent: Boolean): CompletionShotReview.Request? =
        route.consume(token, savedRevision(), consent)
}
