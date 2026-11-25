package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import android.widget.Toast
import java.text.NumberFormat

/** Displays and shares the same frozen cup summary, without rendering or exporting a file. */
internal class BrewCompletionDialog(
    private val activity: Activity,
    summary: BrewCompletionSummary,
    private val launchShare: (Intent) -> Unit = activity::startActivity,
    private val onReview: () -> Unit,
) {
    private val title = activity.getString(R.string.feedback_title)
    private val message = buildString {
        val number = NumberFormat.getNumberInstance(activity.resources.configuration.locales[0]).apply {
            maximumFractionDigits = 2
        }
        fun reading(value: Double?, units: String): String =
            value?.let { "${number.format(it)} $units" } ?: "—"

        appendLine(activity.getString(R.string.completion_average_flow, reading(summary.averageScaleFlowGramsPerSecond, "g/s")))
        appendLine(activity.getString(R.string.completion_yield, reading(summary.outputGrams, "g")))
        appendLine(activity.getString(R.string.completion_average_pressure, reading(summary.averagePressureBar, "bar")))
        append(when (val count = summary.endCupCount) {
            null -> activity.getString(R.string.completion_cup_count, "—")
            0 -> activity.getString(R.string.completion_cup_count, activity.getString(R.string.completion_cup_number_zero))
            else -> activity.getString(R.string.completion_cup_number, number.format(count))
        })
    }

    val dialog: AlertDialog = AlertDialog.Builder(activity)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.completion_card_close, null)
        .setNegativeButton(R.string.completion_card_share, null)
        .setNeutralButton(R.string.completion_card_review, null)
        .create()

    fun show() {
        AppDialog.show(dialog)
        // The result remains open when Android's share panel is dismissed.
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { share() }
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

    private fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$message")
        }
        try {
            launchShare(Intent.createChooser(send, activity.getString(R.string.completion_card_share)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.completion_share_no_target, Toast.LENGTH_SHORT).show()
        }
    }
}
