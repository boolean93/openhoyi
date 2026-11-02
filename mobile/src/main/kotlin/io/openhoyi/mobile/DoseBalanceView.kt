package io.openhoyi.mobile

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Displays an explicit preview or a matched ledger receipt; never changes inventory. */
internal class DoseBalanceView(private val activity: ThemedActivity, private val receipt: Boolean = false,
    private val style: TaskProductVisualStyle? = null) : LinearLayout(activity) {
    private val before: TextView
    private val after: TextView
    private val summary: TextView
    init {
        if (style == null) {
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
        } else {
            orientation = VERTICAL; visibility = View.GONE
            tag = if (receipt) "product/preparation/receipt" else "product/preparation/preview"
            val id = if (receipt) "receipt" else "preview"
            addView(style.text(activity.getString(if (receipt) R.string.bean_workflow_receipt else R.string.bean_workflow_preview),
                TaskProductVisualStyle.section), LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            val row = style.row().apply { gravity = Gravity.CENTER_VERTICAL }
            addView(row, LayoutParams(-1, -2))
            fun value(label: Int, key: String, accent: Boolean): TextView {
                val cell = style.column()
                row.addView(cell, LayoutParams(0, -2, 1f))
                val quantity = style.quantity("", "g", TaskProductVisualStyle.largeValue, "product/preparation/$id/$key")
                if (accent) listOf("number", "unit").forEach { part ->
                    quantity.findViewWithTag<TextView>("product/preparation/$id/$key/$part").setTextColor(style.accent)
                }
                cell.addView(quantity, LayoutParams(-1, -2))
                cell.addView(style.text(activity.getString(label), TaskProductVisualStyle.auxiliary, secondary = true), LayoutParams(-1, -2))
                return quantity.findViewWithTag("product/preparation/$id/$key/number")
            }
            before = value(R.string.dose_balance_before, "before", false)
            row.addView(android.widget.ImageView(activity).apply {
                setImageDrawable(HomeVisualIcon.drawable(activity, "back", style.muted))
                scaleX = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) 1f else -1f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(dp(24), dp(24)).apply { marginStart = dp(8); marginEnd = dp(8) })
            after = value(R.string.dose_balance_after, "after", true)
            val notice = style.row().apply { setPadding(dp(16), dp(16), dp(16), dp(16)); background = style.shape(style.selected) }
            addView(notice, LayoutParams(-1, -2).apply { topMargin = dp(16) })
            notice.addView(HomeVisualIcon(activity, "info", style.accent), LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(8) })
            summary = style.text("", TaskProductVisualStyle.auxiliary, if (receipt) "dose-balance-receipt" else "dose-balance-preview")
            notice.addView(summary, LayoutParams(0, -2, 1f))
        }
    }
    fun update(beforeMg: Long?, afterMg: Long?) {
        if (beforeMg == null || afterMg == null) { visibility = View.GONE; summary.text = ""; return }
        fun quantity(mg: Long) = SpannableString(BeanQuantity.formatGrams(mg) + " g").apply {
            setSpan(RelativeSizeSpan(.55f), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style == null) { before.text = quantity(beforeMg); after.text = quantity(afterMg) }
        else { before.text = BeanQuantity.formatGrams(beforeMg); after.text = BeanQuantity.formatGrams(afterMg) }
        summary.text = activity.getString(if (receipt) R.string.dose_balance_receipt else R.string.dose_balance_preview,
            BeanQuantity.formatGrams(beforeMg), BeanQuantity.formatGrams(afterMg))
        visibility = View.VISIBLE
    }
    private fun dp(value: Int) = HoyiUi.dp(activity, value)
}
