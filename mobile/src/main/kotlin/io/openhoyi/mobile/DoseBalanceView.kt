package io.openhoyi.mobile

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Displays an explicit preview or a matched ledger receipt; never changes inventory. */
internal class DoseBalanceView(private val activity: ThemedActivity, private val receipt: Boolean = false) : LinearLayout(activity) {
    private val before: TextView
    private val after: TextView
    private val summary: TextView
    init {
        orientation = VERTICAL
        visibility = View.GONE
        setPadding(0, dp(16), 0, dp(16))
        val row = LinearLayout(activity).apply {
            orientation = HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL
            this@DoseBalanceView.addView(this, LayoutParams(-1, -2))
        }
        fun value(label: Int, accent: Boolean): TextView {
            val cell = LinearLayout(activity).apply { orientation = VERTICAL }
            row.addView(cell, LayoutParams(0, -2, 1f))
            HoyiUi.label(activity, cell, activity.getString(label), 14, muted = true)
            return HoyiUi.label(activity, cell, "", 40, true).apply {
                typeface = TaskTypography.face(activity, 700)
                fontFeatureSettings = "tnum"
                if (accent) setTextColor(activity.getColor(R.color.mobile_accent))
            }
        }
        before = value(R.string.dose_balance_before, false)
        HoyiUi.label(activity, row, if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "←" else "→", 24, muted = true).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(dp(8), 0, dp(8), 0)
        }
        after = value(R.string.dose_balance_after, true)
        summary = HoyiUi.label(activity, this, "", 14, muted = true).apply {
            tag = if (receipt) "dose-balance-receipt" else "dose-balance-preview"
            setPadding(0, dp(8), 0, 0)
        }
    }
    fun update(beforeMg: Long?, afterMg: Long?) {
        if (beforeMg == null || afterMg == null) { visibility = View.GONE; summary.text = ""; return }
        fun quantity(mg: Long) = SpannableString(BeanQuantity.formatGrams(mg) + " g").apply {
            setSpan(RelativeSizeSpan(.55f), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        before.text = quantity(beforeMg); after.text = quantity(afterMg)
        summary.text = activity.getString(if (receipt) R.string.dose_balance_receipt else R.string.dose_balance_preview,
            BeanQuantity.formatGrams(beforeMg), BeanQuantity.formatGrams(afterMg))
        visibility = View.VISIBLE
    }
    private fun dp(value: Int) = HoyiUi.dp(activity, value)
}
