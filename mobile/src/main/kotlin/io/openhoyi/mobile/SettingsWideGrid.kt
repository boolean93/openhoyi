package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Permanent children: widths reflow while editor, listeners and focus stay attached. */
internal class SettingsWideGrid(
    context: Context, private val style: TaskProductVisualStyle, private val maxColumns: Int,
    minimumCellDp: Int, minimumWideDp: Int, gapDp: Int = 12,
    private val onColumns: (Int) -> Unit = {},
) : ViewGroup(context) {
    private val cell = style.dp(minimumCellDp)
    private val wide = style.dp(minimumWideDp)
    private val gap = style.dp(gapDp)
    private var previousColumns = 0
    private data class Slot(val child: View, val x: Int, val y: Int)
    private val slots = mutableListOf<Slot>()
    override fun generateDefaultLayoutParams() = LayoutParams(-1, -2)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val columns = if (width < wide) 1 else ((width + gap) / (cell + gap)).coerceIn(1, maxColumns)
        if (columns != previousColumns) { previousColumns = columns; onColumns(columns) }
        val usable = (width - (columns - 1) * gap).coerceAtLeast(0)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        slots.clear()
        var y = 0
        (0 until childCount).map(::getChildAt).filter { it.visibility != GONE }.chunked(columns).forEach { row ->
            if (slots.isNotEmpty()) y += gap
            var rowHeight = 0
            row.forEachIndexed { index, child ->
                val start = usable * index / columns
                val childWidth = usable * (index + 1) / columns - start
                child.measure(exact(childWidth), free)
                slots += Slot(child, start + index * gap, y)
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
            y += rowHeight
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(y, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        slots.forEach { (child, x, y) ->
            val start = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
            child.layout(start, y, start + child.measuredWidth, y + child.measuredHeight)
        }
    }
}
