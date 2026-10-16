package io.openhoyi.mobile

import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Observed outcome values, not estimates from chart alignment or interpolation. */
internal class ComparisonSummaryTable(private val activity: ThemedActivity) : LinearLayout(activity) {
    private val headers = mutableListOf<TextView>()
    private val values = List(2) { mutableListOf<TextView>() }
    private val labels = listOf(R.string.history_duration, R.string.history_end_scale, R.string.journal_taste)
    init {
        orientation = VERTICAL
        tag = "comparison-summary-table"
        fun row() = LinearLayout(activity).apply {
            orientation = HORIZONTAL; isBaselineAligned = false
            this@ComparisonSummaryTable.addView(this, LayoutParams(-1, -2))
        }
        fun cell(parent: LinearLayout, value: String, index: Int, heading: Boolean) =
            HoyiUi.label(activity, parent, value, if (heading) 14 else 16, muted = heading).apply {
                layoutParams = LayoutParams(0, -1, if (index == 0) .7f else 1f)
                gravity = Gravity.CENTER_VERTICAL or if (index == 0) Gravity.START else Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = HoyiUi.shape(activity, if (heading) R.color.mobile_background else R.color.mobile_surface,
                    0, R.color.mobile_border)
            }
        val header = row()
        cell(header, "", 0, true)
        repeat(2) { headers += cell(header, "", it + 1, true) }
        labels.forEachIndexed { rowIndex, label ->
            val line = row().apply { tag = "comparison-summary-row-$rowIndex" }
            cell(line, activity.getString(label), 0, true)
            repeat(2) { column ->
                values[column] += cell(line, "—", column + 1, false).apply {
                    if (rowIndex == 2) {
                        tag = "full-text-details"
                        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                        minimumHeight = dp(48)
                        background = android.graphics.drawable.RippleDrawable(
                            android.content.res.ColorStateList.valueOf(activity.getColor(R.color.mobile_accent_soft)),
                            android.graphics.drawable.StateListDrawable().apply {
                                addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(activity,
                                    R.color.mobile_surface, 0, R.color.mobile_accent))
                                addState(intArrayOf(), HoyiUi.shape(activity, R.color.mobile_surface, 0, R.color.mobile_border))
                            }, null)
                    }
                }
            }
        }
    }
    fun update(column: Int, entry: BrewJournal.Entry?) {
        headers[column].text = activity.getString(if (column == 0) R.string.journal_first else R.string.journal_second) +
            " · " + (entry?.observation?.let { activity.journalDate(it.startedAtMs) } ?: "—")
        val observation = entry?.observation
        val contents = listOf(
            observation?.elapsedMs?.let { activity.getString(R.string.history_duration_value, "%.1f".format(Locale.ROOT, it / 1000.0)) } ?: "—",
            observation?.weightHundredthsGram?.let { "%.2f g".format(Locale.ROOT, it / 100.0) } ?: "—",
            entry?.notes?.taste?.takeIf { it.isNotBlank() } ?: "—")
        values[column].forEachIndexed { index, view ->
            if (view.text.toString() != contents[index]) view.text = contents[index]
            view.contentDescription = activity.getString(labels[index]) + " · " + contents[index]
        }
        values[column][2].apply {
            setOnClickListener { activity.beanDetails(activity.getString(R.string.journal_taste), contents[2]) }
            isClickable = !entry?.notes?.taste.isNullOrBlank()
            isFocusable = isClickable
            tooltipText = contents[2]
        }
    }
    private fun dp(value: Int) = HoyiUi.dp(activity, value)
}
