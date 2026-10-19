package io.openhoyi.mobile

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Display only: values are supplied from the existing cup-bound recipe snapshot. */
internal class TaskRecipeSummary(private val activity: ThemedActivity, private val visual: ExtractionHistoryVisualStyle? = null) : LinearLayout(activity) {
    private val values = linkedMapOf<Int, TextView>()
    private val units = linkedMapOf<Int, TextView>()
    val text: String get() = values.entries.joinToString("\n") {
        activity.getString(R.string.journal_field_value, activity.getString(it.key), if (visual != null) it.value.contentDescription ?: it.value.text else it.value.text)
    }

    init {
        orientation = VERTICAL
        listOf(R.string.task_recipe_temperature, R.string.task_recipe_target,
            R.string.journal_bean, R.string.task_recipe_dose).forEach { label ->
            addView(View(activity).apply { setBackgroundColor(visual?.palette?.border ?: activity.getColor(R.color.mobile_border)) },
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
            val quantity = visual?.palette?.row("product/extraction/recipe/$label/value")?.apply {
                isBaselineAligned = true; gravity = Gravity.END or Gravity.BOTTOM
                row.addView(this, LayoutParams(0, -2, .6f))
            }
            values[label] = HoyiUi.label(activity, quantity ?: row, "—", 16).apply {
                tag = "recipe-value-$label"
                layoutParams = if (quantity == null) LayoutParams(0, -2, .6f) else LayoutParams(-2, -2)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 0)
            }
            visual?.palette?.let { style ->
                style.apply(row.getChildAt(0) as TextView, TaskProductVisualStyle.auxiliary, true)
                style.apply(values.getValue(label), TaskProductVisualStyle.body)
                units[label] = style.text("", TaskProductVisualStyle.auxiliary, "product/extraction/recipe/$label/unit", true).apply {
                    quantity!!.addView(this, LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
                }
            }
        }
    }

    fun present(fields: Map<Int, String?>) {
        values.forEach { (label, view) ->
            val value = fields[label] ?: "—"
            if (visual != null) view.contentDescription = value
            val match = if (visual != null) Regex("^([+-]?[0-9]+(?:\\.[0-9]+)?) (g|°C)$").matchEntire(value) else null
            val number = match?.groupValues?.get(1) ?: value
            if (view.text.toString() != number) view.text = number
            units[label]?.apply { text = match?.groupValues?.get(2).orEmpty(); visibility = if (match == null) View.GONE else View.VISIBLE }
        }
    }
}
