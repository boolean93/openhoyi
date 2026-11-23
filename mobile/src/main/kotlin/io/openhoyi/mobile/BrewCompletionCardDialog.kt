package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable

/** The visible graphic and the exported bitmap are the very same artifact. */
internal class BrewCompletionCardDialog(
    private val activity: Activity,
    graphic: ShotShareCardRenderer.Model,
    onShare: (android.graphics.Bitmap) -> Unit,
    flowLevel: BrewFeedbackClips.Level? = null,
    onReview: () -> Unit,
) {
    private val style = TaskProductVisualStyle(activity)
    private val bitmap = ShotShareCardRenderer.render(activity, graphic)
    private val dark = HoyiUi.dark(activity)
    private val shellColor = if (dark) 0xFF241D19.toInt() else 0xFFFAF8F5.toInt()
    private val closeInk = if (dark) 0xFFDCC8B9.toInt() else 0xFF65534A.toInt()
    private val secondaryInk = if (dark) 0xFFEED6C5.toInt() else 0xFF673D2D.toInt()
    val dialog: AlertDialog
    private val review: Button
    private val share: Button

    init {
        val size = activity.resources.configuration
        val inset = if (size.screenWidthDp >= 600) 20 else 16
        // Solve the 4:5 picture first, then derive the shell and action width from it.
        // Height-limited tablet/landscape windows therefore never get a wider empty tray.
        val horizontalBudget = (size.screenWidthDp - 24 - 2 * inset).coerceAtLeast(96)
        val verticalBudget = (size.screenHeightDp - 168).coerceAtLeast(88)
        val imageWidth = minOf(horizontalBudget, 414,
            (verticalBudget.toDouble() * bitmap.width / bitmap.height).toInt())
        val imageHeight = (imageWidth.toDouble() * bitmap.height / bitmap.width).toInt()
        val shell = style.column("completion-card-dialog").apply {
            background = shape(shellColor, 24)
            elevation = style.dp(5).toFloat()
            setPadding(style.dp(inset), 0, style.dp(inset), style.dp(20))
        }
        val header = style.row("completion-card-header").apply { gravity = Gravity.CENTER_VERTICAL }
        val leading = FrameLayout(activity).apply {
            setPadding(0, 0, style.dp(4), 0)
        }
        header.addView(leading, LinearLayout.LayoutParams(0, style.dp(48), 1f))
        completionFlowPaceLabel(flowLevel)?.let { label ->
            val flow = style.text(activity.getString(label), TaskProductVisualStyle.auxiliary).apply {
                tag = "completion-card-flow-pace"
                setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(secondaryInk)
                setPadding(style.dp(10), style.dp(4), style.dp(10), style.dp(4))
                background = shape(if (dark) 0xFF392C25.toInt() else 0xFFF1E7DE.toInt(), 12)
            }
            leading.addView(flow, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        }
        val close = ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = null
            backgroundTintList = null
            setImageDrawable(closeDrawable())
            background = interactive(Color.TRANSPARENT, focus = closeInk, radius = 24)
            setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
            isFocusable = true
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
            background = shape(shellColor, 15)
            clipToOutline = true
            elevation = style.dp(3).toFloat()
            contentDescription = spoken
        }
        shell.addView(photo, LinearLayout.LayoutParams(-1, style.dp(imageHeight)))

        val actions = style.row("completion-card-actions").apply { gravity = Gravity.CENTER }
        share = action(primary = true).apply {
            setActionLabel(R.string.completion_card_share, ChromeIcon.SHARE)
            tag = "completion-card-share"
            setOnClickListener { onShare(bitmap) }
        }
        review = action(primary = false).apply {
            setActionLabel(R.string.completion_card_review, ChromeIcon.SPARK)
            tag = "completion-card-review"
            setOnClickListener { onReview() }
        }
        actions.addView(review, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = style.dp(10) })
        actions.addView(share, LinearLayout.LayoutParams(0, -1, 1.25f))
        style.add(shell, actions, top = 18)
        dialog = AlertDialog.Builder(activity).setView(shell).create().apply {
            setOnShowListener {
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window?.setLayout(style.dp(imageWidth + 2 * inset), -2)
            }
        }
    }

    fun refresh(canShowReview: Boolean, reviewReady: Boolean, saving: Boolean = false) {
        review.visibility = if (canShowReview) View.VISIBLE else View.GONE
        review.isEnabled = reviewReady
        review.setActionLabel(when {
            !canShowReview || reviewReady -> R.string.completion_card_review
            saving -> R.string.completion_card_saving
            else -> R.string.completion_card_no_samples
        }, ChromeIcon.SPARK)
    }

    private fun shape(fill: Int, radius: Int, border: Int? = null) = GradientDrawable().apply {
        cornerRadius = style.dp(radius).toFloat()
        setColor(fill)
        border?.let { setStroke(style.dp(1).coerceAtLeast(1), it) }
    }

    private fun interactive(fill: Int, focus: Int, radius: Int, border: Int? = null, lightRipple: Boolean = false): Drawable {
        fun ripple(color: Int, outline: Int?) = RippleDrawable(
            ColorStateList.valueOf(if (lightRipple) 0x33FFFFFF else if (dark) 0x33FFD8C2 else 0x23764C37),
            shape(color, radius, outline), shape(Color.WHITE, radius),
        )
        return StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(if (dark) 0xFF3A2E27.toInt() else 0xFFECE5DE.toInt(), radius))
            addState(intArrayOf(android.R.attr.state_focused), ripple(fill, focus))
            addState(intArrayOf(), ripple(fill, border))
        }
    }

    private fun action(primary: Boolean): Button = style.button(Button(activity)).apply {
        minHeight = style.dp(50)
        minimumHeight = minHeight
        setPadding(style.dp(8), style.dp(8), style.dp(8), style.dp(8))
        val fill = if (primary) {
            if (dark) 0xFFE6AD8B.toInt() else 0xFF874B32.toInt()
        } else if (dark) 0xFF4C3024.toInt() else 0xFFF1E7DE.toInt()
        val border = when { primary -> null; dark -> 0xFF5F473A.toInt(); else -> 0xFFE3D4C7.toInt() }
        val focus = if (primary) {
            if (dark) 0xFF503022.toInt() else 0xFFFFDCC7.toInt()
        } else secondaryInk
        background = interactive(fill, focus, 12, border, primary && !dark)
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(if (dark) 0xFFBAA79B.toInt() else 0xFF746258.toInt(),
                if (primary) {
                    if (dark) 0xFF291A14.toInt() else 0xFFFFF8F2.toInt()
                } else secondaryInk),
        ))
    }

    private fun Button.setActionLabel(resource: Int, icon: ChromeIcon) {
        val label = activity.getString(resource)
        text = iconLabel(label, icon)
        contentDescription = label
    }

    private fun closeDrawable(): Drawable = object : Drawable() {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = closeInk
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        override fun getIntrinsicWidth(): Int = style.dp(16)
        override fun getIntrinsicHeight(): Int = style.dp(16)
        override fun draw(canvas: Canvas) {
            val side = minOf(bounds.width(), bounds.height()).toFloat()
            if (side <= 0f) return
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val arm = side * 0.28f
            ink.strokeWidth = side * 0.105f
            canvas.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm, ink)
            canvas.drawLine(centerX + arm, centerY - arm, centerX - arm, centerY + arm, ink)
        }
        override fun setAlpha(alpha: Int) { ink.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { ink.colorFilter = filter; invalidateSelf() }
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private enum class ChromeIcon { SPARK, SHARE }

    /** Inline icons keep the icon and label centered as one text run, including when a label wraps. */
    private fun iconLabel(label: String, icon: ChromeIcon) = SpannableString("\uFFFC" + label).apply {
        val size = style.dp(18)
        val gap = if (label.isEmpty()) 0 else style.dp(7)
        setSpan(object : ReplacementSpan() {
            override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = size + gap

            override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float,
                              top: Int, y: Int, bottom: Int, paint: Paint) {
                val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = paint.color
                    style = Paint.Style.STROKE
                    strokeWidth = 1.55f
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                val metrics = paint.fontMetrics
                canvas.save()
                canvas.translate(x, y + (metrics.ascent + metrics.descent - size) / 2f)
                canvas.scale(size / 24f, size / 24f)
                fun path(draw: Path.() -> Unit) = canvas.drawPath(Path().apply(draw), ink)
                when (icon) {
                    ChromeIcon.SPARK -> {
                        path {
                            moveTo(10f, 4f); quadTo(11f, 9f, 16f, 10f); quadTo(11f, 11f, 10f, 16f)
                            quadTo(9f, 11f, 4f, 10f); quadTo(9f, 9f, 10f, 4f); close()
                        }
                        canvas.drawLine(18f, 13f, 18f, 19f, ink)
                        canvas.drawLine(15f, 16f, 21f, 16f, ink)
                    }
                    ChromeIcon.SHARE -> {
                        path { moveTo(6f, 10f); lineTo(4f, 10f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 10f); lineTo(18f, 10f) }
                        path { moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f) }
                        canvas.drawLine(12f, 3f, 12f, 15f, ink)
                    }
                }
                canvas.restore()
            }
        }, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun dismiss() = dialog.dismiss()

    fun writePreviewPng(destination: java.io.OutputStream): Boolean =
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, destination)
}
