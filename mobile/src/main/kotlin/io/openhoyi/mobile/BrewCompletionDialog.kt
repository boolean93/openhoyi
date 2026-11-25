package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import java.text.NumberFormat
import java.util.Locale

/** Shows frozen readings for one completed extraction and its optional AI review. */
internal class BrewCompletionDialog(
    private val activity: Activity,
    level: BrewFeedbackClips.Level,
    outputHundredthsGram: Int? = null,
    endCupCount: Int? = null,
    private val onReview: () -> Unit,
) {
    private val style = TaskProductVisualStyle(activity)
    private val locale = ConfigurationCompat.getLocales(activity.resources.configuration)[0]
    private val numberFormat = NumberFormat.getNumberInstance(locale ?: Locale.getDefault()).apply {
        isGroupingUsed = false
        maximumFractionDigits = 2
    }
    private val status = style.text("", TaskProductVisualStyle.auxiliary, "completion-card/status", secondary = true).apply {
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val body = style.column("completion-card/body").apply {
        setPadding(style.dp(24), style.dp(4), style.dp(24), style.dp(5))
        val weight = style.column("completion-card/weight-group").apply {
            val label = activity.getString(R.string.completion_card_weight)
            val number = outputHundredthsGram?.let { numberFormat.format(it / 100.0) }
            style.add(this, style.text(label, TaskProductVisualStyle.auxiliary, secondary = true))
            addView(quantity(label, number, R.string.completion_card_gram_unit, TaskProductVisualStyle.largeValue,
                "completion-card/weight"), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            describeMetric(this, label, number, R.string.completion_card_gram_unit)
        }
        val flowLabel = activity.getString(when (level) {
            BrewFeedbackClips.Level.BRAVO -> R.string.completion_card_flow_balanced
            BrewFeedbackClips.Level.HIGH_FLOW -> R.string.completion_card_flow_fast
            BrewFeedbackClips.Level.LOW_FLOW -> R.string.completion_card_flow_slow
        })
        val flow = style.text(flowLabel, TaskProductVisualStyle.auxiliary, "completion-card/flow").apply {
            background = style.shape(style.selected, outline = true, radius = 20)
            setPadding(style.dp(10), style.dp(4), style.dp(10), style.dp(4))
        }
        style.add(this, CompletionReadingsRow(activity, style.dp(12), weight, flow, alignEnd = true))
        style.add(this, style.text(activity.getString(R.string.feedback_note), TaskProductVisualStyle.auxiliary,
            "completion-card/note", secondary = true), top = 8)
        style.divider(this, top = 12, bottom = 9)
        val cupsLabel = activity.getString(R.string.completion_card_total_cups)
        val cupsNumber = endCupCount?.let { numberFormat.format(it) }
        val cupsUnit = if (endCupCount == 1) R.string.completion_card_one_cup_unit else R.string.completion_card_cup_unit
        val cups = CompletionReadingsRow(
            activity, style.dp(9),
            style.text(cupsLabel, TaskProductVisualStyle.auxiliary, secondary = true),
            quantity(cupsLabel, cupsNumber, cupsUnit, TaskProductVisualStyle.compactMetric, "completion-card/cups"),
        ).apply {
            tag = "completion-card/cups-group"
            describeMetric(this, cupsLabel, cupsNumber, cupsUnit)
        }
        style.add(this, cups)
        style.add(this, status, top = 6)
    }

    val dialog: AlertDialog = AlertDialog.Builder(activity)
        .setTitle(R.string.feedback_title)
        .setView(body)
        .setPositiveButton(R.string.feedback_done, null)
        .setNeutralButton(R.string.completion_card_review, null)
        .create()

    fun show() {
        AppDialog.show(dialog, styleCustomBody = false, maxWidthDp = 400)
        val porcelain = ColorUtils.blendARGB(style.surface, style.selected, 0.12f)
        dialog.window?.setBackgroundDrawable(style.shape(porcelain, radius = 22))
        val review = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        val done = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        style.button(review, outline = true)
        arrangeActions(review, done)
        review.visibility = View.GONE
        review.setOnClickListener { onReview() }
    }

    private fun arrangeActions(review: Button, done: Button) {
        val bar = done.parent as? LinearLayout ?: return
        if (review.parent !== bar) return
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.weightSum = 0f // The only visible action fills the row when AI review is hidden.
        bar.setPaddingRelative(style.dp(24), style.dp(12), style.dp(24), style.dp(16))
        for (index in 0 until bar.childCount) {
            val child = bar.getChildAt(index)
            if (child !== review && child !== done) child.visibility = View.GONE
        }
        for (button in listOf(review, done)) {
            button.minHeight = style.dp(56)
            button.minimumHeight = style.dp(56)
            button.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (button === review) marginEnd = style.dp(8)
            }
        }
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
        if (reviewStatus == null) {
            status.visibility = View.GONE
        } else {
            val text = activity.getString(reviewStatus)
            if (status.text != text) status.text = text
            status.visibility = View.VISIBLE
        }
    }

    private fun quantity(
        label: String,
        number: String?,
        unit: Int,
        type: TaskProductVisualStyle.Type,
        tag: String,
    ): LinearLayout = style.quantity(number ?: "—", number?.let { activity.getString(unit) }, type, tag).apply {
        // Allow an unusually long number to wrap rather than clipping the unit.
        val numberView = getChildAt(0) as TextView
        numberView.layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f)
        contentDescription = measurementDescription(label, number, unit)
    }

    private fun describeMetric(group: LinearLayout, label: String, number: String?, unit: Int) {
        ViewCompat.setScreenReaderFocusable(group, true)
        group.contentDescription = measurementDescription(label, number, unit)
        for (index in 0 until group.childCount) {
            group.getChildAt(index).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun measurementDescription(label: String, number: String?, unit: Int): String =
        if (number == null) activity.getString(R.string.completion_card_measurement_unavailable, label)
        else activity.getString(R.string.completion_card_measurement, label, number, activity.getString(unit))
}

/** Pairs two readings on one line when they fit, with a vertical fallback for larger text. */
private class CompletionReadingsRow(
    activity: Activity,
    private val gap: Int,
    private val leading: View,
    private val trailing: View,
    private val alignEnd: Boolean = false,
) : LinearLayout(activity) {
    init {
        addView(leading, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        addView(trailing, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unconstrained = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        leading.measure(unconstrained, unconstrained)
        trailing.measure(unconstrained, unconstrained)
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val fits = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ||
            leading.measuredWidth + trailing.measuredWidth + gap <= availableWidth
        orientation = if (fits) HORIZONTAL else VERTICAL
        isBaselineAligned = false
        val readingParams = leading.layoutParams as LayoutParams
        readingParams.width = if (fits && alignEnd) 0 else if (fits) WRAP_CONTENT else MATCH_PARENT
        readingParams.weight = if (fits && alignEnd) 1f else 0f
        val badgeParams = trailing.layoutParams as LayoutParams
        badgeParams.gravity = if (!fits) Gravity.START else if (alignEnd) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        badgeParams.marginStart = if (fits) gap else 0
        // This change happens after the framework's normal margin resolution for this measure pass.
        badgeParams.resolveLayoutDirection(layoutDirection)
        badgeParams.topMargin = if (fits) 0 else gap / 2
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
