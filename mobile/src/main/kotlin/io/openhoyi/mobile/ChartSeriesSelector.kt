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
            cornerRadius = dp(16).toFloat()
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
        heading.addView(Button(context).apply {
            text = "×"; contentDescription = context.getString(R.string.chart_series_close)
            style.button(this); minimumWidth = dp(48); minimumHeight = dp(48)
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(heading)
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
                text = "$name · ${if(metric == ChartMetric.WATER) "mL" else metric.unit}"
                setTextColor(visual.liveMetric(metric)); textSize = 15f
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
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.chart_series_hint)
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
            val width = dp(320).coerceAtMost(availableWidth - dp(16)).coerceAtLeast(dp(48))
            val popup = PopupWindow(scroll, width, panelHeight(width, availableHeight - dp(64)), true).apply {
                setBackgroundDrawable(panelBackground())
                elevation = dp(12).toFloat(); isOutsideTouchable = true
            }
            popup.setOnDismissListener { readings.clear(); checks.clear(); dismiss = null }
            popup.showAsDropDown(button, -dp(120), dp(4))
            dismiss = { popup.dismiss() }
        }
    }
}
