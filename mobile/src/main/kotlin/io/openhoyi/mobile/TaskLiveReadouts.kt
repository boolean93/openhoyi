package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Two prominent values and supporting readings share a row when the actual text fits. */
internal class TaskLiveReadouts(context: Context) : ViewGroup(context) {
    private val gap get() = (TaskVisualTokens.RELATED_GAP_DP * resources.displayMetrics.density).toInt()
    private var inline = false
    private var firstRowHeight = 0

    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        check(childCount == 3)
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val support = getChildAt(2) as LinearLayout
        fun arrangeSupport(vertical: Boolean) {
            support.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            for (index in 0 until support.childCount) {
                val params = support.getChildAt(index).layoutParams as LinearLayout.LayoutParams
                params.width = if (vertical) -1 else 0
                params.height = -2
                params.weight = if (vertical) 0f else 1f
                params.marginEnd = if (!vertical && index == 0) gap else 0
            }
        }
        arrangeSupport(true)
        val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (index in 0 until childCount) getChildAt(index).measure(natural, natural)
        val mainMinimum = maxOf(getChildAt(0).measuredWidth, getChildAt(1).measuredWidth)
        val supportingWidth = maxOf(support.measuredWidth, (160 * resources.displayMetrics.density).toInt())
        inline = mainMinimum * 2 + supportingWidth + gap * 2 <= available
        arrangeSupport(inline)
        val primaryWidth = if (inline) ((available - supportingWidth - gap * 2) / 2).coerceAtLeast(0)
            else ((available - gap) / 2).coerceAtLeast(0)
        for (index in 0..1) getChildAt(index).measure(MeasureSpec.makeMeasureSpec(primaryWidth, MeasureSpec.EXACTLY), natural)
        support.measure(MeasureSpec.makeMeasureSpec(if (inline) supportingWidth else available, MeasureSpec.EXACTLY), natural)
        firstRowHeight = maxOf(getChildAt(0).measuredHeight, getChildAt(1).measuredHeight)
        val height = if (inline) maxOf(firstRowHeight, support.measuredHeight) else firstRowHeight + support.measuredHeight
        setMeasuredDimension(available, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        fun place(view: View, x: Int, y: Int) {
            val left = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - x - view.measuredWidth else x
            view.layout(left, y, left + view.measuredWidth, y + view.measuredHeight)
        }
        place(getChildAt(0), 0, 0)
        place(getChildAt(1), getChildAt(0).measuredWidth + gap, 0)
        val support = getChildAt(2)
        place(support, if (inline) width - support.measuredWidth else 0,
            if (inline) (measuredHeight - support.measuredHeight) / 2 else firstRowHeight)
    }
}
