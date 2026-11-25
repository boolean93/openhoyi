package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.view.View

/** Shows the local completion feedback and the optional AI review action. */
internal class BrewCompletionDialog(
    private val activity: Activity,
    level: BrewFeedbackClips.Level,
    private val onReview: () -> Unit,
) {
    private val title = activity.getString(R.string.feedback_title)
    private val message = buildString {
        val prompt = when (level) {
            BrewFeedbackClips.Level.BRAVO -> R.string.feedback_bravo
            BrewFeedbackClips.Level.HIGH_FLOW -> R.string.feedback_high_flow
            BrewFeedbackClips.Level.LOW_FLOW -> R.string.feedback_low_flow
        }
        appendLine(activity.getString(prompt))
        appendLine()
        append(activity.getString(R.string.feedback_note))
    }

    val dialog: AlertDialog = AlertDialog.Builder(activity)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.feedback_done, null)
        .setNeutralButton(R.string.completion_card_review, null)
        .create()

    fun show() {
        AppDialog.show(dialog)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { onReview() }
    }

    fun refresh(canShowReview: Boolean, reviewReady: Boolean, saving: Boolean = false) {
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
            visibility = if (canShowReview) View.VISIBLE else View.GONE
            isEnabled = reviewReady
        }
        val reviewStatus = when {
            !canShowReview || reviewReady -> null
            saving -> R.string.completion_card_saving
            else -> R.string.completion_card_no_samples
        }
        dialog.setMessage(if (reviewStatus == null) message else "$message\n\n${activity.getString(reviewStatus)}")
    }
}
