package io.openhoyi.mobile

import android.widget.Button
import android.widget.LinearLayout

/** Physical metrics are directly selectable; selection never changes the underlying samples. */
internal class ComparisonMetricTabs(private val activity: ThemedActivity, selected: ChartMetric,
    label: (ChartMetric) -> String, private val changed: (ChartMetric) -> Unit) : LinearLayout(activity) {
    private var selectedMetric = selected
    private val buttons = linkedMapOf<ChartMetric, Button>()
    init {
        orientation = HORIZONTAL
        isBaselineAligned = false
        tag = "comparison-metric"
        background = HoyiUi.shape(activity, R.color.mobile_background, 12, R.color.mobile_border)
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
