package io.openhoyi.mobile

import android.app.Dialog
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/** Low-frequency display controls; opening/dismissing never changes the active extraction. */
internal class ChartSeriesSelector(private val chart: ExtractionOverlayView) {
    private val context = chart.context
    private val style = TaskProductVisualStyle(context)
    private val visual = ExtractionHistoryVisualStyle(context)
    private val checks = linkedMapOf<ChartMetric, CheckBox>()
    private val readings = linkedMapOf<ChartMetric, TextView>()
    private var dismiss: (() -> Unit)? = null
    val isOpen: Boolean get() = dismiss != null
    private fun dp(n: Int) = style.dp(n)
    val button = Button(context).apply {
        tag = "product/extraction/series-selector"
        style.button(this)
        setTextColor(style.accent)
        minimumWidth = dp(48); minimumHeight = dp(48)
        background = GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT); cornerRadius = dp(8).toFloat()
            setStroke(dp(1).coerceAtLeast(1), style.accent)
        }
        setPadding(dp(12), dp(4), dp(12), dp(4))
        setCompoundDrawablesRelative(CurveMark(null).apply { setBounds(0, 0, dp(20), dp(20)) }, null, null, null)
        compoundDrawablePadding = dp(6)
        setOnClickListener { show() }
    }
    init { refresh() }
    private fun refresh() { button.text = context.getString(R.string.chart_series_count, chart.visibleMetrics.size) }
    fun refreshReadings() { readings.forEach { (metric, view) ->
        val value = chart.metricReadout(metric)
        view.text = value
        checks[metric]?.let { it.contentDescription = "${it.text}: $value" }
    } }
    fun close() { dismiss?.invoke(); dismiss = null; readings.clear(); checks.clear() }
    private fun show() {
        close()
        val landscape = context.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        fun panelBackground() = GradientDrawable().apply {
            setColor(if (landscape && dark) android.graphics.Color.parseColor("#243233") else style.surface)
            cornerRadius = dp(if (landscape) 12 else 16).toFloat()
            if (landscape) setStroke(dp(1).coerceAtLeast(1), android.graphics.Color.parseColor(
                if (dark) "#6A8380" else "#A7B8B5"))
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = panelBackground()
        }
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(context).apply {
            text = context.getString(R.string.chart_series_title)
            setTextColor(style.ink); textSize = 16f; typeface = TaskTypography.face(context, 500)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!landscape) heading.addView(Button(context).apply {
            text = "×"; contentDescription = context.getString(R.string.chart_series_close)
            style.button(this); minimumWidth = dp(48); minimumHeight = dp(48)
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(heading, LinearLayout.LayoutParams(-1, if (landscape) dp(36) else -2))
        selectableLiveMetrics.forEach { metric ->
            val check = CheckBox(context).apply {
                tag = "product/extraction/series-${metric.name.lowercase()}"
                val name = context.getString(when (metric) {
                    ChartMetric.PRESSURE -> R.string.task_chart_pressure
                    ChartMetric.WATER -> R.string.task_chart_water
                    ChartMetric.FLOW -> R.string.task_chart_flow
                    ChartMetric.TEMPERATURE -> R.string.task_chart_temperature
                    ChartMetric.YIELD -> R.string.chart_series_yield
                    ChartMetric.WEIGHT -> R.string.task_chart_weight
                })
                text = name
                setTextColor(style.ink); textSize = 15f
                setCompoundDrawablesRelative(CurveMark(metric).apply { setBounds(0, 0, dp(24), dp(16)) }, null, null, null)
                compoundDrawablePadding = dp(8)
                typeface = TaskTypography.face(context, 400)
                buttonTintList = ColorStateList.valueOf(style.accent)
                minHeight = dp(48)
                isChecked = metric in chart.visibleMetrics
                setOnClickListener {
                    if (!chart.toggleMetric(metric)) announceForAccessibility(context.getString(R.string.chart_series_keep_one))
                    isChecked = metric in chart.visibleMetrics
                    refresh()
                }
            }
            checks[metric] = check
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            val reading = TextView(context).apply {
                setTextColor(style.ink); textSize = 13f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(dp(6), 0, 0, 0)
            }
            readings[metric] = reading
            row.addView(reading, LinearLayout.LayoutParams(-2, -2))
            row.setOnClickListener { check.performClick() }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        panel.addView(View(context).apply { setBackgroundColor(style.border) },
            LinearLayout.LayoutParams(-1, dp(1).coerceAtLeast(1)).apply { topMargin = dp(8); bottomMargin = dp(4) })
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.chart_series_hint_compact)
            contentDescription = context.getString(R.string.chart_series_hint)
            setTextColor(style.muted); textSize = 12f
            setPadding(0, dp(8), 0, dp(4))
        })
        refreshReadings()
        val scroll = ScrollView(context).apply { addView(panel) }
        // Use the actual app window, not the physical screen: freeform can be much smaller.
        val availableWidth = chart.rootView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val availableHeight = chart.rootView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        fun panelHeight(width: Int, budget: Int): Int {
            scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return scroll.measuredHeight.coerceAtMost(budget.coerceAtLeast(dp(48)))
        }
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val dialog = Dialog(context).apply { setContentView(scroll); setCanceledOnTouchOutside(true) }
            dialog.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.BOTTOM)
            }
            dialog.setOnDismissListener { readings.clear(); checks.clear(); dismiss = null }
            dialog.show()
            dialog.window?.setLayout(-1, panelHeight(availableWidth, (availableHeight * .6f).toInt()))
            dismiss = { dialog.dismiss() }
        } else {
            val width = dp(288).coerceAtMost(availableWidth - dp(16)).coerceAtLeast(dp(48))
            val popup = PopupWindow(scroll, width, panelHeight(width, availableHeight - dp(64)), true).apply {
                setBackgroundDrawable(panelBackground())
                elevation = dp(12).toFloat(); isOutsideTouchable = true
            }
            popup.setOnDismissListener { readings.clear(); checks.clear(); dismiss = null }
            popup.showAsDropDown(button, button.width - width, dp(6))
            dismiss = { popup.dismiss() }
        }
    }
    private inner class CurveMark(private val metric: ChartMetric?) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = dp(2).toFloat(); strokeCap = android.graphics.Paint.Cap.ROUND
            color = metric?.let { visual.liveMetric(it) } ?: this@ChartSeriesSelector.style.accent
        }
        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            paint.pathEffect = when (metric) {
                ChartMetric.WATER -> android.graphics.DashPathEffect(floatArrayOf(dp(5).toFloat(), dp(3).toFloat()), 0f)
                ChartMetric.TEMPERATURE -> android.graphics.DashPathEffect(floatArrayOf(dp(1).toFloat(), dp(3).toFloat()), 0f)
                else -> null
            }
            if (metric != null) canvas.drawLine(b.left.toFloat(), b.exactCenterY(), b.right.toFloat(), b.exactCenterY(), paint)
            else {
                val path = android.graphics.Path().apply {
                    moveTo(b.left.toFloat(), b.bottom - b.height() * .2f)
                    cubicTo(b.left + b.width() * .3f, b.bottom - b.height() * .2f,
                        b.left + b.width() * .28f, b.top + b.height() * .15f,
                        b.left + b.width() * .5f, b.top + b.height() * .2f)
                    cubicTo(b.left + b.width() * .7f, b.top + b.height() * .2f,
                        b.left + b.width() * .7f, b.top + b.height() * .65f,
                        b.right.toFloat(), b.top + b.height() * .65f)
                }
                canvas.drawPath(path, paint)
            }
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Drawable contract") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

}
