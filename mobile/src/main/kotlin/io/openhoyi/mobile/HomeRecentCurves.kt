package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Real usage choices; this component never starts a shot or records a use. */
internal class HomeRecentCurves(private val activity: ThemedActivity, primary: LinearLayout,
    secondary: LinearLayout, private val active: () -> Boolean, private val choose: (CurveLibraryItem) -> Unit) {
    private val app get() = activity.application as MobileApplication
    private var items = emptyList<CurveLibraryItem>()
    private var dialog: AlertDialog? = null
    private val block = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        tag = "home-recent-curves"
        primary.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private val heading = HoyiUi.label(activity, block, activity.getString(R.string.curve_sort_recent), 20, true)
    private val empty = HoyiUi.label(activity, block, "", 14, muted = true)
    private val rows = (0..2).map { index ->
        HoyiUi.label(activity, block, "", 16).apply {
            tag = "home-recent-curve-$index"
            minimumHeight = HoyiUi.dp(activity, 48)
            setPadding(0, HoyiUi.dp(activity, 8), 0, HoyiUi.dp(activity, 8))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true; isFocusable = true
            val states = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused),
                    HoyiUi.shape(activity, android.R.color.transparent, 0, R.color.mobile_accent))
                addState(intArrayOf(), HoyiUi.shape(activity, android.R.color.transparent, 0))
            }
            background = RippleDrawable(ColorStateList.valueOf(activity.getColor(R.color.mobile_accent_soft)),
                states, HoyiUi.shape(activity, android.R.color.white, 0))
            setOnClickListener { items.getOrNull(index)?.let(::select) }
        }
    }
    private val fallback = HoyiUi.button(activity, secondary, activity.getString(R.string.curve_sort_recent)) {
        if (!active()) {
            val builder = AlertDialog.Builder(activity).setTitle(R.string.curve_sort_recent)
            if (items.isEmpty()) builder.setMessage(empty.text)
            else builder.setItems(items.map { it.name }.toTypedArray()) { _, index -> select(items[index]) }
            dialog?.dismiss()
            dialog = builder.setNegativeButton(android.R.string.cancel, null).show()
        }
    }.apply { tag = "home-recent-fallback" }

    private fun select(item: CurveLibraryItem) {
        if (active()) return
        val current = app.curves.find(item.id) ?: return
        if (app.curves.canStart(current)) choose(current)
        else android.widget.Toast.makeText(activity, R.string.curve_cannot_start, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun refresh() {
        val ledger = app.curveUsageResult.getOrNull()
        items = if (ledger == null) emptyList() else CurveOrdering.recentUsed(app.curves.items, ledger, 3)
        empty.setText(if (ledger == null) R.string.task_brew_usage_unavailable else R.string.task_brew_usage_empty)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rows.forEachIndexed { index, row ->
            val item = items.getOrNull(index)
            row.visibility = if (item == null) View.GONE else View.VISIBLE
            row.text = item?.let { it.name + activity.getString(R.string.curve_usage_count, ledger!!.stats(it.id).count) }.orEmpty()
            row.contentDescription = row.text
        }
        updateAvailability()
    }

    fun updateAvailability() {
        val available = !active()
        fallback.isEnabled = available
        rows.forEachIndexed { index, row ->
            row.isEnabled = available && items.getOrNull(index)?.let(app.curves::canStart) == true
            row.setTextColor(activity.getColor(if (row.isEnabled) R.color.mobile_text else R.color.mobile_muted))
        }
    }

    /** Keep the chart's budget; short windows retain the same choices in the supporting pane. */
    fun fit(width: Int, remainingHeight: Int): Int {
        block.measure(View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val fits = block.measuredHeight <= remainingHeight
        block.visibility = if (fits) View.VISIBLE else View.GONE
        fallback.visibility = if (fits) View.GONE else View.VISIBLE
        return if (fits) block.measuredHeight else 0
    }

    fun dismiss() { dialog?.dismiss(); dialog = null }
}
