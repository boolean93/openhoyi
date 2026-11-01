package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure read-only view composition, shared by list preview and standalone detail. */
internal class LegacyHistoryContent(context: Context, private val visual: LegacyVisualStyle,
    private val openDetails: (LegacyShot) -> Unit) : LinearLayout(context) {
    private val style = visual.palette
    private val title = style.text("", TaskProductVisualStyle.title, "legacy/history/selected/title")
    private val metadata = style.text("", TaskProductVisualStyle.auxiliary, "legacy/history/selected/metadata", true)
    private val chart = LegacyShotChartView(context).apply { visualStyle = visual; tag = "legacy/history/selected/chart" }
    private val chartViewport = LegacyChartViewport(context, chart)
    private val action = style.button(android.widget.Button(context), outline = true).apply {
        text = context.getString(R.string.ui_full_details); tag = "legacy/history/selected/open"
        setOnClickListener { selected?.let(openDetails) }
    }
    private var selected: LegacyShot? = null
    init {
        orientation = VERTICAL; visual.panel(this)
        val heading = LegacyNaturalFlow(context, style.dp(16), style.dp(8)).apply {
            addView(title); addView(visual.readonly()); addView(metadata)
        }
        style.add(this, heading)
        addView(chartViewport, LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(8) })
        val footer = LegacyNoticeActionRow(context, visual.warning(context.getString(R.string.legacy_visual_notice),
            "legacy/history/selected/notice"), action, style.dp(16))
        style.add(this, footer, 16)
        present(null)
    }
    fun present(shot: LegacyShot?, unavailable: String? = null) {
        selected = shot
        title.text = shot?.profileName?.ifBlank { context.getString(R.string.legacy_history_unnamed) }
            ?: unavailable ?: context.getString(R.string.legacy_history_empty_title)
        metadata.text = shot?.let { legacyMetadata(context, it) }.orEmpty()
        metadata.visibility = if (shot == null) GONE else VISIBLE
        chart.points = shot?.points.orEmpty()
        chartViewport.visibility = if (shot == null) INVISIBLE else VISIBLE
        action.isEnabled = shot != null
    }
}

internal fun legacyMetadata(context: Context, shot: LegacyShot) = context.getString(R.string.legacy_history_row,
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(shot.createdAtMs)), shot.durationSec.toString())

/** The same chart stays mounted; its actual plot keeps 160dp, separately from real font/axis bands.
 * A constrained viewport scrolls the middle only. No resize mutates the selection or reader. */
internal class LegacyChartViewport(context: Context, val chart: LegacyShotChartView) : ScrollView(context) {
    init {
        isFillViewport = true; tag = "legacy/chart/viewport"
        addView(chart, android.widget.FrameLayout.LayoutParams(-1, -2))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val available = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val minimum = chart.minimumVisualHeight(width, 160)
        val desired = maxOf(minimum, available)
        if (chart.layoutParams.height != desired) chart.layoutParams = chart.layoutParams.apply { height = desired }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        chart.recordViewportMeasurement(widthMeasureSpec, heightMeasureSpec, measuredHeight)
    }
}

/** Whole natural-width groups wrap without replacing or reparenting any child. */
internal class LegacyNaturalFlow(context: Context, private val gap: Int, private val rowGap: Int) : android.view.ViewGroup(context) {
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0; var y = 0; var row = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index); if (child.visibility == GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + child.measuredWidth > available) { y += row + rowGap; x = 0; row = 0 }
            x += child.measuredWidth + gap; row = maxOf(row, child.measuredHeight)
        }
        setMeasuredDimension(resolveSize(available, widthMeasureSpec), resolveSize(y + row, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var x = 0; var y = 0; var row = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index); if (child.visibility == GONE) continue
            if (x > 0 && x + child.measuredWidth > width) { y += row + rowGap; x = 0; row = 0 }
            val start = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
            child.layout(start, y, start + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gap; row = maxOf(row, child.measuredHeight)
        }
    }
}

/** Footer measures the real action first; complete notice wraps, never the action out of its dock. */
private class LegacyNoticeActionRow(context: Context, private val notice: View, private val action: View,
    private val gap: Int) : LinearLayout(context) {
    init { isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL; addView(notice); addView(action) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        action.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val wide = action.measuredWidth + (160 * resources.displayMetrics.density).toInt() + gap <= width
        orientation = if (wide) HORIZONTAL else VERTICAL
        notice.layoutParams = LayoutParams(if (wide) 0 else -1, -2, if (wide) 1f else 0f)
        action.layoutParams = LayoutParams(if (wide) -2 else -1, -2).apply {
            marginStart = if (wide) gap else 0; topMargin = if (wide) 0 else gap / 2
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
