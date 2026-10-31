package io.openhoyi.mobile

import android.widget.Button
import android.widget.LinearLayout

/** Selecting fewer stages hides fields without discarding their draft values. */
internal class CurveStageCountTabs(private val activity: ThemedActivity, selected: Int, private val productStyle: TaskProductFormStyle? = null, private val changed: (Int) -> Unit) : LinearLayout(activity) {
    var selectedCount = selected
        private set
    private val buttons = linkedMapOf<Int, Button>()
    init {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        tag = "curve-stage-count"
        background = productStyle?.visual?.let { it.shape(it.surface, true) }
            ?: HoyiUi.shape(activity, R.color.mobile_background, 12, R.color.mobile_border)
        (1..4).forEach { count ->
            buttons[count] = HoyiUi.button(activity, this, count.toString()) {
                if (selectedCount != count) {
                    selectedCount = count
                    updateSelection()
                    changed(count)
                }
            }.apply {
                tag = "curve-stage-count-$count"
                contentDescription = activity.getString(R.string.profiles_stage, count)
                layoutParams = LayoutParams(0, -2, 1f)
                asTaskTextAction()
                productStyle?.visual?.button(this)
                setPadding(HoyiUi.dp(activity, 8), HoyiUi.dp(activity, 8), HoyiUi.dp(activity, 8), HoyiUi.dp(activity, 8))
            }
        }
        updateSelection()
    }
    private fun updateSelection() {
        buttons.forEach { (count, button) ->
            button.isSelected = count == selectedCount
            if (productStyle != null) {
                val visual = productStyle.visual
                button.setTextColor(if (button.isSelected) android.graphics.Color.WHITE else visual.accent)
                button.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(visual.selected),
                    android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), visual.shape(if (button.isSelected) visual.primary else android.graphics.Color.TRANSPARENT, true, 8))
                        addState(intArrayOf(), visual.shape(if (button.isSelected) visual.primary else android.graphics.Color.TRANSPARENT, false, 8))
                    }, null)
            } else {
            button.setTextColor(activity.getColor(if (button.isSelected) android.R.color.white else R.color.mobile_accent))
            val fill = if (button.isSelected) R.color.mobile_primary_button else android.R.color.transparent
            val states = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(activity, fill, 8, R.color.mobile_accent))
                addState(intArrayOf(), HoyiUi.shape(activity, fill, 8))
            }
            button.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(activity.getColor(R.color.mobile_accent_soft)), states, null)
            }
        }
    }
}
