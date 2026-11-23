package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.min

/** The visible graphic and the exported bitmap are the very same artifact. */
internal class BrewCompletionCardDialog(
    private val activity: Activity,
    graphic: ShotShareCardRenderer.Model,
    onShare: (android.graphics.Bitmap) -> Unit,
    onReview: () -> Unit,
) {
    private val style = TaskProductVisualStyle(activity)
    private val bitmap = ShotShareCardRenderer.render(activity, graphic)
    val dialog: AlertDialog
    private val review: Button
    private val share: Button

    init {
        val shell = style.column("completion-card-dialog").apply {
            background = style.shape(style.surface, true, 20)
            setPadding(style.dp(12), 0, style.dp(12), style.dp(12))
        }
        val header = style.row("completion-card-header").apply { gravity = Gravity.END }
        val close = style.text("×", TaskProductVisualStyle.section).apply {
            style.clickable(this)
            gravity = Gravity.CENTER
            contentDescription = activity.getString(R.string.completion_card_close)
            tag = "completion-card-close"
            setOnClickListener { dismiss() }
        }
        header.addView(close, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        style.add(shell, header)
        val formatter = java.text.NumberFormat.getNumberInstance(activity.resources.configuration.locales[0]).apply {
            maximumFractionDigits = 2
        }
        val spoken = buildList {
            add(graphic.title)
            graphic.curveName?.let(::add)
            graphic.durationSeconds?.let { add(activity.getString(R.string.completion_card_spoken_seconds, formatter.format(it))) }
            graphic.outputGrams?.let { add(activity.getString(R.string.completion_card_spoken_yield, formatter.format(it))) }
            val dose = graphic.doseGrams
            val output = graphic.outputGrams
            if (dose != null && dose > 0 && output != null && output > 0) {
                add(activity.getString(R.string.completion_card_spoken_ratio, formatter.format(output / dose)))
            }
        }.joinToString(" · ")
        val photo = ImageView(activity).apply {
            tag = "completion-card-graphic"
            adjustViewBounds = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
            contentDescription = spoken
        }
        val max = style.dp((activity.resources.configuration.screenHeightDp - 172).coerceAtLeast(56))
        val scaled = ((activity.resources.configuration.screenWidthDp - 48).coerceIn(180, 466)
            .toDouble() * bitmap.height / bitmap.width).toInt()
        shell.addView(photo, LinearLayout.LayoutParams(-1, min(max, style.dp(scaled))))

        val actions = style.row("completion-card-actions").apply { gravity = Gravity.CENTER }
        share = style.button(Button(activity), primary = true).apply {
            text = activity.getString(R.string.completion_card_share)
            tag = "completion-card-share"
            setOnClickListener { onShare(bitmap) }
        }
        review = style.button(Button(activity), outline = true).apply {
            text = activity.getString(R.string.completion_card_review)
            tag = "completion-card-review"
            setOnClickListener { onReview() }
        }
        actions.addView(review, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = style.dp(8) })
        actions.addView(share, LinearLayout.LayoutParams(0, -2, 1.45f))
        style.add(shell, actions, top = 10)
        dialog = AlertDialog.Builder(activity).setView(shell).create().apply {
            setOnShowListener {
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val width = (activity.resources.configuration.screenWidthDp - 32).coerceIn(180, 490)
                window?.setLayout(style.dp(width), -2)
            }
        }
    }

    fun refresh(canShowReview: Boolean, reviewReady: Boolean, saving: Boolean = false) {
        review.visibility = if (canShowReview) View.VISIBLE else View.GONE
        review.isEnabled = reviewReady
        review.text = activity.getString(when {
            !canShowReview || reviewReady -> R.string.completion_card_review
            saving -> R.string.completion_card_saving
            else -> R.string.completion_card_no_samples
        })
    }

    fun dismiss() = dialog.dismiss()

    fun writePreviewPng(destination: java.io.OutputStream): Boolean =
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, destination)
}
