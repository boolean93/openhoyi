package io.openhoyi.mobile

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Display only: values are supplied from the existing cup-bound recipe snapshot. */
internal class TaskRecipeSummary(private val activity: ThemedActivity) : LinearLayout(activity) {
    private val values = linkedMapOf<Int, TextView>()
    val text: String get() = values.entries.joinToString("\n") {
        activity.getString(R.string.journal_field_value, activity.getString(it.key), it.value.text)
    }

    init {
        orientation = VERTICAL
        listOf(R.string.task_recipe_temperature, R.string.task_recipe_target,
            R.string.journal_bean, R.string.task_recipe_dose).forEach { label ->
            addView(View(activity).apply { setBackgroundColor(activity.getColor(R.color.mobile_border)) },
                LayoutParams(-1, HoyiUi.dp(activity, 1)))
            val row = LinearLayout(activity).apply {
                orientation = HORIZONTAL
                isBaselineAligned = false
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, HoyiUi.dp(activity, 8), 0, HoyiUi.dp(activity, 8))
            }
            addView(row, LayoutParams(-1, -2))
            HoyiUi.label(activity, row, activity.getString(label), 14, muted = true).apply {
                layoutParams = LayoutParams(0, -2, .4f)
                setPaddingRelative(0, 0, HoyiUi.dp(activity, 8), 0)
            }
            values[label] = HoyiUi.label(activity, row, "—", 16).apply {
                tag = "recipe-value-$label"
                layoutParams = LayoutParams(0, -2, .6f)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 0)
            }
        }
    }

    fun present(fields: Map<Int, String?>) {
        values.forEach { (label, view) ->
            val value = fields[label] ?: "—"
            if (view.text.toString() != value) view.text = value
        }
    }
}
