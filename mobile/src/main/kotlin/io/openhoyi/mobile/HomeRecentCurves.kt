package io.openhoyi.mobile

import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Real usage choices; this component never starts a shot or records a use. */
internal class HomeRecentCurves(private val activity: ThemedActivity, primary: LinearLayout,
    secondary: LinearLayout, private val active: () -> Boolean, private val choose: (CurveLibraryItem) -> Unit) {
    private val app get() = activity.application as MobileApplication
    private val style = TaskProductVisualStyle(activity)
    private var items = emptyList<CurveLibraryItem>()
    private var dialog: AlertDialog? = null
    private val block = style.column("home-recent-curves").apply {
        primary.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private val heading = style.text(activity.getString(R.string.curve_sort_recent), TaskProductVisualStyle.section).also { style.add(block, it, 8) }
    private val list = style.column()
    private val listScroll = ScrollView(activity).apply {
        tag = "product/home/recent-scroll"; isFillViewport = false; addView(list)
        block.addView(this, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(8) })
    }
    private val empty = style.text("", TaskProductVisualStyle.auxiliary, secondary = true).also { style.add(list, it) }
    private val names = (0..2).map { style.text("", TaskProductVisualStyle.body, "product/home/recent/$it/name") }
    private val descriptions = (0..2).map { style.text("", TaskProductVisualStyle.auxiliary, "product/home/recent/$it/summary", true) }
    private val rows = (0..2).map { index ->
        style.row("home-recent-curve-$index").apply {
            minimumHeight = style.dp(48); gravity = android.view.Gravity.CENTER_VERTICAL
            names[index].apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
            addView(names[index], LinearLayout.LayoutParams(0, -2, 1f))
            descriptions[index].gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            addView(descriptions[index], LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(16) })
            style.clickable(this)
            setOnClickListener { items.getOrNull(index)?.let(::select) }
            style.add(list, this)
        }
    }
    private val fallback = style.action(secondary, activity.getString(R.string.curve_sort_recent), "home-recent-fallback") {
        if (!active()) {
            val builder = AlertDialog.Builder(activity).setTitle(R.string.curve_sort_recent)
            if (items.isEmpty()) builder.setMessage(empty.text)
            else builder.setItems(items.map { it.name }.toTypedArray()) { _, index -> select(items[index]) }
            dialog?.dismiss()
            dialog = builder.setNegativeButton(android.R.string.cancel, null).show()
        }
    }
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
            names[index].text = item?.name.orEmpty()
            descriptions[index].text = item?.let { activity.getString(R.string.curve_usage_count, ledger!!.stats(it.id).count) }.orEmpty()
            row.contentDescription = listOf(names[index].text, descriptions[index].text).joinToString(" · ")
        }
        updateAvailability()
    }
    fun updateAvailability() {
        val available = !active()
        fallback.isEnabled = available
        rows.forEachIndexed { index, row ->
            row.isEnabled = available && items.getOrNull(index)?.let(app.curves::canStart) == true
            names[index].setTextColor(if (row.isEnabled) style.ink else style.muted)
        }
    }
    /** Standard keeps two actual rows, large text one; all three stay locally reachable.
     * Only genuinely short windows retain the original supporting dialog fallback. */
    fun fit(width: Int, remainingHeight: Int): Int {
        val exact = View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), View.MeasureSpec.EXACTLY)
        val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        heading.measure(exact, natural)
        list.measure(exact, natural)
        val visibleRows = rows.filter { it.visibility != View.GONE }
        val count = if (activity.resources.configuration.fontScale > 1.1f) 1 else 2
        val rowMinimum = if (visibleRows.isEmpty()) empty.measuredHeight else visibleRows.take(count).sumOf { it.measuredHeight }
        val headHeight = heading.measuredHeight + style.dp(16)
        val minimum = headHeight + rowMinimum
        val fits = remainingHeight >= minimum
        block.visibility = if (fits) View.VISIBLE else View.GONE
        fallback.visibility = if (fits) View.GONE else View.VISIBLE
        val allocated = if (fits) minOf(headHeight + list.measuredHeight, remainingHeight) else 0
        (block.layoutParams as LinearLayout.LayoutParams).apply { height = allocated; weight = 0f }
        return allocated
    }
    fun dismiss() { dialog?.dismiss(); dialog = null }
}
