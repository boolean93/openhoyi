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
    private val headingRow = style.row("product/home/recent-heading").apply {
        minimumHeight = style.dp(48); gravity = android.view.Gravity.CENTER_VERTICAL
        style.divider(block, 0, 0)
        addView(style.text(activity.getString(R.string.curve_sort_recent), TaskProductVisualStyle.section), LinearLayout.LayoutParams(0, -2, 1f))
        style.action(this, activity.getString(R.string.home_visual_more_recipes), "product/home/recent-more", top = 0) {
            activity.startActivity(android.content.Intent(activity, CurveActivity::class.java))
        }.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(16) }
        style.add(block, this)
        style.divider(block, 0, 0)
    }
    private val list = style.column()
    private val listScroll = ScrollView(activity).apply {
        tag = "product/home/recent-scroll"; isFillViewport = false; addView(list)
        block.addView(this, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(8) })
    }
    private val empty = style.text("", TaskProductVisualStyle.auxiliary, secondary = true).also { style.add(list, it) }
    private val names = (0..2).map { style.text("", TaskProductVisualStyle.action, "product/home/recent/$it/name") }
    private val descriptions = (0..2).map { style.text("", TaskProductVisualStyle.auxiliary, "product/home/recent/$it/summary", true) }
    private val modes = (0..2).map { style.text("", TaskProductVisualStyle.auxiliary, "product/home/recent/$it/mode", true) }
    private val parameters = (0..2).map { style.text("", TaskProductVisualStyle.auxiliary, "product/home/recent/$it/parameters", true) }
    private val rows = (0..2).map { index ->
        val left = HomeRecentFields(activity, style.dp(8), listOf(names[index], modes[index], descriptions[index]))
        object : LinearLayout(activity) {
            init {
                tag = "home-recent-curve-$index"; isBaselineAligned = false
                minimumHeight = style.dp(48); gravity = android.view.Gravity.CENTER_VERTICAL
                addView(left, LayoutParams(0, -2, 1f))
                addView(parameters[index], LayoutParams(-2, -2).apply { marginStart = style.dp(16) })
                style.clickable(this)
                setOnClickListener { items.getOrNull(index)?.let(::select) }
                // A drawn rule does not change the real row's natural text height.
                foreground = object : android.graphics.drawable.Drawable() {
                    private val paint = android.graphics.Paint().apply { color = this@HomeRecentCurves.style.border; strokeWidth = activity.resources.displayMetrics.density }
                    override fun draw(canvas: android.graphics.Canvas) { canvas.drawLine(0f, bounds.bottom - paint.strokeWidth / 2, bounds.right.toFloat(), bounds.bottom - paint.strokeWidth / 2, paint) }
                    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
                    @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
                }
            }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                parameters[index].measure(natural, natural)
                names[index].measure(natural, natural)
                val available = View.MeasureSpec.getSize(widthMeasureSpec)
                val stacked = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
                    parameters[index].measuredWidth + names[index].measuredWidth + style.dp(16) > available
                orientation = if (stacked) VERTICAL else HORIZONTAL
                (left.layoutParams as LayoutParams).apply { width = if (stacked) -1 else 0; weight = if (stacked) 0f else 1f }
                (parameters[index].layoutParams as LayoutParams).apply {
                    width = if (stacked) -1 else -2; marginStart = if (stacked) 0 else style.dp(16)
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.also { style.add(list, it) }
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
            val presentation = item?.let(HomeRecipePresentation::from)
            modes[index].text = presentation?.description(activity).orEmpty()
            parameters[index].text = presentation?.compact(activity).orEmpty()
            row.contentDescription = listOf(names[index].text, modes[index].text, descriptions[index].text, parameters[index].text).joinToString(" · ")
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
        headingRow.measure(exact, natural)
        list.measure(exact, natural)
        val visibleRows = rows.filter { it.visibility != View.GONE }
        val count = if (activity.resources.configuration.fontScale > 1.1f) 1 else 2
        val rowMinimum = if (visibleRows.isEmpty()) empty.measuredHeight else visibleRows.take(count).sumOf { it.measuredHeight }
        val headHeight = headingRow.measuredHeight + style.dp(2) + style.dp(8)
        val minimum = headHeight + rowMinimum
        val fits = remainingHeight >= minimum
        block.visibility = if (fits) View.VISIBLE else View.GONE
        fallback.visibility = if (fits) View.GONE else View.VISIBLE
        val allocated = if (fits) minimum else 0
        (block.layoutParams as LinearLayout.LayoutParams).apply { height = allocated; weight = 0f }
        return allocated
    }
    fun dismiss() { dialog?.dismiss(); dialog = null }
}

/** Natural inline fields, with only real width shortage introducing another line. */
private class HomeRecentFields(context: android.content.Context, private val gap: Int,
    private val fields: List<android.widget.TextView>) : android.widget.FrameLayout(context) {
    private var positions = emptyList<Pair<Int, Int>>()
    init { fields.forEach { addView(it) } }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val childWidth = if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED) natural
            else View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST)
        fields.forEach { it.measure(childWidth, natural) }
        val lines = mutableListOf<MutableList<Int>>()
        var used = 0
        fields.indices.forEach { index ->
            val field = fields[index]
            if (lines.isEmpty() || (View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
                used > 0 && used + gap + field.measuredWidth > width)) { lines.add(mutableListOf()); used = 0 }
            if (used > 0) used += gap
            lines.last().add(index); used += field.measuredWidth
        }
        val offsets = MutableList(fields.size) { 0 to 0 }
        var y = 0; var naturalWidth = 0
        lines.forEach { line ->
            val baseline = line.maxOf { fields[it].baseline }
            val below = line.maxOf { fields[it].measuredHeight - fields[it].baseline }
            var x = 0
            line.forEach { index -> offsets[index] = x to (y + baseline - fields[index].baseline); x += fields[index].measuredWidth + gap }
            naturalWidth = maxOf(naturalWidth, (x - gap).coerceAtLeast(0)); y += baseline + below
        }
        positions = offsets
        setMeasuredDimension(resolveSize(naturalWidth, widthMeasureSpec), resolveSize(y, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        fields.forEachIndexed { index, field ->
            val (logicalX, y) = positions[index]
            val x = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - logicalX - field.measuredWidth else logicalX
            field.layout(x, y, x + field.measuredWidth, y + field.measuredHeight)
        }
    }
}
