package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button

/** The same, always accessible controls reflow into complete rows in narrow windows. */
internal class SettingsPreviewOptions(context: Context, private val size: Int,
    private val style: TaskProductVisualStyle) : ViewGroup(context) {
    private val gap = style.dp(8)
    private var columns = size
    private val heights = mutableListOf<Int>()
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val required = style.dp(if (size == 4) 112 else 86)
        columns = if (width >= size * required + (size - 1) * gap) size
            else if (size == 4 && width >= 2 * required + gap) 2
            else if (size == 3 && width >= 2 * required + gap) 2 else 1
        val cellWidth = ((width - (columns - 1) * gap) / columns).coerceAtLeast(0)
        heights.clear()
        (0 until childCount).chunked(columns).forEach { row ->
            val w = MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY)
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            val rowHeight = row.maxOf { i -> getChildAt(i).let { it.measure(w, free); it.measuredHeight } }
            val h = MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY)
            row.forEach { getChildAt(it).measure(w, h) }
            heights.add(rowHeight)
        }
        val total = heights.sum() + (heights.size - 1).coerceAtLeast(0) * gap
        setMeasuredDimension(width, resolveSize(total, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var y = 0
        heights.forEachIndexed { row, h ->
            for (col in 0 until columns) {
                val i = row * columns + col
                if (i >= childCount) break
                val child = getChildAt(i)
                val x = col * (child.measuredWidth + gap)
                val start = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
                child.layout(start, y, start + child.measuredWidth, y + h)
            }
            y += h + gap
        }
    }
}

internal fun settingsPreviewButton(context: Context, style: TaskProductVisualStyle, title: CharSequence,
    selected: Boolean): Button = style.button(Button(context), outline = true).apply {
    text = title
    style.apply(this, TaskProductVisualStyle.auxiliary)
    setTextColor(if (selected) style.accent else style.ink)
    isSelected = selected
    minHeight = style.dp(48)
    minimumHeight = minHeight
    gravity = Gravity.CENTER
    setPaddingRelative(style.dp(6), style.dp(6), style.dp(6), style.dp(6))
    val fill = if (selected) style.selected else style.surface
    fun shape(focused: Boolean): GradientDrawable = style.shape(fill, true, 10).apply {
        if (selected || focused) setStroke(style.dp(1), style.accent)
    }
    background = RippleDrawable(ColorStateList.valueOf(style.selected), StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), shape(true))
        addState(intArrayOf(), shape(false))
    }, style.shape(Color.WHITE, radius = 10))
}
