package io.openhoyi.mobile

import android.widget.Button
import android.widget.LinearLayout

/** Physical metrics are directly selectable; selection never changes the underlying samples. */
internal class ComparisonMetricTabs(private val activity: ThemedActivity, selected: ChartMetric,
    label: (ChartMetric) -> String, private val changed: (ChartMetric) -> Unit, private val visual: ExtractionHistoryVisualStyle?) : LinearLayout(activity) {
    constructor(activity: ThemedActivity, selected: ChartMetric, label: (ChartMetric) -> String,
        changed: (ChartMetric) -> Unit) : this(activity, selected, label, changed, null)
    private var selectedMetric = selected
    private val buttons = linkedMapOf<ChartMetric, Button>()
    init {
        orientation = HORIZONTAL
        isBaselineAligned = false
        tag = "comparison-metric"
        background = visual?.palette?.let { it.shape(it.page, true, 12) }
            ?: HoyiUi.shape(activity, R.color.mobile_background, 12, R.color.mobile_border)
        ChartMetric.entries.forEach { metric ->
            buttons[metric] = HoyiUi.button(activity, this, label(metric)) {
                if (selectedMetric != metric) {
                    selectedMetric = metric
                    updateSelection()
                    changed(metric)
                }
            }.apply {
                tag = "comparison-metric-${metric.name}"
                layoutParams = LayoutParams(0, -2, 1f)
                asTaskTextAction()
            }
        }
        updateSelection()
    }
    private fun updateSelection() {
        buttons.forEach { (metric, button) ->
            button.isSelected = metric == selectedMetric
            visual?.palette?.let { style ->
                style.button(button)
                button.setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(style.onDisabled, if (button.isSelected) style.accent else style.muted)))
                val fill = if (button.isSelected) style.selected else style.surface
                button.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(style.selected),
                    android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(-android.R.attr.state_enabled), style.shape(style.disabled, true))
                        addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply {
                            setColor(fill); cornerRadius = style.dp(12).toFloat(); setStroke(style.dp(1), style.accent)
                        })
                        addState(intArrayOf(), style.shape(fill, true))
                    }, null)
            }
            if (visual != null) return@forEach
            val fill = if (button.isSelected) R.color.mobile_accent_soft else android.R.color.transparent
            val states = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(activity, fill, 8, R.color.mobile_accent))
                addState(intArrayOf(), HoyiUi.shape(activity, fill, 8))
            }
            button.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(activity.getColor(R.color.mobile_accent_soft)), states, null)
        }
    }
}
